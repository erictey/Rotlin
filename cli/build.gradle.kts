plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":compiler"))
    implementation(project(":runtime"))
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.0")
    testImplementation(kotlin("test"))
}

application {
    applicationName = "rotlin"
    mainClass.set("rotlin.cli.MainKt")
    applicationDefaultJvmArgs = listOf("-Xmx1g", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir // so `gradlew :cli:run --args="cook examples/hello.rot"` resolves from the repo root
    standardInput = System.`in`
}
