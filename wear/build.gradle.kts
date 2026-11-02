import java.util.Properties

plugins {
    id("com.android.application")
}

// Same signing key as the phone app: the Wear OS Data Layer only pairs apps with the same
// application id and signature.
val keystoreProperties = Properties().apply {
    val file = System.getenv("WRISTLINE_KEYSTORE_PROPERTIES")?.let { File(it) } ?: rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "app.wristline.wear"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.wristline"
        minSdk = 30
        targetSdk = 36
        versionCode = (property("wristline.versionCode") as String).toInt() + 1000
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
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    testOptions {
        // Robolectric renders the screens (WatchScreensTest) with the real resources.
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED") }
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.wear:wear:1.4.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.wear.tiles:tiles:1.6.2")
    implementation("androidx.wear.protolayout:protolayout:1.4.2")
    implementation("androidx.concurrent:concurrent-futures:1.3.0")
    // "Call <favourite>" complication for watch faces.
    implementation("androidx.wear.watchface:watchface-complications-data-source:1.3.0")
    // Call indicator on the watch face while a call started from the watch is on.
    implementation("androidx.wear:wear-ongoing:1.1.0")
    implementation("androidx.core:core:1.19.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
}
