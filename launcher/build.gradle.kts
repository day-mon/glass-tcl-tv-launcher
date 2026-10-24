buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 compiles Kotlin itself and bundles KGP 2.2.10; this opts into the current one.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
}
