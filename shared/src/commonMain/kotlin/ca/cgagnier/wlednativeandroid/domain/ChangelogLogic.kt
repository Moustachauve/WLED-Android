package ca.cgagnier.wlednativeandroid.domain

import co.touchlab.kermit.Logger
import io.github.z4kn4fein.semver.toVersion
import io.github.z4kn4fein.semver.toVersionOrNull
import io.github.z4kn4fein.semver.Version as SemVersion

private const val TAG = "ChangelogLogic"
private val logger = Logger.withTag(TAG)

data class VersionChangelog(val version: String, val content: String, val isDev: Boolean = false)

object ChangelogLogic {
    private const val MARKDOWN_EXTENSION = ".md"
    private const val DEV_VERSION_KEY = "dev"
    private val DEFAULT_VERSION = "0.0.0".toVersion(strict = false)
    private val DEV_VERSION = "999.0.0".toVersion(strict = false)

    fun getChangelog(
        lastSeenVersionStr: String,
        currentVersionStr: String,
        listFilenames: () -> List<String>,
        isDebugBuild: Boolean = false,
        readContent: (filename: String) -> String?,
    ): List<VersionChangelog> {
        val lastSeenVersion = parseSemverSafe(lastSeenVersionStr) ?: DEFAULT_VERSION
        val currentVersion = parseSemverSafe(currentVersionStr) ?: return emptyList()

        val validChangelogs = getValidChangelogs(
            filenames = listFilenames(),
            lastSeenVersion = lastSeenVersion,
            currentVersion = currentVersion,
            isDebugBuild = isDebugBuild,
        )
        if (validChangelogs.isEmpty()) {
            return emptyList()
        }

        return buildChangelogEntries(validChangelogs, readContent)
    }

    private fun parseSemverSafe(versionStr: String): SemVersion? {
        val parsed = versionStr.toVersionOrNull(strict = false)
        if (parsed == null) {
            logger.d { "Invalid version string: $versionStr" }
        }
        return parsed
    }

    private fun getValidChangelogs(
        filenames: List<String>,
        lastSeenVersion: SemVersion,
        currentVersion: SemVersion,
        isDebugBuild: Boolean,
    ): List<ChangelogFile> {
        val shouldIncludeDev = isDebugBuild ||
            currentVersion.preRelease?.contains("beta", ignoreCase = true) == true
        val validFiles = mutableListOf<ChangelogFile>()

        val devFilename = filenames.firstOrNull { it.equals("dev.md", ignoreCase = true) }
        if (shouldIncludeDev && devFilename != null) {
            validFiles.add(ChangelogFile(fileVersion = DEV_VERSION, filename = devFilename, isDev = true))
        }

        validFiles.addAll(
            filenames.mapNotNull { filename ->
                if (!filename.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ||
                    filename.equals("dev.md", ignoreCase = true) ||
                    filename.equals("README.md", ignoreCase = true)
                ) {
                    return@mapNotNull null
                }

                val versionPart = filename.dropLast(MARKDOWN_EXTENSION.length)
                val fileVersion = parseSemverSafe(versionPart)

                if (fileVersion == null ||
                    fileVersion <= lastSeenVersion ||
                    fileVersion > currentVersion
                ) {
                    return@mapNotNull null
                }

                ChangelogFile(fileVersion = fileVersion, filename = filename)
            },
        )

        return validFiles.sortedByDescending { it.fileVersion }
    }

    private fun buildChangelogEntries(
        validChangelogs: List<ChangelogFile>,
        readContent: (filename: String) -> String?,
    ): List<VersionChangelog> = validChangelogs.mapNotNull { changelogFile ->
        val content = readContent(changelogFile.filename)?.trim()
        if (content.isNullOrEmpty()) {
            return@mapNotNull null
        }

        VersionChangelog(
            version = if (changelogFile.isDev) DEV_VERSION_KEY else changelogFile.fileVersion.toString(),
            content = content,
            isDev = changelogFile.isDev,
        )
    }

    private data class ChangelogFile(val fileVersion: SemVersion, val filename: String, val isDev: Boolean = false)
}
