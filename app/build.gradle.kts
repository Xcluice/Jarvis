plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val vc = (project.findProperty("vc") as String?)?.toInt() ?: 1
android {
    namespace = "com.xcluice.jarvis"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.xcluice.jarvis"
        minSdk = 26; targetSdk = 34
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        versionCode = vc; versionName = "1.$vc"
    }
    buildFeatures { buildConfig = true }
    signingConfigs { create("rel") {
        storeFile = file("../jarvis.jks"); storePassword = "jarvis123"; keyAlias = "jarvis"; keyPassword = "jarvis123"
    } }
    buildTypes { release { signingConfig = signingConfigs.getByName("rel"); isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.alphacephei:vosk-android:0.3.47")
}
