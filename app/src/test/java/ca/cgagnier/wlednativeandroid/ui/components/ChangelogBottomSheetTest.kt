package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChangelogBottomSheetTest {

    @Test
    fun `buildSteppedGradientText returns plain string when colors are empty`() {
        val result = buildSteppedGradientText("What's new", emptyList())

        assertEquals("What's new", result.text)
        assertEquals(0, result.spanStyles.size)
    }

    @Test
    fun `buildSteppedGradientText assigns discrete color spans to non-whitespace characters`() {
        val start = Color(1f, 0f, 0f, 1f)
        val middle = Color(0f, 1f, 0f, 1f)
        val end = Color(0f, 0f, 1f, 1f)

        val result = buildSteppedGradientText("A B C", listOf(start, middle, end))

        assertEquals("A B C", result.text)
        assertEquals(3, result.spanStyles.size)
        assertEquals(start, result.spanStyles[0].item.color)
        assertEquals(middle, result.spanStyles[1].item.color)
        assertEquals(end, result.spanStyles[2].item.color)
    }

    @Test
    fun `buildSteppedGradientText handles single visible character`() {
        val primary = Color(0.2f, 0.4f, 0.8f, 1f)
        val secondary = Color(0.8f, 0.4f, 0.2f, 1f)

        val result = buildSteppedGradientText("W", listOf(primary, secondary))

        assertEquals(1, result.spanStyles.size)
        assertEquals(primary, result.spanStyles.first().item.color)
    }
}
