import java.io.File
import java.net.URI
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val voskModelName = "vosk-model-small-ru-0.22"
val voskModelUrl = "https://alphacephei.com/vosk/models/$voskModelName.zip"
val voskAssetsDir = layout.buildDirectory.dir("generated/vosk-assets")

android {
    namespace = "com.jasper.facemirror"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jasper.facemirror"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            "\"${localProperties.getProperty("gemini.api.key", "")}\"",
        )
    }

    buildTypes {
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
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    sourceSets.getByName("main").assets.srcDir(voskAssetsDir)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    implementation("com.google.mlkit:face-detection:16.1.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.tensorflow:tensorflow-lite:2.16.1")

    implementation("net.java.dev.jna:jna:5.18.1@aar")
    implementation("com.alphacephei:vosk-android:0.3.75@aar")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

val unpackVoskModel by tasks.registering {
    val zipFile = layout.buildDirectory.file("downloads/$voskModelName.zip")
    inputs.property("model", voskModelName)
    outputs.dir(voskAssetsDir)
    doLast {
        val zip = zipFile.get().asFile
        if (!zip.exists() || zip.length() < 10_000_000L) {
            zip.parentFile.mkdirs()
            logger.lifecycle("Downloading $voskModelName (~45MB)…")
            zip.outputStream().use { out ->
                URI(voskModelUrl).toURL().openStream().use { input -> input.copyTo(out) }
            }
        }
        val dest = File(voskAssetsDir.get().asFile, "model-ru")
        dest.deleteRecursively()
        dest.mkdirs()
        ZipFile(zip).use { archive ->
            val prefix = "$voskModelName/"
            archive.entries().asSequence().forEach { entry ->
                if (!entry.name.startsWith(prefix)) return@forEach
                val relative = entry.name.removePrefix(prefix)
                if (relative.isEmpty()) return@forEach
                val outFile = File(dest, relative)
                if (entry.isDirectory) {
                    outFile.mkdirs()
                    return@forEach
                }
                outFile.parentFile.mkdirs()
                archive.getInputStream(entry).use { input ->
                    outFile.outputStream().use { input.copyTo(it) }
                }
            }
        }
        File(dest, "uuid").writeText(voskModelName)
        check(File(dest, "am").isDirectory) {
            "Vosk model unpack failed: ${dest}/am missing (zip ${zip.length()} bytes)"
        }
        logger.lifecycle("Vosk model ready at $dest")
    }
}

tasks.named("preBuild").configure { dependsOn(unpackVoskModel) }
