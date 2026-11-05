package ca.cgagnier.wlednativeandroid.domain

import android.content.Context
import androidx.core.os.ConfigurationCompat
import ca.cgagnier.wlednativeandroid.BuildConfig
import co.touchlab.kermit.Logger
import java.io.IOException
import java.util.Locale

private const val TAG = "ChangelogProvider"
private val logger = Logger.withTag(TAG)

class ChangelogProvider(private val context: Context) {
    internal var isDebugBuild: Boolean = BuildConfig.DEBUG
    internal var languageProvider: () -> String = {
        ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language
            ?: Locale.getDefault().language
    }

    fun getChangelog(lastSeenVersionStr: String, currentVersionStr: String): List<VersionChangelog> =
        ChangelogLogic.getChangelog(
            lastSeenVersionStr = lastSeenVersionStr,
            currentVersionStr = currentVersionStr,
            isDebugBuild = isDebugBuild,
            languageCode = languageProvider(),
            source = ChangelogSource(
                listFilenames = {
                    try {
                        context.assets.list(CHANGELOG_DIR)?.toList().orEmpty()
                    } catch (e: IOException) {
                        logger.e(e) { "Failed to list changelog assets" }
                        emptyList()
                    }
                },
                readContent = { filename ->
                    try {
                        context.assets.open("$CHANGELOG_DIR/$filename")
                            .bufferedReader()
                            .use { it.readText() }
                    } catch (e: IOException) {
                        logger.e(e) { "Failed to read $filename" }
                        null
                    }
                },
            ),
        )

    companion object {
        private const val CHANGELOG_DIR = "changelog"
    }
}
