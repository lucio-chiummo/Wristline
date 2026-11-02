import java.util.Properties

plugins {
    id("com.android.application")
}

// Signing secrets never go in the repo. They live in keystore.properties (gitignored), or in the
// file WRISTLINE_KEYSTORE_PROPERTIES points to:
//   storeFile=/absolute/path/to/your.keystore
//   storePassword=...
//   keyAlias=...
//   keyPassword=...
val keystoreProperties = Properties().apply {
    val file = System.getenv("WRISTLINE_KEYSTORE_PROPERTIES")?.let { File(it) } ?: rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.wristline"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.wristline"
        minSdk = 31
        targetSdk = 36
        versionCode = (property("wristline.versionCode") as String).toInt()
        versionName = property("wristline.versionName") as String
    }

    signingConfigs {
        create("release") {
            if (keystoreProperties.isNotEmpty()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProperties.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // A trial of R8, which release doesn't use yet. Installs next to the normal app
        // (app.wristline.shrunk, debug key).
        create("shrunk") {
            initWith(getByName("release"))
            applicationIdSuffix = ".shrunk"
            versionNameSuffix = "-shrunk"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        // Robolectric tests need the merged resources (strings, drawables) on the classpath.
        unitTests.isIncludeAndroidResources = true
        // Robolectric's SDK 36 sandbox reaches into FileDescriptor internals.
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

dependencies {
    // Wear OS Data Layer: contacts to the watch app, call requests back.
    implementation("com.google.android.gms:play-services-wearable:20.0.1")

    testImplementation("junit:junit:4.13.2")
    // Android glue without a device: RelayListener's call flow, OutgoingCalls.
    testImplementation("org.robolectric:robolectric:4.17")
}
