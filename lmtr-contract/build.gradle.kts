plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { testImplementation("junit:junit:4.13.2") }

// Pure contract conformance tool; no app/camera dependency or device execution.
tasks.register<JavaExec>("lmtr") {
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.lmthermal.lmtr.LmtrCommand")
    doFirst { args(providers.gradleProperty("lmtrArgs").get().split("|")) }
}

// Host interoperability uses one JVM per invocation, without rerunning Gradle.
tasks.register("writeRuntimeClasspath") {
    dependsOn("classes")
    val output=layout.buildDirectory.file("runtime-classpath.txt")
    outputs.file(output)
    doLast { output.get().asFile.writeText(sourceSets["main"].runtimeClasspath.asPath) }
}
