package ca.cgagnier.wlednativeandroid.repository

import okio.buffer
import okio.sink
import okio.source
import java.io.InputStream
import java.io.OutputStream

suspend fun UserPreferencesSerializer.readFrom(input: InputStream): UserPreferences = readFrom(input.source().buffer())

suspend fun UserPreferencesSerializer.writeTo(t: UserPreferences, output: OutputStream) {
    val sink = output.sink().buffer()
    writeTo(t, sink)
    sink.flush()
}
