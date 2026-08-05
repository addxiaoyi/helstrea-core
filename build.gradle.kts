plugins {
    `java-library`
}

group = "io.helstrea"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 17
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.register<Exec>("forwardingSelfTest") {
    workingDir = projectDir
    commandLine("bash", "scripts/test.sh")
}

tasks.named("check") {
    dependsOn("forwardingSelfTest")
}
