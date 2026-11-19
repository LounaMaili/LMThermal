import javax.inject.Inject

// Explicitly separate, debuggable field APK. Production app never depends on this project.
plugins { id("com.android.application"); kotlin("android"); id("org.jetbrains.kotlin.plugin.compose") }
val fieldAbi = if (providers.gradleProperty("fieldX86").orNull == "true") "x86_64" else "arm64-v8a"
// Package only three known synthetic examples in the debug field variant.
abstract class CopyRecordingDemoAssets @Inject constructor(private val files: FileSystemOperations) : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val examples: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction fun copy() = files.sync {
        into(outputDirectory)
        into("recording-demo") { from(examples) }
    }
}
val copyRecordingDemoAssets = tasks.register<CopyRecordingDemoAssets>("copyRecordingDemoAssets") {
    examples.from(fileTree("../lmtr-contract/src/test/resources/lmtr/files") {
        include("illustration-06-stored.lmtr", "missing-end.lmtr", "committed-logical-hash-corrupt.lmtr")
    })
    outputDirectory.set(layout.buildDirectory.dir("generated/recording-demo-assets"))
}
android {
    namespace="org.lmthermal.app"
    compileSdk=35
    ndkVersion="28.0.13004108"
    defaultConfig {
        applicationId="org.lmthermal.field";minSdk=26;targetSdk=35;versionCode=1;versionName="r4-field-1"
        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += fieldAbi }
    }
    buildFeatures { compose=true;buildConfig=true }
    buildTypes { getByName("debug"){isDebuggable=true};getByName("release"){isDebuggable=true} }
    sourceSets.getByName("main").apply {
        java.srcDir("../app/src/main/java");java.exclude("**/MainActivity.kt")
        res.srcDir("../app/src/main/res")
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
    externalNativeBuild { cmake { path=file("../app/src/main/cpp/CMakeLists.txt");version="3.22.1" } }
}
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copyRecordingDemoAssets, CopyRecordingDemoAssets::outputDirectory)
}
dependencies {
    implementation(project(":core"));implementation(project(":lmtr-contract"));implementation(project(":lmtr-recorder"))
    implementation(project(":camera-recording"));implementation(project(":lmtr-field-support"))
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1");implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.compose.material3:material3");implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7");implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
}
