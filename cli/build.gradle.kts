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

// Portable release ZIP for GitHub releases: prebuilt CLI (bin/ + lib/) plus
// examples, docs, and the one-click installers. Build with `gradlew :cli:distZip`
// -> cli/build/distributions/rotlin.zip. Kids need only a JDK 21+; no Gradle.
distributions {
    main {
        distributionBaseName.set("rotlin")
        contents {
            from(rootProject.file("examples")) { into("examples") }
            from(rootProject.file("docs")) { into("docs") }
            from(rootProject.file("README.md"))
            from(rootProject.file("packaging/installWin.bat"))
            from(rootProject.file("packaging/installMac.command")) { filePermissions { unix("0755") } }
            from(rootProject.file("packaging/installLinux.sh")) { filePermissions { unix("0755") } }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir // so `gradlew :cli:run --args="cook examples/hello.rot"` resolves from the repo root
    standardInput = System.`in`
}
