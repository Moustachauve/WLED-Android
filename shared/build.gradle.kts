import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.skie)
}

kotlin {
    android {
        namespace = "ca.cgagnier.wlednativeandroid.shared"
        compileSdk = 37
        minSdk = 24

        withHostTest {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "ca.cgagnier.wlednativeandroid.shared")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
            implementation(libs.semver)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

skie {
    analytics {
        enabled.set(false)
    }
}
