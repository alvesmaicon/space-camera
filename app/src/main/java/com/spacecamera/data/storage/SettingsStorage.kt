package com.spacecamera.data.storage

import android.content.Context
import android.content.SharedPreferences
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.VideoBitratePreset

class SettingsStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("space_camera_settings", Context.MODE_PRIVATE)

    var isStabilizationEnabled: Boolean
        get() = prefs.getBoolean(KEY_EIS, true)
        set(value) = prefs.edit().putBoolean(KEY_EIS, value).apply()

    var isNoiseReductionEnabled: Boolean
        get() = prefs.getBoolean(KEY_NR, true)
        set(value) = prefs.edit().putBoolean(KEY_NR, value).apply()

    var isGridEnabled: Boolean
        get() = prefs.getBoolean(KEY_GRID, false)
        set(value) = prefs.edit().putBoolean(KEY_GRID, value).apply()

    var isLevelEnabled: Boolean
        get() = prefs.getBoolean(KEY_LEVEL, false)
        set(value) = prefs.edit().putBoolean(KEY_LEVEL, value).apply()

    var isMicMuted: Boolean
        get() = prefs.getBoolean(KEY_MIC_MUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_MIC_MUTED, value).apply()

    var bitratePreset: VideoBitratePreset
        get() = VideoBitratePreset.entries.getOrNull(
            prefs.getInt(KEY_BITRATE_PRESET, VideoBitratePreset.MEDIUM.ordinal)
        ) ?: VideoBitratePreset.MEDIUM
        set(value) = prefs.edit().putInt(KEY_BITRATE_PRESET, value.ordinal).apply()

    var isTapToFocusEnabled: Boolean
        get() = prefs.getBoolean(KEY_TAP_TO_FOCUS, true)
        set(value) = prefs.edit().putBoolean(KEY_TAP_TO_FOCUS, value).apply()

    var isSaveLocationEnabled: Boolean
        get() = prefs.getBoolean(KEY_SAVE_LOCATION, false)
        set(value) = prefs.edit().putBoolean(KEY_SAVE_LOCATION, value).apply()

    var isFrontCameraMirrorEnabled: Boolean
        get() = prefs.getBoolean(KEY_FRONT_MIRROR, false)
        set(value) = prefs.edit().putBoolean(KEY_FRONT_MIRROR, value).apply()

    var isKeepSettingsEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SETTINGS, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SETTINGS, value).apply()

    var isHdrEnabled: Boolean
        get() = prefs.getBoolean(KEY_HDR, false)
        set(value) = prefs.edit().putBoolean(KEY_HDR, value).apply()

    var photoQualityPreset: PhotoQualityPreset
        get() = PhotoQualityPreset.entries.getOrNull(
            prefs.getInt(KEY_PHOTO_QUALITY, PhotoQualityPreset.MAXIMA.ordinal)
        ) ?: PhotoQualityPreset.MAXIMA
        set(value) = prefs.edit().putInt(KEY_PHOTO_QUALITY, value.ordinal).apply()

    var isImageEnhancementEnabled: Boolean
        get() = prefs.getBoolean(KEY_IMAGE_ENHANCEMENT, false)
        set(value) = prefs.edit().putBoolean(KEY_IMAGE_ENHANCEMENT, value).apply()

    companion object {
        private const val KEY_EIS = "eis_enabled"
        private const val KEY_NR = "nr_enabled"
        private const val KEY_GRID = "grid_enabled"
        private const val KEY_LEVEL = "level_enabled"
        private const val KEY_MIC_MUTED = "mic_muted"
        private const val KEY_BITRATE_PRESET = "video_bitrate_preset"
        private const val KEY_TAP_TO_FOCUS = "tap_to_focus_enabled"
        private const val KEY_SAVE_LOCATION = "save_location_enabled"
        private const val KEY_FRONT_MIRROR = "front_camera_mirror"
        private const val KEY_KEEP_SETTINGS = "keep_settings"
        private const val KEY_HDR = "hdr_enabled"
        private const val KEY_PHOTO_QUALITY = "photo_quality_preset"
        private const val KEY_IMAGE_ENHANCEMENT = "image_enhancement_enabled"
    }
}
