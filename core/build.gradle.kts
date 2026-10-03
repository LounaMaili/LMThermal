plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1"); testImplementation("junit:junit:4.13.2") }

// Developer-only camera-free conformance tools; no test assets enter the Android runtime.
tasks.register<JavaExec>("checkLmtx") {
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.lmthermal.exchange.LmtxCommand")
    doFirst { args(providers.gradleProperty("lmtxFile").get()) }
}
tasks.register<JavaExec>("generateLmtxFixtures") {
    dependsOn("testClasses")
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("org.lmthermal.exchange.LmtxFixtureGenerator")
    args(layout.projectDirectory.dir("src/test/resources/lmtx").asFile.absolutePath)
}
