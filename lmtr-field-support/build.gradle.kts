// Test-only field protocol helpers; never a product dependency.
plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":camera-recording")); implementation(project(":core")); implementation(project(":lmtr-recorder")); implementation(project(":lmtr-contract")); testImplementation("junit:junit:4.13.2") }
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1") }
sourceSets["test"].kotlin.apply { srcDir("../lmtr-recorder/src/test/kotlin");exclude("**/RecorderEngineTest.kt", "**/RecordingInteropProbe.kt") }
sourceSets["test"].resources.srcDir("../core/src/test/resources")
tasks.register("writeTestRuntimeClasspath") { dependsOn("testClasses"); doLast { layout.buildDirectory.file("test-runtime-classpath.txt").get().asFile.writeText(sourceSets["test"].runtimeClasspath.asPath) } }
