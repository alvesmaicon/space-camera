package com.spacecamera.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.spacecamera.camera.mode.ModeLabel

/** O texto do nome de um modo, no idioma do aparelho. */
@Composable
internal fun ModeLabel.text(): String = when (this) {
    is ModeLabel.Resource -> stringResource(id)
    is ModeLabel.Literal -> text
}
