package com.spacecamera.camera

enum class CameraMode(val label: String) {
    VIDEO("Vídeo"),
    PHOTO("Foto");

    fun next(): CameraMode = entries[(ordinal + 1) % entries.size]
    fun previous(): CameraMode = entries[(ordinal - 1 + entries.size) % entries.size]
}
