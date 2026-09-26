package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.model.Version
import com.diffplug.selfie.Selfie.expectSelfie
import com.vdurmont.semver4j.Semver
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VersionWithAssetsRepositoryTest {

    @Test
    fun semVerComparator_sortsCorrectly() {
        val versions = listOf(
            createVersion("0.14.0", "2023-01-01T00:00:00Z"),
            createVersion("0.15.0", "2023-06-01T00:00:00Z"),
            createVersion("0.15.5", "2023-07-01T00:00:00Z"),
            createVersion("16.0.0", "2022-01-01T00:00:00Z"), // Older date, newer semver
            createVersion("invalid-tag", "2024-01-01T00:00:00Z"), // Fallback to date
            createVersion("invalid-old", "2023-12-01T00:00:00Z"), // Fallback to date
        ).shuffled()

        val parsedVersions = versions.map {
            it to runCatching {
                Semver(it.tagName, Semver.SemverType.LOOSE)
            }.getOrNull()
        }
        val sorted = parsedVersions.sortedWith(VersionWithAssetsRepository.semVerComparator).map { it.first }

        // Invalid semver tags are sorted by date and placed before valid semver tags
        expectSelfie(
            sorted.map {
                it.tagName
            }.joinToString(", "),
        ).toBe("invalid-old, invalid-tag, 0.14.0, 0.15.0, 0.15.5, 16.0.0")

        val latest = VersionWithAssetsRepository.getLatestVersion(versions)
        assertEquals("16.0.0", latest?.tagName)
    }

    private fun createVersion(tagName: String, publishedDate: String): Version = Version(
        id = 0,
        repositoryId = 0,
        tagName = tagName,
        name = tagName,
        description = "",
        isPrerelease = false,
        publishedDate = publishedDate,
        htmlUrl = "",
    )
}
