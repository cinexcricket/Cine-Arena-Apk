package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.*
import com.startapp.sdk.ads.banner.Banner
import com.startapp.sdk.ads.banner.BannerListener

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Horizontal Banner Ad
 * Integrates Start.io (StartApp) Banner View with responsive fallback container.
 */
@Composable
fun StartAppHorizontalBannerAd(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isAdLoaded by remember { mutableStateOf(false) }
    var hasLoadFailed by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CineSurface),
        border = BorderStroke(1.dp, CineOutline),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header label
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Campaign,
                        contentDescription = null,
                        tint = CineTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SPONSORED BANNER",
                        color = CineTextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )
                }

                Surface(
                    shape = RoundedCornerShape(3.dp),
                    color = CineSurfaceVariant,
                    border = BorderStroke(0.5.dp, CineOutline)
                ) {
                    Text(
                        text = "Start.io",
                        color = CineTextSecondary,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // StartApp Banner View Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        try {
                            val banner = Banner(ctx, object : BannerListener {
                                override fun onReceiveAd(view: View?) {
                                    Log.d("StartAppBanner", "Banner ad loaded successfully")
                                    isAdLoaded = true
                                    hasLoadFailed = false
                                }

                                override fun onFailedToReceiveAd(view: View?) {
                                    Log.d("StartAppBanner", "Banner ad failed to load")
                                    hasLoadFailed = true
                                }

                                override fun onClick(view: View?) {
                                    Log.d("StartAppBanner", "Banner ad clicked")
                                }

                                override fun onImpression(view: View?) {
                                    Log.d("StartAppBanner", "Banner ad impression logged")
                                }
                            })
                            banner.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            banner.loadAd()
                            banner
                        } catch (e: Exception) {
                            Log.e("StartAppBanner", "Error instantiating StartApp Banner", e)
                            hasLoadFailed = true
                            FrameLayout(ctx)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                )

                // Fallback banner placeholder if network is offline or simulator container without live ads
                if (hasLoadFailed && !isAdLoaded) {
                    Surface(
                        onClick = {
                            val activity = context.findActivity()
                            if (activity != null) {
                                StartAppHelper.showExitInterstitial(activity) {}
                            }
                        },
                        shape = RoundedCornerShape(6.dp),
                        color = CineSurfaceVariant,
                        border = BorderStroke(1.dp, CineOutline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = CinePrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Cine Arena Premium Streams",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = CineTextPrimary
                                    )
                                    Text(
                                        text = "Support live broadcasting • Tap to check special sponsor offers",
                                        fontSize = 10.sp,
                                        color = CineTextSecondary
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = CinePrimary,
                                modifier = Modifier.padding(start = 4.dp)
                            ) {
                                Text(
                                    text = "OPEN",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
