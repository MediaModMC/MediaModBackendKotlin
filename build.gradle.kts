plugins {
    kotlin("jvm") version "1.3.72"
    application
}

group = "org.mediamod"
version = "1.0-SNAPSHOT"

application {
    mainClassName = "org.mediamod.backend.MMBackendKt"
}

repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))

    implementation("io.ktor:ktor-server-netty:1.3.2")
    implementation("io.ktor:ktor-client-apache:1.3.2")

    implementation("io.ktor:ktor-gson:1.3.2")
    implementation("io.ktor:ktor-client-gson:1.3.2")

    testImplementation("io.ktor:ktor-server-test-host:1.3.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.6.2")

    implementation("org.litote.kmongo:kmongo-coroutine:4.0.3")

    // Required for log messages to print
    implementation("ch.qos.logback:logback-classic:1.2.1")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

// config JVM target to 1.8 for kotlin compilation tasks
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "1.8"
}