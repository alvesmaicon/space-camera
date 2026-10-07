package com.spacecamera.presentation.screens

import com.spacecamera.R
import androidx.compose.ui.res.stringResource
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.spacecamera.BuildConfig
import com.spacecamera.presentation.layout.rememberReadingGutter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navController: NavController) {
    BackHandler { navController.popBackStack() }
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isDark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isDark -> dynamicLightColorScheme(context)
        isDark -> darkColorScheme()
        else -> lightColorScheme()
    }

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
                    title = { Text(stringResource(R.string.about_title)) },
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
                    // FR-8: limita a largura de leitura em janela larga; zero em retrato.
                    .padding(horizontal = readingGutter),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                item { AboutSectionHeader(stringResource(R.string.about_section_app)) }
                item {
                    AboutRow(stringResource(R.string.about_app), stringResource(R.string.app_name))
                    // Vem do BuildConfig (gradle/libs.versions.toml é a fonte única).
                    AboutRow(stringResource(R.string.about_version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    // Identifica exatamente qual commit gerou o APK instalado —
                    // essencial para reproduzir um problema relatado de um aparelho.
                    AboutRow(stringResource(R.string.about_build), BuildConfig.GIT_SHA)
                    AboutRow(stringResource(R.string.about_developer), "Maicon Alves")
                }

                item { Spacer(Modifier.height(8.dp)) }
                item { AboutSectionHeader(stringResource(R.string.about_section_technical)) }
                item {
                    AboutRow(stringResource(R.string.about_video_codec), "H.264 (AVC)")
                    AboutRow(stringResource(R.string.about_audio_codec), "AAC")
                    AboutRow(stringResource(R.string.about_output_format), "MP4")
                    AboutRow(stringResource(R.string.about_min_api), "Android 7.0 (API 24)")
                }

                item { Spacer(Modifier.height(20.dp)) }
                item {
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AboutSectionHeader(text: String) {
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
private fun AboutRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}
