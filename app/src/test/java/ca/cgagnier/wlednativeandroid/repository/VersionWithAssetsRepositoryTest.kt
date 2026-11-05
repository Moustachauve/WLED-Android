package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.model.Version
import com.diffplug.selfie.Selfie.expectSelfie
import io.github.z4kn4fein.semver.toVersionOrNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class VersionWithAssetsRepositoryTest {

    @Test
    fun semVerComparator_sortsCorrectly() {
        val versions = listOf(
            createVersion("0.15.2", "2026-01-01T00:00:00Z"),
            createVersion("0.15.3", "2026-03-01T00:00:00Z"),
            createVersion("0.16.0", "2026-05-01T00:00:00Z"),
            createVersion("16.0.0", "2025-01-01T00:00:00Z"), // Older date, newer semver
            createVersion("invalid-tag", "2026-06-01T00:00:00Z"), // Fallback to date
            createVersion("invalid-old", "2026-02-01T00:00:00Z"), // Fallback to date
        ).shuffled()

        val parsedVersions = versions.map {
            it to it.tagName.toVersionOrNull(strict = false)
        }
        val sorted = parsedVersions.sortedWith(VersionWithAssetsRepository.semVerComparator).map { it.first }

        // Invalid semver tags are sorted by date and placed before valid semver tags
        expectSelfie(
            sorted.map {
                it.tagName
            }.joinToString(", "),
        ).toBe("invalid-old, invalid-tag, 0.15.2, 0.15.3, 0.16.0, 16.0.0")

        val latest = VersionWithAssetsRepository.getLatestVersion(versions)
        assertEquals("16.0.0", latest?.tagName)
    }

    @Test
    fun semVerComparator_breaksTieUsingPublishedDateWhenSemverIsEqual() {
        val v1 = createVersion("0.15.2", "2026-01-01T00:00:00Z")
        val v2 = createVersion("0.15.2", "2026-02-01T00:00:00Z")
        val pair1 = v1 to v1.tagName.toVersionOrNull(strict = false)
        val pair2 = v2 to v2.tagName.toVersionOrNull(strict = false)

        val cmp = VersionWithAssetsRepository.semVerComparator.compare(pair1, pair2)
        org.junit.jupiter.api.Assertions.assertTrue(cmp < 0)
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
