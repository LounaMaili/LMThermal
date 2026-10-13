// Native host conformance does not configure the production Android project or require its SDK.
pluginManagement { repositories { mavenCentral(); gradlePluginPortal() }; plugins { kotlin("jvm") version "2.1.20" } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { mavenCentral() } }
rootProject.name = "LMThermal-LMTR-R3-Host"
include(":lmtr-contract")
project(":lmtr-contract").projectDir = file("../../lmtr-contract")

include(":lmtr-recorder")
project(":lmtr-recorder").projectDir = file("../../lmtr-recorder")
