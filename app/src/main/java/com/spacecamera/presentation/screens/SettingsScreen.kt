package com.spacecamera.presentation.screens

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
                    title = { Text("Configurações da câmera") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Voltar")
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
                // ── Câmera ────────────────────────────────────────────────
                item { SectionHeader("Câmera") }
                item {
                    SettingsToggleRow(
                        title = "Toque para focar / brilho",
                        subtitle = "Toque na tela para ajustar foco e exposição",
                        checked = isTapToFocusEnabled,
                        onToggle = { viewModel.toggleTapToFocus() }
                    )
                    SettingsToggleRow(
                        title = "Espelhar câmera frontal",
                        subtitle = "Espelha o vídeo gravado pela câmera frontal",
                        checked = isFrontCameraMirrorEnabled,
                        onToggle = { viewModel.toggleFrontCameraMirror() }
                    )
                }
                // ── Foto ────────────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("Foto") }
                item {
                    SettingsNavigationRow(
                        title = "Qualidade da foto",
                        subtitle = "${photoQualityPreset.label} (${photoQualityPreset.displayLabel})",
                        onClick = { showPhotoQualityDialog = true }
                    )
                    SettingsToggleRow(
                        title = "Melhoria de imagem",
                        subtitle = "Aumenta saturação e contraste de forma adaptativa após a captura",
                        checked = isImageEnhancementEnabled,
                        onToggle = { viewModel.toggleImageEnhancement() }
                    )
                }
                // ── Vídeo ────────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("Vídeo") }
                item {
                    SettingsToggleRow(
                        title = "Estabilização EIS",
                        subtitle = if (isEisSupported) "Estabilização eletrônica de imagem"
                                    else "Não suportado neste dispositivo",
                        checked = isStabilizationEnabled,
                        onToggle = { viewModel.toggleStabilization() },
                        enabled = isEisSupported
                    )
                    SettingsToggleRow(
                        title = "HDR",
                        subtitle = if (isHdrSupported) "Alto alcance dinâmico para vídeo"
                                    else "Não suportado neste dispositivo",
                        checked = isHdrEnabled,
                        onToggle = { viewModel.toggleHdr() },
                        enabled = isHdrSupported
                    )
                    SettingsToggleRow(
                        title = "Redução de Ruído",
                        subtitle = "Reduz grãos visuais no vídeo",
                        checked = isNoiseReductionEnabled,
                        onToggle = { viewModel.toggleNoiseReduction() }
                    )
                    SettingsNavigationRow(
                        title = "Qualidade do vídeo",
                        subtitle = bitratePreset.label,
                        onClick = { showBitrateDialog = true }
                    )
                }

                // ── Interface ────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("Interface") }
                item {
                    SettingsToggleRow(
                        title = "Grade de composição",
                        subtitle = "Exibe linhas de referência na pré-visualização",
                        checked = isGridEnabled,
                        onToggle = { viewModel.toggleGrid() }
                    )
                    SettingsToggleRow(
                        title = "Nível horizontal",
                        subtitle = "Indicador de nivelamento na pré-visualização",
                        checked = isLevelEnabled,
                        onToggle = { viewModel.toggleLevel() }
                    )
                    SettingsToggleRow(
                        title = "Microfone silenciado",
                        subtitle = "Inicia gravações sem áudio",
                        checked = isMicMuted,
                        onToggle = { viewModel.toggleMic() }
                    )
                }

                // ── Privacidade ──────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("Privacidade") }
                item {
                    SettingsToggleRow(
                        title = "Salvar localização",
                        subtitle = "Lembrar localização para fotos e vídeos",
                        checked = isSaveLocationEnabled,
                        onToggle = { viewModel.toggleSaveLocation() }
                    )
                }

                // ── App ──────────────────────────────────────────────────
                item { Spacer(Modifier.height(8.dp)) }
                item { SectionHeader("App") }
                item {
                    SettingsToggleRow(
                        title = "Manter configurações",
                        subtitle = "Lembra as configurações entre sessões; quando desativado, usa os padrões ao abrir",
                        checked = isKeepSettingsEnabled,
                        onToggle = { viewModel.toggleKeepSettings() }
                    )
                    SettingsNavigationRow(
                        title = "Sobre",
                        subtitle = "Versão e informações do app",
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
                title = { Text("Qualidade do vídeo") },
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
                                Text(preset.label, style = MaterialTheme.typography.bodyLarge)
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
                title = { Text("Qualidade da foto") },
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
                                    Text(preset.label, style = MaterialTheme.typography.bodyLarge)
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
