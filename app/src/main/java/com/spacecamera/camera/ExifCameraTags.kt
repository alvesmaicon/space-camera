// Só constantes com o nome das tags — nenhuma leitura nem escrita de EXIF aqui. O uso do
// `ExifInterface` do framework mora no `CameraManager` e já está no baseline do lint.
// A anotação é de arquivo porque o lint acusa a linha do import.
@file:SuppressLint("ExifInterface")

package com.spacecamera.camera

import android.annotation.SuppressLint
import android.media.ExifInterface

/**
 * Tags EXIF que a câmera escreve no JPEG original e que precisam sobreviver quando a
 * foto é regravada a partir do bitmap (recorte do "Full", melhoria de imagem).
 *
 * Inclui ISO e tempo de exposição: é por isso que a foto do modo Pro sai com os
 * valores manuais no EXIF sem código próprio (AC-12.1).
 *
 * Saiu do `CameraManager` sem mudança, para abrir espaço do NFR-3 na Tarefa 11.
 */
internal val EXIF_CAMERA_TAGS = listOf(
    ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
    ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_APERTURE_VALUE,
    ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_ISO_SPEED_RATINGS,
    ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
    ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH,
    ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_EXPOSURE_PROGRAM,
    ExifInterface.TAG_METERING_MODE, ExifInterface.TAG_SCENE_CAPTURE_TYPE,
    ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, ExifInterface.TAG_BRIGHTNESS_VALUE,
    ExifInterface.TAG_SUBJECT_DISTANCE, ExifInterface.TAG_LIGHT_SOURCE,
)
