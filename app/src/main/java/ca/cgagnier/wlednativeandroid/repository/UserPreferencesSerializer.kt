package ca.cgagnier.wlednativeandroid.repository

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

class UserPreferencesSerializer(
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    },
) : Serializer<UserPreferences> {
    override val defaultValue: UserPreferences
        get() = UserPreferences()

    override suspend fun readFrom(input: InputStream): UserPreferences = try {
        withContext(Dispatchers.IO) {
            val text = input.bufferedReader().use { it.readText() }
            if (text.isBlank()) {
                defaultValue
            } else {
                json.decodeFromString(UserPreferences.serializer(), text)
            }
        }
    } catch (exception: SerializationException) {
        throw CorruptionException("Cannot read JSON user preferences.", exception)
    }

    override suspend fun writeTo(t: UserPreferences, output: OutputStream) {
        withContext(Dispatchers.IO) {
            val text = json.encodeToString(UserPreferences.serializer(), t)
            output.write(text.toByteArray(Charsets.UTF_8))
        }
    }
}
