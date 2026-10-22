package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import ca.cgagnier.wlednativeandroid.BuildConfig
import co.touchlab.kermit.Logger
import io.github.z4kn4fein.semver.toVersion
import io.github.z4kn4fein.semver.toVersionOrNull
import java.io.IOException
import io.github.z4kn4fein.semver.Version as SemVersion

private const val TAG = "ChangelogProvider"
private val logger = Logger.withTag(TAG)

data class VersionChangelog(val version: String, val content: String)

class ChangelogProvider(private val context: Context) {
    internal var isDebugBuild: Boolean = BuildConfig.DEBUG

    fun getChangelog(lastSeenVersionStr: String, currentVersionStr: String): List<VersionChangelog> {
        val lastSeenVersion = parseSemverSafe(lastSeenVersionStr) ?: DEFAULT_VERSION
        val currentVersion = parseSemverSafe(currentVersionStr) ?: return emptyList()

        val validChangelogs = getValidChangelogs(lastSeenVersion, currentVersion)
        if (validChangelogs.isEmpty()) {
            return emptyList()
        }

        return buildChangelogEntries(validChangelogs)
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

        val shouldIncludeDev = isDebugBuild ||
            currentVersion.preRelease?.contains("beta", ignoreCase = true) == true
        val validFiles = mutableListOf<ChangelogFile>()

        val devFilename = files.firstOrNull { it.equals("dev.md", ignoreCase = true) }
        if (shouldIncludeDev && devFilename != null) {
            validFiles.add(ChangelogFile(DEV_VERSION, devFilename, "Dev"))
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

                if (fileVersion == null ||
                    fileVersion <= lastSeenVersion ||
                    fileVersion > currentVersion
                ) {
                    return@mapNotNull null
                }

                ChangelogFile(fileVersion, filename)
            },
        )

        return validFiles.sortedByDescending { it.fileVersion }
    }

    private fun buildChangelogEntries(validChangelogs: List<ChangelogFile>): List<VersionChangelog> =
        validChangelogs.mapNotNull { changelogFile ->
            try {
                val content = context.assets.open("$CHANGELOG_DIR/${changelogFile.filename}")
                    .bufferedReader()
                    .use { it.readText() }
                    .trim()

                if (content.isEmpty()) {
                    return@mapNotNull null
                }

                VersionChangelog(
                    version = "Version ${changelogFile.displayVersion}",
                    content = content,
                )
            } catch (e: IOException) {
                logger.e(e) { "Failed to read ${changelogFile.filename}" }
                null
            }
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
        private val DEV_VERSION = "999.0.0".toVersion(strict = false)
    }
}
