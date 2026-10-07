package com.spacecamera.camera

import com.spacecamera.camera.mode.CameraModeDefinition
import com.spacecamera.camera.mode.PhotoMode
import com.spacecamera.camera.mode.VideoMode

enum class CameraMode(val label: String, val definition: CameraModeDefinition) {
    VIDEO("Vídeo", VideoMode),
    PHOTO("Foto", PhotoMode);

    fun next(): CameraMode = entries[(ordinal + 1) % entries.size]
    fun previous(): CameraMode = entries[(ordinal - 1 + entries.size) % entries.size]
}
