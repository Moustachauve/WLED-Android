package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import org.junit.Assert.assertEquals
import org.junit.Test

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

        val sorted = versions.sortedWith(VersionWithAssetsRepository.SemVerComparator)

        // Invalid semver tags are sorted by date and placed before valid semver tags
        assertEquals("invalid-old", sorted[0].version.tagName)
        assertEquals("invalid-tag", sorted[1].version.tagName)
        // Valid semver tags are sorted by semver
        assertEquals("0.14.0", sorted[2].version.tagName)
        assertEquals("0.15.0", sorted[3].version.tagName)
        assertEquals("0.15.5", sorted[4].version.tagName)
        assertEquals("16.0.0", sorted[5].version.tagName)
    }

    private fun createVersion(tagName: String, publishedDate: String): VersionWithAssets = VersionWithAssets(
        version = Version(
            id = 0,
            repositoryId = 0,
            tagName = tagName,
            name = tagName,
            description = "",
            isPrerelease = false,
            publishedDate = publishedDate,
            htmlUrl = "",
        ),
        assets = emptyList(),
    )
}
