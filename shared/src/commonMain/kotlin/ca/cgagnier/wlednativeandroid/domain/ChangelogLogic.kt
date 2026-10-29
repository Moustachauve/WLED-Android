package ca.cgagnier.wlednativeandroid.domain

import co.touchlab.kermit.Logger
import io.github.z4kn4fein.semver.toVersion
import io.github.z4kn4fein.semver.toVersionOrNull
import io.github.z4kn4fein.semver.Version as SemVersion

private const val TAG = "ChangelogLogic"
private val logger = Logger.withTag(TAG)

data class VersionChangelog(val version: String, val content: String, val isDev: Boolean = false)

class ChangelogSource(val listFilenames: () -> List<String>, val readContent: (filename: String) -> String?)

object ChangelogLogic {
    private const val MARKDOWN_EXTENSION = ".md"
    private const val DEV_VERSION_KEY = "dev"
    private const val DEFAULT_LANGUAGE_CODE = "en"
    private const val MIN_LANGUAGE_CODE_LENGTH = 2
    private const val MAX_LANGUAGE_CODE_LENGTH = 3
    private val DEFAULT_VERSION = "0.0.0".toVersion(strict = false)
    private val DEV_VERSION = "999.0.0".toVersion(strict = false)

    fun getChangelog(
        lastSeenVersionStr: String,
        currentVersionStr: String,
        isDebugBuild: Boolean = false,
        languageCode: String = DEFAULT_LANGUAGE_CODE,
        source: ChangelogSource,
    ): List<VersionChangelog> {
        val lastSeenVersion = parseSemverSafe(lastSeenVersionStr) ?: DEFAULT_VERSION
        val currentVersion = parseSemverSafe(currentVersionStr) ?: return emptyList()
        val normalizedLanguage = normalizeLanguageCode(languageCode)

        val validChangelogs = getValidChangelogs(
            filenames = source.listFilenames(),
            lastSeenVersion = lastSeenVersion,
            currentVersion = currentVersion,
            isDebugBuild = isDebugBuild,
        )
        if (validChangelogs.isEmpty()) {
            return emptyList()
        }

        return buildChangelogEntries(validChangelogs, normalizedLanguage, source.readContent)
    }

    internal fun normalizeLanguageCode(languageCode: String): String {
        val primarySubtag = languageCode
            .trim()
            .split('-', '_')
            .firstOrNull()
            ?.lowercase()
            .orEmpty()
        return if (isLanguageSubtag(primarySubtag)) primarySubtag else DEFAULT_LANGUAGE_CODE
    }

    private fun isLanguageSubtag(subtag: String): Boolean =
        subtag.length in MIN_LANGUAGE_CODE_LENGTH..MAX_LANGUAGE_CODE_LENGTH &&
            subtag.all { it in 'a'..'z' || it in 'A'..'Z' }

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

        return filenames.mapNotNull { filename ->
            parseChangelogFilename(
                filename = filename,
                lastSeenVersion = lastSeenVersion,
                currentVersion = currentVersion,
                shouldIncludeDev = shouldIncludeDev,
            )
        }
    }

    private fun parseChangelogFilename(
        filename: String,
        lastSeenVersion: SemVersion,
        currentVersion: SemVersion,
        shouldIncludeDev: Boolean,
    ): ChangelogFile? {
        if (!filename.endsWith(MARKDOWN_EXTENSION, ignoreCase = true) ||
            filename.equals("README.md", ignoreCase = true)
        ) {
            return null
        }

        val stem = filename.dropLast(MARKDOWN_EXTENSION.length)
        val lastDotIndex = stem.lastIndexOf('.')
        val potentialLanguage = if (lastDotIndex > 0) stem.substring(lastDotIndex + 1) else ""
        val hasLanguageSuffix = isLanguageSubtag(potentialLanguage)

        val versionPart = if (hasLanguageSuffix) stem.substring(0, lastDotIndex) else stem
        val fileLanguage = if (hasLanguageSuffix) potentialLanguage.lowercase() else null

        if (versionPart.equals(DEV_VERSION_KEY, ignoreCase = true)) {
            return if (shouldIncludeDev) {
                ChangelogFile(
                    fileVersion = DEV_VERSION,
                    filename = filename,
                    language = fileLanguage,
                    isDev = true,
                )
            } else {
                null
            }
        }

        val fileVersion = parseSemverSafe(versionPart)
        if (fileVersion == null ||
            fileVersion <= lastSeenVersion ||
            fileVersion > currentVersion
        ) {
            return null
        }

        return ChangelogFile(
            fileVersion = fileVersion,
            filename = filename,
            language = fileLanguage,
        )
    }

    private fun buildChangelogEntries(
        validChangelogs: List<ChangelogFile>,
        normalizedLanguage: String,
        readContent: (filename: String) -> String?,
    ): List<VersionChangelog> = validChangelogs
        .groupBy { it.isDev to it.fileVersion }
        .entries
        .sortedByDescending { (_, files) -> files.first().fileVersion }
        .mapNotNull { (_, filesForVersion) ->
            val candidates = selectCandidatesByLanguage(filesForVersion, normalizedLanguage)
            val resolvedContent = candidates.firstNotNullOfOrNull { candidate ->
                readContent(candidate.filename)?.trim()?.takeIf { it.isNotEmpty() }
            } ?: return@mapNotNull null

            val representative = filesForVersion.first()
            VersionChangelog(
                version = if (representative.isDev) DEV_VERSION_KEY else representative.fileVersion.toString(),
                content = resolvedContent,
                isDev = representative.isDev,
            )
        }

    private fun selectCandidatesByLanguage(
        filesForVersion: List<ChangelogFile>,
        normalizedLanguage: String,
    ): List<ChangelogFile> {
        val localized = if (normalizedLanguage != DEFAULT_LANGUAGE_CODE) {
            filesForVersion.filter { it.language == normalizedLanguage }
        } else {
            emptyList()
        }
        val defaultEnglish = filesForVersion.filter {
            it.language == null || it.language == DEFAULT_LANGUAGE_CODE
        }
        return localized + defaultEnglish
    }

    private data class ChangelogFile(
        val fileVersion: SemVersion,
        val filename: String,
        val language: String? = null,
        val isDev: Boolean = false,
    )
}
