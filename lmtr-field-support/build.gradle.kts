// Test-only field protocol helpers; never a product dependency.
plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":camera-recording")); implementation(project(":core")); implementation(project(":lmtr-recorder")); implementation(project(":lmtr-contract")); testImplementation("junit:junit:4.13.2") }
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1") }
sourceSets["test"].kotlin.apply { srcDir("../lmtr-recorder/src/test/kotlin");exclude("**/RecorderEngineTest.kt", "**/RecordingInteropProbe.kt") }
sourceSets["test"].resources.srcDir("../core/src/test/resources")
// JDK management probes must never enter ART's shared src/test/kotlin source directory.
// This adds no test selection/exclusion to the Android instrumentation suite.
kotlin.sourceSets.named("test") { kotlin.srcDir("src/hostTest/kotlin") }
tasks.register("writeTestRuntimeClasspath") { dependsOn("testClasses"); doLast { layout.buildDirectory.file("test-runtime-classpath.txt").get().asFile.writeText(sourceSets["test"].runtimeClasspath.asPath) } }
