pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "LMThermal"
include(":core", ":app", ":lmtr-contract", ":lmtr-recorder", ":lmtr-art", ":camera-recording")
// Isolated opt-in field APK and camera-free helpers, not product dependencies.
include(":lmtr-field", ":lmtr-field-support")
