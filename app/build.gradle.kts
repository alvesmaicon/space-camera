plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

/**
 * Hash curto do commit atual, embutido no BuildConfig.
 *
 * Serve para responder "qual build exatamente está neste aparelho?" — a tela
 * Sobre mostra o valor. Retorna "unknown" fora de um repositório git (ex.: build
 * a partir de um zip do código) para não quebrar o build.
 */
fun gitShortSha(): String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "unknown" }
} catch (_: Exception) {
    "unknown"
}

android {
    namespace = "com.spacecamera"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.spacecamera"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = libs.versions.appVersionCode.get().toInt()
        versionName = libs.versions.appVersionName.get()

        buildConfigField("String", "GIT_SHA", "\"${gitShortSha()}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // Sem applicationIdSuffix de propósito: `make run` e os scripts npm
            // iniciam a activity por com.spacecamera/.MainActivity.
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // Obrigatório a partir do AGP 8.0: sem isto a classe BuildConfig nem é
        // gerada, e era por isso que a tela Sobre precisava hardcodar a versão.
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Necessário para o Robolectric enxergar recursos e o AndroidManifest.
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    lint {
        // O relatório em XML é o que as ferramentas consomem; o HTML é para humanos.
        xmlReport = true
        htmlReport = true
        // Avisos não derrubam o build hoje (há dívida acumulada), mas erros sim.
        warningsAsErrors = false
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Compose
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.androidx.navigation.compose)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video)
    implementation(libs.camerax.view)

    // Coroutines
    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    // Permissões
    implementation(libs.accompanist.permissions)

    // Logging
    implementation(libs.timber)

    // Testes unitários (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // Testes instrumentados (device/emulador)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    // Ferramentas de debug
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
