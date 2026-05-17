plugins {
    kotlin("jvm") version "2.0.21"
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.sellercockpit"
            artifactId = "domain-model"
            version = "0.1.0"
        }
    }
}
