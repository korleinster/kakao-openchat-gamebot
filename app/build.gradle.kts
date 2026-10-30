plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.korleinster.kakaobot"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.korleinster.kakaobot"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.0-test"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs.getByName("debug") { storeFile = rootProject.file("work/toolchain/debug.keystore") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    buildTypes { release { isMinifyEnabled = false } }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
