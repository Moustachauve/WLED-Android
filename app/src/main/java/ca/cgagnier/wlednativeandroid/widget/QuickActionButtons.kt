package ca.cgagnier.wlednativeandroid.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

private val BUTTON_HEIGHT = 28.dp
private val CORNER_RADIUS = 12.dp
private val SEPARATOR_HEIGHT = 6.dp

/**
 * A row of quick action buttons with a rounded container and dividers between buttons.
 *
 * @param items List of [QuickActionItem] to display.
 * @param modifier Optional [GlanceModifier] to apply to the container.
 */
@Composable
fun QuickActionButtons(items: List<QuickActionItem>, modifier: GlanceModifier = GlanceModifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(BUTTON_HEIGHT)
            .background(GlanceTheme.colors.surfaceVariant)
            .cornerRadius(CORNER_RADIUS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            if (index > 0) {
                QuickActionDivider()
            }
            QuickActionButton(item)
        }
    }
}

/**
 * Represents a single quick action button item.
 *
 * @param label The text label displayed on the button.
 * @param onClick The action to perform when the button is clicked.
 */
data class QuickActionItem(val label: String, val onClick: Action)

@Composable
private fun QuickActionDivider() {
    Box(
        modifier = GlanceModifier
            .width(1.dp)
            .padding(SEPARATOR_HEIGHT)
            .background(GlanceTheme.colors.outline),
    ) {}
}

@Composable
private fun RowScope.QuickActionButton(item: QuickActionItem) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .clickable(item.onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.label,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
    }
}

@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 100)
@Composable
private fun QuickActionButtonsPreview() {
    GlanceTheme {
        Column(modifier = GlanceModifier.padding(16.dp)) {
            QuickActionButtons(
                items = listOf(
                    QuickActionItem(label = "A", onClick = actionDoNothing()),
                    QuickActionItem(label = "B", onClick = actionDoNothing()),
                    QuickActionItem(label = "C", onClick = actionDoNothing()),
                ),
            )
        }
    }
}

private fun actionDoNothing(): Action = object : Action {}
