package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.player.VideoResizeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _defaultAspectRatio = MutableStateFlow(loadDefaultAspectRatio())
    val defaultAspectRatio: StateFlow<VideoResizeMode> = _defaultAspectRatio.asStateFlow()

    private val _alwaysLandscape = MutableStateFlow(loadAlwaysLandscape())
    val alwaysLandscape: StateFlow<Boolean> = _alwaysLandscape.asStateFlow()

    private fun loadDefaultAspectRatio(): VideoResizeMode {
        val savedName = prefs.getString(KEY_DEFAULT_ASPECT_RATIO, VideoResizeMode.FIT.name)
        return try {
            VideoResizeMode.valueOf(savedName ?: VideoResizeMode.FIT.name)
        } catch (e: Exception) {
            VideoResizeMode.FIT
        }
    }

    private fun loadAlwaysLandscape(): Boolean {
        return prefs.getBoolean(KEY_ALWAYS_LANDSCAPE, false)
    }

    fun setDefaultAspectRatio(mode: VideoResizeMode) {
        prefs.edit().putString(KEY_DEFAULT_ASPECT_RATIO, mode.name).apply()
        _defaultAspectRatio.value = mode
    }

    fun setAlwaysLandscape(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALWAYS_LANDSCAPE, enabled).apply()
        _alwaysLandscape.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "cine_arena_app_settings"
        private const val KEY_DEFAULT_ASPECT_RATIO = "key_default_aspect_ratio"
        private const val KEY_ALWAYS_LANDSCAPE = "key_always_landscape"

        @Volatile
        private var INSTANCE: AppSettingsManager? = null

        fun getInstance(context: Context): AppSettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSettingsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
