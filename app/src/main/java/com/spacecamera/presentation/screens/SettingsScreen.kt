package com.spacecamera.presentation.screens

import com.spacecamera.R
import androidx.compose.ui.res.stringResource
import com.spacecamera.presentation.components.ModeSettingsSection
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.spacecamera.camera.PhotoQualityPreset
import com.spacecamera.camera.VideoBitratePreset
import com.spacecamera.presentation.layout.rememberReadingGutter
import com.spacecamera.presentation.viewmodels.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: CameraViewModel
) {
    BackHandler { navController.popBackStack() }
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isDark -> dynamicLightColorScheme(context)
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }

    val isStabilizationEnabled by viewModel.isStabilizationEnabled.collectAsState()
    val isEisSupported by viewModel.isEisSupported.collectAsState()
    val isNoiseReductionEnabled by viewModel.isNoiseReductionEnabled.collectAsState()
    val isHdrEnabled by viewModel.isHdrEnabled.collectAsState()
    val isHdrSupported by viewModel.isHdrSupported.collectAsState()
    val isGridEnabled by viewModel.isGridEnabled.collectAsState()
    val isLevelEnabled by viewModel.isLevelEnabled.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val bitratePreset by viewModel.bitratePreset.collectAsState()
    val isTapToFocusEnabled by viewModel.isTapToFocusEnabled.collectAsState()
    val isSaveLocationEnabled by viewModel.isSaveLocationEnabled.collectAsState()
    val isFrontCameraMirrorEnabled by viewModel.isFrontCameraMirrorEnabled.collectAsState()
    val isKeepSettingsEnabled by viewModel.isKeepSettingsEnabled.collectAsState()

    val bitrateOptions = VideoBitratePreset.entries
    var showBitrateDialog by remember { mutableStateOf(false) }
    val photoQualityPreset by viewModel.photoQualityPreset.collectAsState()
    val photoQualityOptions = PhotoQualityPreset.entries
    var showPhotoQualityDialog by remember { mutableStateOf(false) }
    val isImageEnhancementEnabled by viewModel.isImageEnhancementEnabled.collectAsState()
    val arrangedModes by viewModel.arrangedModes.collectAsState()

    MaterialTheme(colorScheme = colorScheme) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

        // A mesma margem da lista vai na barra de título, senão o título fica colado
        // na borda esquerda enquanto o conteúdo está centralizado (FR-8). Padding no
        // app bar é seguro aqui porque o containerColor é o próprio fundo da página.
        val readingGutter = rememberReadingGutter()

        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    modifier = Modifier.padding(horizontal = readingGutter),
                    title = { Text(stringResource(R.string.settings_title)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // FR-8: em tablet cada linha se esticava por 1.280dp. Zero em
                    // retrato, então o telefone continua idêntico.
                    .padding(horizontal = readingGutter),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // ── Modos da câmera (FR-7) — no topo, onde o "Editar" da gaveta cai ──
                item { SectionHeader(stringResource(R.string.settings_section_modes)) }
                item {
                    ModeSettingsSection(
                        modes = arrangedModes.ordered,
                        pinned = arrangedModes.pinned.toSet(),
                        isRemovable = viewModel::isModeRemovable,
                        onMove = viewModel::moveMode,
                        onPinnedChange = viewModel::setModePinned,
                        onRestoreDefault = viewModel::restoreDefaultModes
                    )
                }
                // ── Câmera ────────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_section_camera)) }
                item {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_tap_focus),
                        subtitle = stringResource(R.string.settings_tap_focus_summary),
                        checked = isTapToFocusEnabled,
                        onToggle = { viewModel.toggleTapToFocus() }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_mirror),
                        subtitle = stringResource(R.string.settings_mirror_summary),
                        checked = isFrontCameraMirrorEnabled,
                        onToggle = { viewModel.toggleFrontCameraMirror() }
                    )
                }
                // ── Foto ────────────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_section_photo)) }
                item {
                    SettingsNavigationRow(
                        title = stringResource(R.string.settings_photo_quality),
                        subtitle = stringResource(
                            R.string.settings_photo_quality_summary,
                            stringResource(photoQualityPreset.labelRes),
                            photoQualityPreset.displayLabel
                        ),
                        onClick = { showPhotoQualityDialog = true }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_enhancement),
                        subtitle = stringResource(R.string.settings_enhancement_summary),
                        checked = isImageEnhancementEnabled,
                        onToggle = { viewModel.toggleImageEnhancement() }
                    )
                }
                // ── Vídeo ────────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_section_video)) }
                item {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_eis),
                        subtitle = if (isEisSupported) stringResource(R.string.settings_eis_summary)
                                    else stringResource(R.string.settings_not_supported),
                        checked = isStabilizationEnabled,
                        onToggle = { viewModel.toggleStabilization() },
                        enabled = isEisSupported
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_hdr),
                        subtitle = if (isHdrSupported) stringResource(R.string.settings_hdr_summary)
                                    else stringResource(R.string.settings_not_supported),
                        checked = isHdrEnabled,
                        onToggle = { viewModel.toggleHdr() },
                        enabled = isHdrSupported
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_noise_reduction),
                        subtitle = stringResource(R.string.settings_noise_reduction_summary),
                        checked = isNoiseReductionEnabled,
                        onToggle = { viewModel.toggleNoiseReduction() }
                    )
                    SettingsNavigationRow(
                        title = stringResource(R.string.settings_video_quality),
                        subtitle = stringResource(bitratePreset.labelRes),
                        onClick = { showBitrateDialog = true }
                    )
                }

                // ── Interface ────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_section_interface)) }
                item {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_grid),
                        subtitle = stringResource(R.string.settings_grid_summary),
                        checked = isGridEnabled,
                        onToggle = { viewModel.toggleGrid() }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_level),
                        subtitle = stringResource(R.string.settings_level_summary),
                        checked = isLevelEnabled,
                        onToggle = { viewModel.toggleLevel() }
                    )
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_mic_muted),
                        subtitle = stringResource(R.string.settings_mic_muted_summary),
                        checked = isMicMuted,
                        onToggle = { viewModel.toggleMic() }
                    )
                }

                // ── Privacidade ──────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_section_privacy)) }
                item {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_location),
                        subtitle = stringResource(R.string.settings_location_summary),
                        checked = isSaveLocationEnabled,
                        onToggle = { viewModel.toggleSaveLocation() }
                    )
                }

                // ── App ──────────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader(stringResource(R.string.settings_section_app)) }
                item {
                    SettingsToggleRow(
                        title = stringResource(R.string.settings_keep),
                        subtitle = stringResource(R.string.settings_keep_summary),
                        checked = isKeepSettingsEnabled,
                        onToggle = { viewModel.toggleKeepSettings() }
                    )
                    SettingsNavigationRow(
                        title = stringResource(R.string.settings_about),
                        subtitle = stringResource(R.string.settings_about_summary),
                        onClick = { navController.navigate("about") }
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        // ── Modal bitrate ─────────────────────────────────────────────────
        if (showBitrateDialog) {
            AlertDialog(
                onDismissRequest = { showBitrateDialog = false },
                title = { Text(stringResource(R.string.settings_video_quality)) },
                text = {
                    Column {
                        bitrateOptions.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = preset == bitratePreset,
                                        role = Role.RadioButton,
                                        onClick = {
                                            viewModel.setBitratePreset(preset)
                                            showBitrateDialog = false
                                        }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = preset == bitratePreset, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Text(stringResource(preset.labelRes), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }

        // ── Modal qualidade de foto ───────────────────────────────────────
        if (showPhotoQualityDialog) {
            AlertDialog(
                onDismissRequest = { showPhotoQualityDialog = false },
                title = { Text(stringResource(R.string.settings_photo_quality)) },
                text = {
                    Column {
                        photoQualityOptions.forEach { preset ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = preset == photoQualityPreset,
                                        role = Role.RadioButton,
                                        onClick = {
                                            viewModel.setPhotoQualityPreset(preset)
                                            showPhotoQualityDialog = false
                                        }
                                    )
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = preset == photoQualityPreset, onClick = null)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(stringResource(preset.labelRes), style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        preset.displayLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {}
            )
        }
    }
}

// ── Componentes internos ────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Divider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true
) {
    val indication = LocalIndication.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .toggleable(
                value = checked,
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = indication,
                onValueChange = { onToggle() }
            )
            .alpha(if (enabled) 1f else 0.55f)
    )
}

@Composable
private fun SettingsNavigationRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick)
    )
}
