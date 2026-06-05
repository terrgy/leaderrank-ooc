import org.gradle.kotlin.dsl.repositories

plugins {
    java
    application
}

group = "leaderrank"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "leaderrank.cli.Main"
}

dependencies {
    implementation("org.apache.commons:commons-csv:1.11.0")
    implementation("it.unimi.dsi:fastutil-core:8.5.18")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.26.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
