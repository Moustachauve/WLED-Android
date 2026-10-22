package ca.cgagnier.wlednativeandroid.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChangelogLogicTest {

    @Test
    fun returnsEmptyWhenCurrentVersionIsInvalid() {
        val result = getChangelogFromMap(
            lastSeenVersionStr = "7.0.0",
            currentVersionStr = "not-a-version",
            filesByFilename = mapOf("7.0.1.md" to "Added feature"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsEmptyWhenLastSeenEqualsCurrentVersion() {
        val result = getChangelogFromMap(
            lastSeenVersionStr = "7.0.1",
            currentVersionStr = "7.0.1",
            filesByFilename = mapOf("7.0.1.md" to "Added feature"),
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun returnsVersionsNewerThanLastSeenSortedDescending() {
        val result = getChangelogFromMap(
            lastSeenVersionStr = "7.0.0",
            currentVersionStr = "7.0.2",
            filesByFilename = mapOf(
                "7.0.0.md" to "Initial 7.0.0",
                "7.0.1.md" to "Added new feature X",
                "7.0.2.md" to "Fixed bug Y",
                "7.1.0.md" to "Future release",
            ),
        )

        assertEquals(
            listOf(
                VersionChangelog(version = "7.0.2", content = "Fixed bug Y"),
                VersionChangelog(version = "7.0.1", content = "Added new feature X"),
            ),
            result,
        )
    }

    @Test
    fun includesDevMdForBetaVersion() {
        val result = getChangelogFromMap(
            lastSeenVersionStr = "7.0.0",
            currentVersionStr = "7.1.0-beta1",
            filesByFilename = mapOf(
                "7.0.1.md" to "Stable release 7.0.1",
                "dev.md" to "Bleeding edge updates",
            ),
        )

        assertEquals(
            listOf(
                VersionChangelog(version = "dev", content = "Bleeding edge updates", isDev = true),
                VersionChangelog(version = "7.0.1", content = "Stable release 7.0.1"),
            ),
            result,
        )
    }

    @Test
    fun includesDevMdWhenIsDebugBuildIsTrueOnStableVersion() {
        val result = getChangelogFromMap(
            lastSeenVersionStr = "7.0.0",
            currentVersionStr = "7.0.0",
            filesByFilename = mapOf(
                "dev.md" to "### Latest Dev Changes\n- Added iOS changelog support",
            ),
            isDebugBuild = true,
        )

        assertEquals(
            listOf(
                VersionChangelog(
                    version = "dev",
                    content = "### Latest Dev Changes\n- Added iOS changelog support",
                    isDev = true,
                ),
            ),
            result,
        )
    }

    @Test
    fun ignoresReadmeAndNonMarkdownFiles() {
        val result = getChangelogFromMap(
            lastSeenVersionStr = "7.0.0",
            currentVersionStr = "7.0.1",
            filesByFilename = mapOf(
                "README.md" to "Rules",
                "notes.txt" to "Ignored",
                "7.0.1.md" to "Added new feature X",
            ),
        )

        assertEquals(
            listOf(
                VersionChangelog(version = "7.0.1", content = "Added new feature X"),
            ),
            result,
        )
    }

    private fun getChangelogFromMap(
        lastSeenVersionStr: String,
        currentVersionStr: String,
        filesByFilename: Map<String, String>,
        isDebugBuild: Boolean = false,
    ): List<VersionChangelog> = ChangelogLogic.getChangelog(
        lastSeenVersionStr = lastSeenVersionStr,
        currentVersionStr = currentVersionStr,
        listFilenames = { filesByFilename.keys.toList() },
        isDebugBuild = isDebugBuild,
        readContent = { filesByFilename[it] },
    )
}
