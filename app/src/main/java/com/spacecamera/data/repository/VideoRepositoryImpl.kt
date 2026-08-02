package com.spacecamera.data.repository

import com.spacecamera.camera.CameraController
import com.spacecamera.domain.repository.VideoRepository

class VideoRepositoryImpl(
    private val cameraManager: CameraController
) : VideoRepository {

    override suspend fun startRecording(micEnabled: Boolean, targetRotation: Int) {
        cameraManager.startRecording(micEnabled, targetRotation)
    }

    override suspend fun pauseRecording() {
        cameraManager.pauseRecording()
    }

    override suspend fun resumeRecording() {
        cameraManager.resumeRecording()
    }

    override suspend fun stopRecording() {
        cameraManager.stopRecording()
    }

    override suspend fun getVideoFileSize(): Long = 0L
    override suspend fun getFormattedFileSize(): String = ""
}
