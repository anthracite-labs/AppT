package dev.anthracite.appt.discovery

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.anthracite.appt.samsung.TvFailure
import dev.anthracite.appt.samsung.TvId
import dev.anthracite.appt.testing.FakeSamsungTvs
import dev.anthracite.appt.testing.assertEveryClickableMeetsTheTouchTargetFloor
import dev.anthracite.appt.testing.assertEveryControlIsDescribedForTalkBack
import dev.anthracite.appt.testing.assertNoTechnicalIdentifierIsExposed
import dev.anthracite.appt.testing.clickableNodes
import dev.anthracite.appt.tokens.AppTTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Discovery's product shape and accessibility contracts (presentation.md#discovery). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DiscoveryScreenTest {
    @get:Rule val composeRule = createComposeRule()

    private val picks = mutableListOf<TvId>()
    private var rescans = 0

    private val ready =
        TvCardUi(TvId(FakeSamsungTvs.LIVING_ROOM_ID), "Living Room TV", CardState.Ready, false)
    private val needsPairing = TvCardUi(TvId("local-7c1e"), "", CardState.NeedsPairing, true)
    private val unsupported =
        TvCardUi(TvId(FakeSamsungTvs.OLDER_ID), "Older TV", CardState.Unsupported, false)

    private fun setDiscovery(state: DiscoveryUiState) {
        composeRule.setContent {
            AppTTheme {
                DiscoveryScreen(state = state, onRescan = { rescans++ }, onPick = { picks += it })
            }
        }
    }

    private fun scanning(vararg cards: TvCardUi) =
        DiscoveryUiState(ScanPhase.Scanning, cards.toList(), false)

    private fun SemanticsNode.texts(): List<String> =
        config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }

    @Test
    fun cardsShowFriendlyNamesAndStatesButNoTechnicalIds() {
        setDiscovery(scanning(ready, needsPairing, unsupported))
        composeRule.onNodeWithText("Living Room TV").assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Ready to use")).assertIsDisplayed()
        composeRule.onNodeWithText("Samsung TV").assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Needs setting up")).assertIsDisplayed()
        composeRule.onNodeWithText("Saved on this phone").assertIsDisplayed()
        composeRule.assertNoTechnicalIdentifierIsExposed("local-7c1e", "local-")
    }

    @Test
    fun aNameCarryingAnAddressFallsBackToTheGenericLabel() {
        val found = FakeSamsungTvs.found(name = "Den 192.0.2.20:8001")
        setDiscovery(DiscoveryUiState.Initial.reduce(found))
        composeRule.onNodeWithText("Samsung TV").assertIsDisplayed()
        composeRule.assertNoTechnicalIdentifierIsExposed("8001", FakeSamsungTvs.LIVING_ROOM_ID)
    }

    @Test
    fun scanningIsAnnouncedPolitely() {
        setDiscovery(scanning())
        val status = composeRule.onNodeWithTag(DiscoveryTestTags.STATUS).fetchSemanticsNode()
        assertEquals(LiveRegionMode.Polite, status.config.getOrNull(SemanticsProperties.LiveRegion))
        composeRule
            .onNodeWithText("Looking for televisions on your home network…")
            .assertIsDisplayed()
    }

    @Test
    fun unsupportedCardHasNoControlAffordance() {
        setDiscovery(scanning(unsupported))
        composeRule
            .onNode(hasStateDescription("AppT can't control this television yet"))
            .assertIsDisplayed()
        val card = composeRule.onNodeWithTag(DiscoveryTestTags.CARD).fetchSemanticsNode()
        assertNull(
            "an Unsupported card must not be clickable",
            card.config.getOrNull(SemanticsActions.OnClick),
        )
        assertNull(card.config.getOrNull(SemanticsProperties.Role))
        assertEquals(
            "no control anywhere on screen while scanning",
            0,
            composeRule.clickableNodes().size,
        )

        composeRule.onNodeWithTag(DiscoveryTestTags.CARD).performClick()
        assertEquals(emptyList<TvId>(), picks)
    }

    /** Each card is one TalkBack stop with a label, a state, and (unless Unsupported) an action. */
    @Test
    @Config(qualifiers = "w360dp-h1200dp")
    fun everyCardExposesItsNameStatusAndOnlyChoosableCardsAct() {
        setDiscovery(scanning(ready, needsPairing, unsupported))
        val cards = composeRule.onAllNodesWithTag(DiscoveryTestTags.CARD).fetchSemanticsNodes()

        assertEquals(
            listOf(
                listOf("Living Room TV"),
                listOf("Samsung TV", "Saved on this phone"),
                listOf("Older TV"),
            ),
            cards.map { it.texts() },
        )
        assertEquals(
            listOf("Ready to use", "Needs setting up", "AppT can't control this television yet"),
            cards.map { it.config.getOrNull(SemanticsProperties.StateDescription) },
        )
        cards.take(2).forEach { card ->
            assertEquals(Role.Button, card.config.getOrNull(SemanticsProperties.Role))
            assertEquals(
                "Choose this television",
                card.config.getOrNull(SemanticsActions.OnClick)?.label,
            )
        }
        assertNull(cards[2].config.getOrNull(SemanticsProperties.Role))
        assertNull(cards[2].config.getOrNull(SemanticsActions.OnClick))
    }

    @Test
    fun choosingACardReportsItsOpaqueId() {
        setDiscovery(scanning(ready, unsupported))
        composeRule.onNodeWithText("Living Room TV").performClick()
        assertEquals(listOf(ready.tvId), picks)
    }

    /**
     * The grid is lazy: only items inside the viewport are composed, so on Robolectric's default
     * small screen the trailing Scan again item would simply not exist in the semantics tree. The
     * check runs on a tall phone (still compact width, so the same one-column layout) so every
     * control is composed and fully on screen, and scrolls to Scan again so it never depends on the
     * viewport height.
     */
    @Test
    @Config(qualifiers = "w360dp-h1200dp")
    fun everyControlMeetsTouchTarget() {
        setDiscovery(
            DiscoveryUiState(ScanPhase.Finished, listOf(ready, needsPairing, unsupported), false)
        )
        composeRule
            .onNodeWithTag(DiscoveryTestTags.LIST)
            .performScrollToNode(hasTestTag(DiscoveryTestTags.RESCAN))

        // Two choosable cards and Scan again; the Unsupported card is not a control.
        assertEquals(
            2,
            composeRule
                .onAllNodes(hasTestTag(DiscoveryTestTags.CARD) and hasClickAction())
                .fetchSemanticsNodes()
                .size,
        )
        composeRule.onNodeWithTag(DiscoveryTestTags.RESCAN).assertHasClickAction()
        assertEquals(3, composeRule.clickableNodes().size)
        composeRule.assertEveryClickableMeetsTheTouchTargetFloor()
        composeRule.assertEveryControlIsDescribedForTalkBack()
    }

    @Test
    fun statusIsTextAndIconNotColourAlone() {
        setDiscovery(scanning(ready, needsPairing, unsupported))
        val icons =
            composeRule
                .onAllNodes(hasTestTag(DiscoveryTestTags.CARD_STATUS_ICON), useUnmergedTree = true)
                .fetchSemanticsNodes()
        val labels =
            composeRule
                .onAllNodes(hasTestTag(DiscoveryTestTags.CARD_STATUS_LABEL), useUnmergedTree = true)
                .fetchSemanticsNodes()
        assertEquals(3, icons.size)
        assertEquals(3, labels.size)
        icons.forEach { icon ->
            assertNull(
                "the icon is decorative beside its label",
                icon.config.getOrNull(SemanticsProperties.Text),
            )
        }
    }

    @Test
    fun emptyStateSaysNoTelevisionsFoundAndOffersScanAgain() {
        setDiscovery(DiscoveryUiState(ScanPhase.Finished, emptyList(), showEmptyState = true))
        composeRule.onNodeWithTag(DiscoveryTestTags.EMPTY_STATE).assertIsDisplayed()
        composeRule.onNodeWithText("No televisions found").assertIsDisplayed()
        composeRule.onNodeWithText("Scan again").assertHasClickAction().performClick()
        assertEquals(1, rescans)
        composeRule.assertEveryClickableMeetsTheTouchTargetFloor()
        composeRule.assertNoTechnicalIdentifierIsExposed()
    }

    @Test
    fun failureExplainsInOrdinaryLanguageAndOffersScanAgain() {
        setDiscovery(DiscoveryUiState(ScanPhase.Failed(TvFailure.Unreachable), emptyList(), false))
        composeRule.onNodeWithTag(DiscoveryTestTags.FAILURE).assertIsDisplayed()
        composeRule.onNodeWithTag(DiscoveryTestTags.RESCAN).performClick()
        assertEquals(1, rescans)
        composeRule.assertEveryClickableMeetsTheTouchTargetFloor()
    }

    @Test
    fun deniedFailureIsExplainedInOrdinaryLanguage() {
        setDiscovery(
            DiscoveryUiState(ScanPhase.Failed(TvFailure.LocalNetworkDenied), emptyList(), false)
        )
        composeRule
            .onNodeWithText("AppT can't reach your home network right now.")
            .assertIsDisplayed()
    }

    @Test
    fun otherFailuresUseTheGeneralMessage() {
        setDiscovery(DiscoveryUiState(ScanPhase.Failed(TvFailure.TimedOut), emptyList(), false))
        composeRule
            .onNodeWithText(
                "Something went wrong while looking for televisions. Please scan again."
            )
            .assertIsDisplayed()
    }

    @Test
    fun finishedWithCardsOffersScanAgainAndNoEmptyState() {
        setDiscovery(DiscoveryUiState(ScanPhase.Finished, listOf(ready), false))
        composeRule.onNodeWithText("Search finished").assertIsDisplayed()
        composeRule.onNodeWithTag(DiscoveryTestTags.RESCAN).assertHasClickAction()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag(DiscoveryTestTags.EMPTY_STATE).fetchSemanticsNodes().size,
        )
    }
}
