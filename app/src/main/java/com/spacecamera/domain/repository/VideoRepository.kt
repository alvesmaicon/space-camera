package com.spacecamera.domain.repository

import java.io.File

interface VideoRepository {
    suspend fun startRecording(micEnabled: Boolean = true, targetRotation: Int = 0)
    suspend fun pauseRecording()
    suspend fun resumeRecording()
    suspend fun stopRecording()
    suspend fun getVideoFileSize(): Long
    suspend fun getFormattedFileSize(): String
}
