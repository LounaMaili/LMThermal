plugins { kotlin("jvm") }
kotlin { jvmToolchain(17) }
dependencies { implementation(project(":core")); testImplementation("junit:junit:4.13.2") }

// Diagnostic tool entry point; these bytes are explicitly noncanonical R2 experiments.
tasks.register<JavaExec>("prototype") {
    dependsOn("classes")
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.lmthermal.r2.PrototypeCommand")
    doFirst { args(providers.gradleProperty("r2Args").get().split(" ")) }
}
