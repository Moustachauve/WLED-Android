package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import android.util.Log
import com.vdurmont.semver4j.Semver
import com.vdurmont.semver4j.SemverException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject

class ChangelogProvider @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun getChangelog(lastSeenVersionStr: String, currentVersionStr: String): String? {
        val lastSeenVersion = parseSemverSafe(lastSeenVersionStr) ?: Semver(DEFAULT_VERSION)
        val currentVersion = parseSemverSafe(currentVersionStr) ?: return null

        val validChangelogs = getValidChangelogs(lastSeenVersion, currentVersion)
        if (validChangelogs.isEmpty()) {
            return null
        }

        return buildChangelogContent(validChangelogs)
    }

    private fun parseSemverSafe(versionStr: String): Semver? = try {
        Semver(versionStr.removePrefix(VERSION_PREFIX_LOWER).removePrefix(VERSION_PREFIX_UPPER))
    } catch (e: SemverException) {
        Log.e(TAG, "Invalid version string: $versionStr", e)
        null
    }

    private fun getValidChangelogs(lastSeenVersion: Semver, currentVersion: Semver): List<ChangelogFile> {
        val files = try {
            context.assets.list(CHANGELOG_DIR) ?: emptyArray()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list changelog assets", e)
            return emptyList()
        }

        val hasBeta = currentVersion.value.contains("beta", ignoreCase = true)
        val validFiles = mutableListOf<ChangelogFile>()

        if (hasBeta && files.contains("dev.md")) {
            validFiles.add(ChangelogFile(Semver("999.0.0"), "dev.md", "Dev"))
        }

        validFiles.addAll(
            files.mapNotNull { filename ->
                if (!filename.endsWith(MARKDOWN_EXTENSION) || filename == "dev.md") return@mapNotNull null

                val versionPart = filename.removeSuffix(MARKDOWN_EXTENSION)
                val fileVersion = parseSemverSafe(versionPart)

                if (fileVersion != null &&
                    fileVersion.isGreaterThan(lastSeenVersion) &&
                    fileVersion.isLowerThanOrEqualTo(currentVersion)
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
                Log.e(TAG, "Failed to read ${changelogFile.filename}", e)
            }
        }

        return stringBuilder.toString().trim()
    }

    private data class ChangelogFile(
        val fileVersion: Semver,
        val filename: String,
        val displayVersion: String = fileVersion.value,
    )

    companion object {
        private const val TAG = "ChangelogProvider"
        private const val CHANGELOG_DIR = "changelog"
        private const val MARKDOWN_EXTENSION = ".md"
        private const val VERSION_PREFIX_LOWER = "v"
        private const val VERSION_PREFIX_UPPER = "V"
        private const val DEFAULT_VERSION = "0.0.0"
    }
}
