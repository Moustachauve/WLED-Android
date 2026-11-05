package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.ui.theme.PixelifySansFontFamily
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownHeader
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import org.intellij.markdown.MarkdownElementTypes

@Composable
fun WledMarkdown(content: String, modifier: Modifier = Modifier) {
    Markdown(
        content = content,
        modifier = modifier,
        imageTransformer = Coil3ImageTransformerImpl,
        padding = markdownPadding(
            block = 0.dp,
            list = 0.dp,
            listItemTop = 0.dp,
            listItemBottom = 2.dp,
            listIndent = 8.dp,
        ),
        components = wledMarkdownComponents(),
        typography = wledMarkdownTypography(),
    )
}

@Composable
private fun wledMarkdownComponents() = markdownComponents(
    heading2 = {
        val topPadding = if (it.node.startOffset == 0) 2.dp else 10.dp
        Box(modifier = Modifier.padding(top = topPadding, bottom = 2.dp)) {
            MarkdownHeader(
                content = it.content,
                node = it.node,
                style = it.typography.h2,
            )
        }
    },
    heading3 = {
        val topPadding = if (it.node.startOffset == 0) 2.dp else 10.dp
        Box(modifier = Modifier.padding(top = topPadding, bottom = 2.dp)) {
            MarkdownHeader(
                content = it.content,
                node = it.node,
                style = it.typography.h3,
            )
        }
    },
    paragraph = {
        val isListItem = it.node.parent?.type == MarkdownElementTypes.LIST_ITEM
        MarkdownParagraph(
            content = it.content,
            node = it.node,
            style = it.typography.paragraph,
            modifier = if (isListItem) Modifier else Modifier.padding(bottom = 2.dp),
        )
    },
    unorderedList = {
        MarkdownBulletList(
            content = it.content,
            node = it.node,
            style = it.typography.bullet,
            depth = it.listDepth + 1,
            markerModifier = { Modifier.padding(end = 6.dp) },
        )
    },
    orderedList = {
        MarkdownOrderedList(
            content = it.content,
            node = it.node,
            style = it.typography.ordered,
            depth = it.listDepth + 1,
            markerModifier = { Modifier.padding(end = 6.dp) },
        )
    },
)

@Composable
private fun wledMarkdownTypography() = markdownTypography(
    h1 = MaterialTheme.typography.headlineSmall.copy(
        fontFamily = PixelifySansFontFamily,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = (-0.2).sp,
    ),
    h2 = MaterialTheme.typography.titleMedium.copy(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.secondary,
    ),
    h3 = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.tertiary,
    ),
    text = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
    paragraph = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
    bullet = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
    ordered = MaterialTheme.typography.bodyMedium.copy(lineHeight = 18.sp),
)
