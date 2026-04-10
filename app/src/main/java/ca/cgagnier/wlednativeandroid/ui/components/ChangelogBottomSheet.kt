package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.ChangelogViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogBottomSheet(viewModel: ChangelogViewModel, modifier: Modifier = Modifier) {
    val changelogContent by viewModel.changelogContent.collectAsStateWithLifecycle()

    if (changelogContent != null) {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = false,
        )
        val scope = rememberCoroutineScope()

        ModalBottomSheet(
            onDismissRequest = { viewModel.dismiss() },
            sheetState = sheetState,
            modifier = modifier,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 24.dp),
            ) {
                ChangelogTitle()

                changelogContent?.let {
                    ChangelogContent(it, Modifier.weight(1f, fill = false))
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(text = stringResource(id = R.string.changelog_dismiss_button))
                }
            }
        }
    }
}

@Composable
private fun ChangelogTitle() {
    val titleGradient = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.tertiary,
        ),
    )

    Text(
        text = stringResource(id = R.string.changelog_title),
        style = MaterialTheme.typography.displaySmall.copy(
            brush = titleGradient,
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
            shadow = Shadow(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.1f),
                blurRadius = 4f,
            ),
        ),
        modifier = Modifier.padding(bottom = 16.dp),
    )
}

@Composable
private fun ChangelogContent(content: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
    ) {
        WledMarkdown(
            content = content,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
