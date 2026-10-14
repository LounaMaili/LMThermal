// Disposable test application: no UVC/JNI, native ABI filter, USB permission or product UI.
plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "org.lmthermal.art"
    compileSdk = 35
    defaultConfig { applicationId = "org.lmthermal.art"; minSdk = 26; targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("androidTest").apply {
        java.srcDir("../lmtr-recorder/src/test/kotlin")
        java.srcDir("../camera-recording/src/test/kotlin")
        resources.srcDir("../core/src/test/resources")
        assets.srcDir("../lmtr-contract/src/test/resources")
    }
}
dependencies {
    implementation(project(":lmtr-contract")); implementation(project(":lmtr-recorder")); implementation(project(":camera-recording")); implementation(project(":core"))
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
android.sourceSets.getByName("androidTest").java.srcDir("../lmtr-field-support/src/test/kotlin")
dependencies { androidTestImplementation(project(":lmtr-field-support")) }
