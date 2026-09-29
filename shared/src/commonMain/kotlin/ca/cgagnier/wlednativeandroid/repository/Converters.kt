package ca.cgagnier.wlednativeandroid.repository

import androidx.room.TypeConverter
import ca.cgagnier.wlednativeandroid.model.Branch

class Converters {
    @TypeConverter
    fun toBranch(value: String) = enumValueOf<Branch>(value.uppercase())

    @TypeConverter
    fun fromBranch(value: Branch) = value.name
}
