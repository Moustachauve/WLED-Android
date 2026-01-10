
plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

spotless {
    // Only use ratchet locally. On CI, check all files (faster than fetching git history)
    if (System.getenv("CI").isNullOrEmpty()) {
        ratchetFrom("origin/dev")
    }

    kotlinGradle {
        target("*.kts") // Targets root build.gradle.kts and settings.gradle.kts
        ktlint()
    }
}

val detektVersion = libs.versions.detekt.get()

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        if (System.getenv("CI").isNullOrEmpty()) {
            ratchetFrom("origin/dev")
        }

        kotlin {
            target("**/*.kt")
            // Exclude build folders to save performance
            targetExclude("**/build/**/*.kt")
            ktlint().editorConfigOverride(
                mapOf(
                    "ktlint_standard_value-argument-comment" to "disabled",
                    "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                    "max_line_length" to "120",
                ),
            )
            trimTrailingWhitespace()
            leadingTabsToSpaces()
            endWithNewline()
        }

        kotlinGradle {
            target("*.kts")
            ktlint()
            trimTrailingWhitespace()
            leadingTabsToSpaces()
            endWithNewline()
        }

        format("xml") {
            target("**/*.xml")
            targetExclude("**/build/**/*.xml", ".idea/**/*.xml")
            trimTrailingWhitespace()
            leadingTabsToSpaces()
            endWithNewline()
        }
    }

    configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        toolVersion = detektVersion
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
        baseline = file("$projectDir/detekt-baseline.xml")
    }
}

tasks.register("installGitHooks") {
    description = "Installs a git pre-commit hook to run Spotless"
    group = "help"

    // Capture the root directory here, during configuration, not execution
    val rootDir = layout.projectDirectory.asFile

    doLast {
        val hooksDir = File(rootDir, ".git/hooks")
        if (!hooksDir.exists()) {
            hooksDir.mkdirs()
        }
        val preCommitFile = File(hooksDir, "pre-commit")
        preCommitFile.writeText(
            """
                        #!/bin/bash
            echo "Running Spotless check..."
            ./gradlew spotlessCheck
            if [ $? -ne 0 ]; then
                echo "Spotless check failed. Automatically formatting..."
                ./gradlew spotlessApply
                echo "Code has been reformatted. Please review, stage, and commit again."
                exit 1
            fi

            echo "Running Detekt check..."
            ./gradlew detekt
            if [ $? -ne 0 ]; then
                echo "Detekt found issues. Please fix them manually and commit again."
                exit 1
            fi

            echo "All checks passed!"
            exit 0
            """.trimIndent(),
        )

        preCommitFile.setExecutable(true)
        println("Git pre-commit hook installed successfully.")
    }
}
