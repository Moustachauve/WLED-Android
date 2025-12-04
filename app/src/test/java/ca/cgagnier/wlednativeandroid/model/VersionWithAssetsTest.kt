package ca.cgagnier.wlednativeandroid.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionWithAssetsTest {

    @Test
    fun versionWithAssets_holdsCorrectData() {
        val version = Version(
            tagName = "v1.0.0",
            name = "Release 1.0.0",
            description = "Description",
            isPrerelease = false,
            publishedDate = "2023-01-01",
            htmlUrl = "http://example.com"
        )
        val asset1 = Asset(
            versionTagName = "v1.0.0",
            name = "asset1",
            size = 100L,
            downloadUrl = "http://example.com/asset1",
            assetId = 1
        )
        val asset2 = Asset(
            versionTagName = "v1.0.0",
            name = "asset2",
            size = 200L,
            downloadUrl = "http://example.com/asset2",
            assetId = 2
        )

        val versionWithAssets = VersionWithAssets(
            version = version,
            assets = listOf(asset1, asset2)
        )

        assertEquals(version, versionWithAssets.version)
        assertEquals(2, versionWithAssets.assets.size)
        assertEquals(asset1, versionWithAssets.assets[0])
        assertEquals(asset2, versionWithAssets.assets[1])
    }
}
