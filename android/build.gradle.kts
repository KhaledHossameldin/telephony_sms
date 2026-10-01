group = "com.khaledhossameldin.telephony_sms"
version = "1.0-SNAPSHOT"

buildscript {
    val kotlinVersion = "2.4.20"
    repositories {
        google()
        mavenCentral()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:9.3.1")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("com.android.library")
}

android {
    namespace = "com.khaledhossameldin.telephony_sms"

    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
    }

    defaultConfig {
        minSdk = 24
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.useJUnitPlatform()

                it.outputs.upToDateWhen { false }

                it.testLogging {
                    events("passed", "skipped", "failed", "standardOut", "standardError")
                    showStandardStreams = true
                }
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    // Used for ActivityCompat, ContextCompat.checkSelfPermission and
    // ContextCompat.registerReceiver. It previously arrived only transitively
    // through the Flutter embedding; registerReceiver with the exported flag
    // needs 1.9.0 or newer, so the floor is declared rather than assumed.
    //
    // Held at 1.18.0 on purpose: its AAR declares minCompileSdk=36, and 1.19.0
    // raises that to 37. Nothing in this plugin needs anything past 1.9.0, so
    // there is no reason to move until Flutter's default compileSdk does.
    // dependabot is configured to stop proposing 1.19.0 and above.
    implementation("androidx.core:core:1.18.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.mockito:mockito-core:5.24.0")
}
