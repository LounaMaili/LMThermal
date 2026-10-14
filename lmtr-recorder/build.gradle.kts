plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":lmtr-contract")); testImplementation("junit:junit:4.13.2") }
tasks.register("writeTestRuntimeClasspath") {
    dependsOn("testClasses")
    doLast { layout.buildDirectory.file("test-runtime-classpath.txt").get().asFile.writeText(sourceSets["test"].runtimeClasspath.asPath) }
}
