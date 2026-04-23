package ca.cgagnier.wlednativeandroid.model

import androidx.room.Embedded
import androidx.room.Relation

data class VersionWithAssets(
    @Embedded
    val version: Version,
    @Relation(
        parentColumn = "id",
        entityColumn = "versionId",
    )
    val assets: List<Asset>,
)
