package ca.cgagnier.wlednativeandroid.repository

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.BufferedSink
import okio.BufferedSource

private val defaultJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = false
}

class UserPreferencesSerializer(private val json: Json = defaultJson) : OkioSerializer<UserPreferences> {
    override val defaultValue: UserPreferences
        get() = UserPreferences()

    override suspend fun readFrom(source: BufferedSource): UserPreferences = try {
        val text = source.readUtf8()
        if (text.isBlank()) {
            defaultValue
        } else {
            json.decodeFromString(UserPreferences.serializer(), text)
        }
    } catch (exception: SerializationException) {
        throw CorruptionException("Cannot read JSON user preferences.", exception)
    }

    override suspend fun writeTo(t: UserPreferences, sink: BufferedSink) {
        val text = json.encodeToString(UserPreferences.serializer(), t)
        sink.writeUtf8(text)
    }
}
