/*
 * This file was generated by the Gradle("init") task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.10.2/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    id("java")
    application
    id("com.palantir.git-version") version "3.0.0"
}


group = "com.gtnewhorizons"
val gitVersion: groovy.lang.Closure<String> by extra
val detectedVersion: String = System.getenv("VERSION") ?: gitVersion()
version = detectedVersion

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation(libs.junit.jupiter)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is used by the application.
    implementation(libs.guava)
    implementation("org.apache.maven:maven-artifact:3.9.9")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.apache.logging.log4j:log4j-api:2.20.0")
    implementation("org.apache.logging.log4j:log4j-core:2.20.0")
    implementation("info.picocli:picocli:4.7.6")
    annotationProcessor("info.picocli:picocli-codegen:4.7.6")

    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")

    testCompileOnly("org.projectlombok:lombok:1.18.36")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.36")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "GTNHNightlyUpdater.Main"
    tasks {
        // command line args example in a dev env
        run.get().args = listOf(
                "-l",
                "--add",
                "-m", "/mnt/games/Minecraft/Instances/GTNH Nightly/.minecraft/",
                "-s", "CLIENT",
                "--add",
                "-m", "/mnt/docker/appdata/minecraft/gtnh/",
                "-s", "server"
        )
    }
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    // Skip git-based versioning inside the tests
    environment("VERSION", "1.0.0")
}

tasks {
    withType<Jar> {
        manifest {
            attributes["Main-Class"] = application.mainClass
        }
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        // here zip stuff found in runtimeClasspath:
        from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    }
}
