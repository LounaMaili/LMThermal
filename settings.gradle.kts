pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "LMThermal"
include(":core", ":app")
// Disposable R2 tooling. Only androidTest consumes it; no product recorder is exposed.
include(":recording-r2")
