
plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            ktlint().editorConfigOverride(
                mapOf(
                    "ktlint_standard_value-argument-comment" to "disabled",
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable"
                )
            )
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        toolVersion = "1.23.7"
        buildUponDefaultConfig = true
        baseline = file("$projectDir/detekt-baseline.xml")
    }
}

tasks.register("clean",Delete::class){
    delete(layout.buildDirectory)
}