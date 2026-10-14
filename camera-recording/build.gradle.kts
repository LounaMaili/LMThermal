plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":core")); implementation(project(":lmtr-recorder")); implementation(project(":lmtr-contract"))
    testImplementation("junit:junit:4.13.2"); testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1") }
// Reuse the existing deterministic R4 producer, not a second recorder or corpus.
sourceSets["test"].kotlin.apply { srcDir("../lmtr-recorder/src/test/kotlin"); exclude("**/RecorderEngineTest.kt","**/RecordingInteropProbe.kt") }
tasks.register("writeTestRuntimeClasspath") { dependsOn("testClasses")
    doLast { layout.buildDirectory.file("test-runtime-classpath.txt").get().asFile.writeText(sourceSets["test"].runtimeClasspath.asPath) } }
sourceSets["test"].resources.srcDir("../core/src/test/resources")
