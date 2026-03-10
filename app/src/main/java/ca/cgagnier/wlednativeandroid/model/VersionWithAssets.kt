package ca.cgagnier.wlednativeandroid.model

import androidx.room.Embedded
import androidx.room.Relation

data class VersionWithAssets(
    @Embedded
    val version: Version,
    @Relation(
        parentColumn = "tagName",
        entityColumn = "versionTagName",
    )
    val assets: List<Asset>,
)
