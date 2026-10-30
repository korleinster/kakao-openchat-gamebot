import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.korleinster.kakaobot"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.korleinster.kakaobot"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildToolsVersion = "35.0.0"
    signingConfigs.getByName("debug") { storeFile = rootProject.file("work/toolchain/debug.keystore") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions { unitTests.isReturnDefaultValues = true }
    val upload = Properties()
    val uploadFile = rootProject.file("signing.properties")
    if(uploadFile.exists()) {
        uploadFile.inputStream().use { upload.load(it) }
        listOf("storeFile","storePassword","keyAlias","keyPassword").forEach { require(!upload.getProperty(it).isNullOrBlank()) { "Incomplete signing.properties" } }
        signingConfigs.create("upload") {
            storeFile = rootProject.file(upload.getProperty("storeFile")); storePassword = upload.getProperty("storePassword")
            keyAlias = upload.getProperty("keyAlias"); keyPassword = upload.getProperty("keyPassword")
        }
    }
    buildTypes { release {
        isMinifyEnabled = false
        isDebuggable = false
        if(uploadFile.exists()) signingConfig = signingConfigs.getByName("upload")
    } }
    lint { abortOnError = true; checkReleaseBuilds = true }
}
dependencies {
    implementation("androidx.activity:activity-ktx:1.11.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
