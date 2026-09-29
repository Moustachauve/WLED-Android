package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import co.touchlab.kermit.Logger
import io.github.z4kn4fein.semver.toVersion
import io.github.z4kn4fein.semver.toVersionOrNull
import java.io.IOException
import io.github.z4kn4fein.semver.Version as SemVersion

private const val TAG = "ChangelogProvider"
private val logger = Logger.withTag(TAG)

class ChangelogProvider(private val context: Context) {
    fun getChangelog(lastSeenVersionStr: String, currentVersionStr: String): String? {
        val lastSeenVersion = parseSemverSafe(lastSeenVersionStr) ?: DEFAULT_VERSION
        val currentVersion = parseSemverSafe(currentVersionStr) ?: return null

        val validChangelogs = getValidChangelogs(lastSeenVersion, currentVersion)
        if (validChangelogs.isEmpty()) {
            return null
        }

        return buildChangelogContent(validChangelogs)
    }

    private fun parseSemverSafe(versionStr: String): SemVersion? {
        val parsed = versionStr.toVersionOrNull(strict = false)
        if (parsed == null) {
            logger.d { "Invalid version string: $versionStr" }
        }
        return parsed
    }

    private fun getValidChangelogs(lastSeenVersion: SemVersion, currentVersion: SemVersion): List<ChangelogFile> {
        val files = try {
            context.assets.list(CHANGELOG_DIR) ?: emptyArray()
        } catch (e: IOException) {
            logger.e(e) { "Failed to list changelog assets" }
            return emptyList()
        }

        val hasBeta = currentVersion.preRelease?.contains("beta", ignoreCase = true) == true
        val validFiles = mutableListOf<ChangelogFile>()

        val devFilename = files.firstOrNull { it.equals("dev.md", ignoreCase = true) }
        if (hasBeta && devFilename != null) {
            validFiles.add(ChangelogFile("999.0.0".toVersion(strict = false), devFilename, "Dev"))
        }

        validFiles.addAll(
            files.mapNotNull { filename ->
                if (!filename.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ||
                    filename.equals("dev.md", ignoreCase = true) ||
                    filename.equals("README.md", ignoreCase = true)
                ) {
                    return@mapNotNull null
                }

                val versionPart = filename.removeSuffix(MARKDOWN_EXTENSION)
                val fileVersion = parseSemverSafe(versionPart)

                if (fileVersion != null &&
                    fileVersion > lastSeenVersion &&
                    fileVersion <= currentVersion
                ) {
                    ChangelogFile(fileVersion, filename)
                } else {
                    null
                }
            },
        )

        return validFiles.sortedByDescending { it.fileVersion }
    }

    private fun buildChangelogContent(validChangelogs: List<ChangelogFile>): String {
        val stringBuilder = StringBuilder()

        validChangelogs.forEachIndexed { index, changelogFile ->
            try {
                val content = context.assets.open("$CHANGELOG_DIR/${changelogFile.filename}")
                    .bufferedReader()
                    .use { it.readText() }

                stringBuilder.append("# Version ${changelogFile.displayVersion}\n\n")
                // Add double newlines before headers in the content for better spacing
                val spacedContent = content.trim().replace(Regex("(?m)^(#{1,6} )"), "\n$1")
                stringBuilder.append(spacedContent)
                stringBuilder.append("\n\n")

                if (index < validChangelogs.size - 1) {
                    stringBuilder.append("<br/>\n\n---\n\n<br/>\n\n")
                }
            } catch (e: IOException) {
                logger.e(e) { "Failed to read ${changelogFile.filename}" }
            }
        }

        return stringBuilder.toString().trim()
    }

    private data class ChangelogFile(
        val fileVersion: SemVersion,
        val filename: String,
        val displayVersion: String = fileVersion.toString(),
    )

    companion object {
        private const val CHANGELOG_DIR = "changelog"
        private const val MARKDOWN_EXTENSION = ".md"
        private val DEFAULT_VERSION = "0.0.0".toVersion(strict = false)
    }
}
