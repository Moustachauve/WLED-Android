package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.domain.VersionChangelog
import ca.cgagnier.wlednativeandroid.ui.ChangelogViewModel
import ca.cgagnier.wlednativeandroid.ui.theme.PixelifySansFontFamily
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogBottomSheet(viewModel: ChangelogViewModel, modifier: Modifier = Modifier) {
    val changelogs by viewModel.changelogs.collectAsStateWithLifecycle()

    if (changelogs.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismiss() },
            sheetState = sheetState,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 6.dp)
                        .size(width = 32.dp, height = 4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = CircleShape,
                        ),
                )
            },
            modifier = modifier,
        ) {
            ChangelogSheetContent(
                changelogs = changelogs,
                onDismiss = {
                    scope.launch {
                        sheetState.hide()
                        viewModel.dismiss()
                    }
                },
            )
        }
    }
}

@Composable
internal fun ChangelogSheetContent(
    changelogs: List<VersionChangelog>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        ChangelogTitle()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        ) {
            ChangelogVersionList(
                changelogs = changelogs,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onDismiss,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 8.dp,
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 16.dp),
            ) {
                Text(text = stringResource(id = R.string.changelog_dismiss_button))
            }
        }
    }
}

@Composable
private fun ChangelogTitle() {
    val titleText = stringResource(id = R.string.changelog_title)
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    val steppedTitle = remember(titleText, primary, secondary, tertiary) {
        buildSteppedGradientText(
            text = titleText,
            colors = listOf(primary, secondary, tertiary),
        )
    }

    Text(
        text = steppedTitle,
        style = MaterialTheme.typography.displaySmall.copy(
            fontFamily = PixelifySansFontFamily,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.5).sp,
        ),
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

internal fun buildSteppedGradientText(text: String, colors: List<Color>): AnnotatedString {
    if (text.isEmpty() || colors.isEmpty()) {
        return AnnotatedString(text)
    }
    if (colors.size == 1) {
        return buildAnnotatedString {
            withStyle(SpanStyle(color = colors.first())) {
                append(text)
            }
        }
    }

    val nonWhitespaceCount = text.count { !it.isWhitespace() }
    var visibleIndex = 0

    return buildAnnotatedString {
        for (char in text) {
            if (char.isWhitespace()) {
                append(char)
            } else {
                val fraction = if (nonWhitespaceCount <= 1) {
                    0f
                } else {
                    visibleIndex.toFloat() / (nonWhitespaceCount - 1).toFloat()
                }
                val color = interpolateColorStops(colors, fraction)
                withStyle(SpanStyle(color = color)) {
                    append(char)
                }
                visibleIndex++
            }
        }
    }
}

private fun interpolateColorStops(colors: List<Color>, fraction: Float): Color {
    val scaled = fraction * (colors.size - 1)
    val segmentIndex = scaled.toInt().coerceAtMost(colors.size - 2)
    val segmentFraction = scaled - segmentIndex
    return lerp(colors[segmentIndex], colors[segmentIndex + 1], segmentFraction)
}

@Composable
private fun ChangelogVersionList(changelogs: List<VersionChangelog>, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        changelogs.forEach { versionChangelog ->
            ChangelogVersionCard(versionChangelog = versionChangelog)
        }
        Spacer(modifier = Modifier.height(68.dp))
    }
}

@Composable
private fun ChangelogVersionCard(versionChangelog: VersionChangelog, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = versionChangelog.version,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = PixelifySansFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.2).sp,
                ),
                modifier = Modifier.padding(bottom = 4.dp),
            )
            WledMarkdown(
                content = versionChangelog.content,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
