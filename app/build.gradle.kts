import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
val releaseProperties = Properties()
val signingFile = providers.environmentVariable("SPORTSWAVE_SIGNING_PROPERTIES").orNull?.let { file(it) }
if (signingFile?.isFile == true) signingFile.inputStream().use { releaseProperties.load(it) }
val hasReleaseSigning = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !releaseProperties.getProperty(it).isNullOrBlank() }
if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    check(hasReleaseSigning) { "Set SPORTSWAVE_SIGNING_PROPERTIES to your private signing properties file. See docs/RELEASING.md." }
}
android {
    namespace = "com.example.sportsstream"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.example.sportsstream"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "1.0.1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseProperties.getProperty("storeFile"))
                storePassword = releaseProperties.getProperty("storePassword")
                keyAlias = releaseProperties.getProperty("keyAlias")
                keyPassword = releaseProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
dependencies {
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("org.jsoup:jsoup:1.18.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    val media3 = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.17.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}
