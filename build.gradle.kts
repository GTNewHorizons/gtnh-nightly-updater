/*
 * This file was generated by the Gradle("init") task.
 *
 * This generated file contains a sample Java application project to get you started.
 * For more details on building Java & JVM projects, please refer to https://docs.gradle.org/8.10.2/userguide/building_java_projects.html in the Gradle documentation.
 */

plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
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
    implementation("commons-cli:commons-cli:1.9.0")
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
        //run.get().args = listOf("-m", "/mnt/games/Minecraft/Instances/GTNH Nightly/.minecraft/", "-s", "CLIENT", "-l")
        run.get().args = listOf("-m", "/mnt/docker/appdata/minecraft/gtnh/", "-s", "SERVER", "-l")
    }
}


tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
    // Skip git-based versioning inside the tests
    environment("VERSION", "1.0.0")
}
