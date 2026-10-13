// Configure only camera-free runtime modules: production app/NDK/UVC are excluded.
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    plugins {
        id("com.android.application") version "8.9.2"
        kotlin("android") version "2.1.20"
        kotlin("jvm") version "2.1.20"
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "LMThermal-LMTR-R4-ART"
include(":core", ":lmtr-contract", ":lmtr-recorder", ":camera-recording", ":lmtr-art")
for (name in listOf("core", "lmtr-contract", "lmtr-recorder", "camera-recording", "lmtr-art")) {
    project(":$name").projectDir = file("../../$name")
}
