package dev.anthracite.appt.testing

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.isRoot
import dev.anthracite.appt.tokens.SizeTokens
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** An IPv4 dotted quad, a UUID, or a MAC address: none may reach the screen. */
val TECHNICAL_ID: Regex =
    Regex(
        "\\b\\d{1,3}(\\.\\d{1,3}){3}\\b|" +
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b|" +
            "\\b[0-9a-fA-F]{2}(:[0-9a-fA-F]{2}){5}\\b"
    )

fun ComposeContentTestRule.clickableNodes(): List<SemanticsNode> =
    onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()

/**
 * presentation.md's accessibility contract: "Targets are at least 48dp", asserted over each
 * clickable node's touch bounds against the token that owns the floor.
 */
fun ComposeContentTestRule.assertEveryClickableMeetsTheTouchTargetFloor() {
    val nodes = clickableNodes()
    assertTrue("expected at least one interactive control", nodes.isNotEmpty())
    val floorPx = with(density) { SizeTokens.minimumTouchTarget.toPx() }
    nodes.forEach { node ->
        val bounds = node.touchBoundsInRoot
        assertTrue("touch width ${bounds.width}px is below ${floorPx}px", bounds.width + HALF_PIXEL >= floorPx)
        assertTrue("touch height ${bounds.height}px is below ${floorPx}px", bounds.height + HALF_PIXEL >= floorPx)
    }
}

/** Every text and content description in the unmerged semantics tree. */
fun ComposeContentTestRule.allExposedText(): List<String> {
    val result = mutableListOf<String>()
    fun visit(node: SemanticsNode) {
        node.config.getOrNull(SemanticsProperties.Text)?.forEach { result += it.text }
        node.config.getOrNull(SemanticsProperties.ContentDescription)?.let { result += it }
        node.config.getOrNull(SemanticsProperties.StateDescription)?.let { result += it }
        node.config.getOrNull(SemanticsActions.OnClick)?.label?.let { result += it }
        node.children.forEach(::visit)
    }
    onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes().forEach(::visit)
    return result
}

fun ComposeContentTestRule.assertNoTechnicalIdentifierIsExposed(vararg alsoForbidden: String) {
    val exposed = allExposedText()
    assertTrue("expected some text on screen", exposed.isNotEmpty())
    exposed.forEach { text ->
        assertFalse("technical identifier on screen: \"$text\"", TECHNICAL_ID.containsMatchIn(text))
        alsoForbidden.forEach { value ->
            assertFalse("\"$value\" reached the screen in \"$text\"", text.contains(value))
        }
    }
}

private const val HALF_PIXEL = 0.5f
