package com.spacecamera.data.repository

import com.spacecamera.camera.CameraManager
import com.spacecamera.domain.repository.VideoRepository

class VideoRepositoryImpl(
    private val cameraManager: CameraManager
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
