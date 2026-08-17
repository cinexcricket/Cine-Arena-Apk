package com.example.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.StartAppSDK
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener

/**
 * Event callbacks for ad pre-loading lifecycle
 */
interface AdPreloadCallback {
    fun onAdLoaded()
    fun onAdFailedToLoad(errorMessage: String?)
}

/**
 * Event callbacks for ad presentation lifecycle
 */
interface AdShowCallback {
    fun onAdDisplayed()
    fun onAdClicked()
    fun onAdClosed()
    fun onAdNotDisplayed(reason: String?)
}

object StartAppHelper {
    const val APP_ID = "207109422"
    private const val TAG = "StartAppHelper"

    private var interstitialAd: StartAppAd? = null
    private var isInitialized = false
    private var isAdLoaded = false
    private var isLoading = false

    fun initialize(context: Context) {
        if (isInitialized) return
        try {
            // Initialize StartApp with the user's provided App ID: 207109422
            StartAppSDK.init(context, APP_ID, false)
            StartAppSDK.enableReturnAds(false)
            isInitialized = true
            Log.d(TAG, "StartApp SDK initialized successfully with App ID: $APP_ID")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize StartApp SDK", e)
        }
    }

    /**
     * Preload Interstitial Ad with Event Callbacks
     */
    fun preloadAd(
        context: Context,
        adMode: StartAppAd.AdMode = StartAppAd.AdMode.AUTOMATIC,
        callback: AdPreloadCallback? = null
    ) {
        try {
            initialize(context)

            // If already loaded and ready, notify callback immediately
            val current = interstitialAd
            if (isAdLoaded && current != null && current.isReady) {
                Log.d(TAG, "StartApp Interstitial already preloaded and ready")
                callback?.onAdLoaded()
                return
            }

            if (isLoading) {
                Log.d(TAG, "StartApp Interstitial is already in progress of preloading")
                return
            }

            val newAd = StartAppAd(context)
            interstitialAd = newAd
            isAdLoaded = false
            isLoading = true

            newAd.loadAd(adMode, object : AdEventListener {
                override fun onReceiveAd(ad: Ad) {
                    isAdLoaded = true
                    isLoading = false
                    Log.d(TAG, "StartApp Interstitial preloaded successfully with event callbacks")
                    callback?.onAdLoaded()
                }

                override fun onFailedToReceiveAd(ad: Ad?) {
                    isAdLoaded = false
                    isLoading = false
                    val error = ad?.errorMessage ?: "Ad failed to load from server"
                    Log.d(TAG, "StartApp Interstitial failed to preload: $error")
                    callback?.onAdFailedToLoad(error)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error preloading StartApp interstitial", e)
            isAdLoaded = false
            isLoading = false
            callback?.onAdFailedToLoad(e.message)
        }
    }

    /**
     * Check if preloaded ad is ready
     */
    fun isReady(): Boolean = isAdLoaded && interstitialAd?.isReady == true

    /**
     * Preload exit ad (convenience wrapper)
     */
    fun preloadExitAd(context: Context, callback: AdPreloadCallback? = null) {
        preloadAd(context, StartAppAd.AdMode.AUTOMATIC, callback)
    }

    /**
     * Show Preloaded Ad with full event callbacks, falling back to on-demand load-and-show
     */
    fun showAd(
        activity: Activity,
        callback: AdShowCallback? = null
    ) {
        try {
            initialize(activity)

            val displayListener = object : AdDisplayListener {
                override fun adDisplayed(ad: Ad?) {
                    Log.d(TAG, "StartApp Ad Displayed Callback")
                    callback?.onAdDisplayed()
                }

                override fun adClicked(ad: Ad?) {
                    Log.d(TAG, "StartApp Ad Clicked Callback")
                    callback?.onAdClicked()
                }

                override fun adHidden(ad: Ad?) {
                    Log.d(TAG, "StartApp Ad Hidden/Closed Callback")
                    isAdLoaded = false
                    interstitialAd = null
                    callback?.onAdClosed()
                    // Auto preload next ad in background
                    preloadAd(activity)
                }

                override fun adNotDisplayed(ad: Ad?) {
                    val reason = ad?.errorMessage ?: "Ad display failed"
                    Log.d(TAG, "StartApp Ad Not Displayed Callback: $reason")
                    isAdLoaded = false
                    interstitialAd = null
                    callback?.onAdNotDisplayed(reason)
                    // Auto preload next ad in background
                    preloadAd(activity)
                }
            }

            val readyAd = interstitialAd
            if (readyAd != null && isAdLoaded && readyAd.isReady) {
                Log.d(TAG, "Showing preloaded StartApp interstitial")
                val displayed = readyAd.showAd(displayListener)
                if (!displayed) {
                    Log.d(TAG, "Preloaded ad show returned false, loading immediately")
                    loadAndShowOnDemand(activity, displayListener, callback)
                }
            } else {
                Log.d(TAG, "Ad not preloaded or not ready yet, executing load-and-show on demand")
                loadAndShowOnDemand(activity, displayListener, callback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error showing StartApp ad", e)
            callback?.onAdNotDisplayed(e.message)
        }
    }

    private fun loadAndShowOnDemand(
        activity: Activity,
        displayListener: AdDisplayListener,
        callback: AdShowCallback?
    ) {
        try {
            val ad = StartAppAd(activity)
            ad.loadAd(StartAppAd.AdMode.AUTOMATIC, object : AdEventListener {
                override fun onReceiveAd(loadedAd: Ad) {
                    val shown = ad.showAd(displayListener)
                    if (!shown) {
                        Log.d(TAG, "Ad received but showAd returned false")
                        callback?.onAdNotDisplayed("Ad loaded but could not be presented")
                    }
                }

                override fun onFailedToReceiveAd(failedAd: Ad?) {
                    val error = failedAd?.errorMessage ?: "Unable to load ad"
                    Log.d(TAG, "On-demand ad load failed: $error")
                    callback?.onAdNotDisplayed(error)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error in loadAndShowOnDemand", e)
            callback?.onAdNotDisplayed(e.message)
        }
    }

    /**
     * Show exit interstitial with callback on finish
     */
    fun showExitInterstitial(activity: Activity, onFinished: () -> Unit) {
        showAd(activity, object : AdShowCallback {
            override fun onAdDisplayed() {}
            override fun onAdClicked() {}
            override fun onAdClosed() { onFinished() }
            override fun onAdNotDisplayed(reason: String?) { onFinished() }
        })
    }
}

