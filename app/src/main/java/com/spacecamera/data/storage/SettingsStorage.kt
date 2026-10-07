package com.spacecamera.data.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.VideoBitratePreset
import com.spacecamera.camera.mode.ModeArrangement
import timber.log.Timber

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

    /**
     * Requirements: FR-8, NFR-8 · Decisions: ADR-004
     *
     * Ordem e plano dos modos, por **identificador estável** — nunca `ordinal`. Guarda
     * tudo o que o usuário gravou, inclusive modos que esta versão não conhece ou que
     * este aparelho não oferece: quem filtra é o registro, na hora de mostrar (AC-5.3).
     * Valor de outro tipo (gravado por outra versão) cai no padrão, sem exceção.
     */
    var modeArrangement: ModeArrangement
        get() = try {
            val ordem = prefs.getString(KEY_MODE_ORDER, null)
            if (ordem == null) ModeArrangement.DEFAULT
            else ModeArrangement(order = ordem.toIds(), pinned = prefs.getString(KEY_MODE_PINNED, "").orEmpty().toIds().toSet())
        } catch (e: ClassCastException) {
            Timber.w(e, "preferência de modos ilegível, usando o padrão")
            ModeArrangement.DEFAULT
        }
        set(value) = prefs.edit {
            putString(KEY_MODE_ORDER, value.order.joinToString(","))
            putString(KEY_MODE_PINNED, value.pinned.joinToString(","))
        }

    private fun String.toIds() = split(',').filter { it.isNotBlank() }

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
        private const val KEY_MODE_ORDER = "mode_order"
        private const val KEY_MODE_PINNED = "mode_pinned"
    }
}
