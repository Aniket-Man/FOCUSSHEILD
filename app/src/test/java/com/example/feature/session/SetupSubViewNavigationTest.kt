package com.example.feature.session

import com.example.feature.session.ui.SetupSubView
import com.example.feature.session.ui.backTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the Focus Session setup sheet's navigation contract (issue #52).
 *
 * The bug was a dismissal lifecycle problem: the Website Blocker and the YouTube channel manager
 * were nested `ModalBottomSheet`s, and dragging the inner one down handed the rest of the
 * interaction to the sheet underneath while the outer sheet's `onDismissRequest` quietly jumped
 * the user to MAIN and left the sheet hidden-but-composed. Every page now lives in one sheet, and
 * system back, the close buttons and drag-to-dismiss all resolve through [backTarget] — so these
 * tests pin the property the fix depends on: **a back/dismiss step always moves exactly one page
 * up, and never lands anywhere that starts a session.**
 *
 * Limitation (documented in issue #52): the drag gesture itself cannot be reproduced here. A
 * Compose UI test cannot drive a pointer across two `ModalBottomSheet` *windows* under Robolectric,
 * and `FocusSessionSetupSheet` needs a live `SessionViewModel` with repositories. The state
 * machine the gesture drives is what is pinned instead.
 */
class SetupSubViewNavigationTest {

    @Test
    fun `main is the only page that closes the sheet`() {
        assertNull("MAIN must dismiss the sheet", SetupSubView.MAIN.backTarget())

        SetupSubView.entries
            .filter { it != SetupSubView.MAIN }
            .forEach { page ->
                assertNotEquals(
                    "$page must not dismiss the sheet, it must navigate",
                    null,
                    page.backTarget()
                )
            }
    }

    @Test
    fun `the nested pages step back to select apps, not to main`() {
        assertEquals(SetupSubView.SELECT_APPS, SetupSubView.WEBSITE_BLOCKER.backTarget())
        assertEquals(SetupSubView.SELECT_APPS, SetupSubView.YOUTUBE_CHANNELS.backTarget())
    }

    @Test
    fun `the first-level pages step back to main`() {
        assertEquals(SetupSubView.MAIN, SetupSubView.FOCUS_TIME.backTarget())
        assertEquals(SetupSubView.MAIN, SetupSubView.BREAK.backTarget())
        assertEquals(SetupSubView.MAIN, SetupSubView.SELECT_APPS.backTarget())
    }

    @Test
    fun `the reported flow walks back one page at a time`() {
        // MAIN -> SELECT_APPS -> WEBSITE_BLOCKER, then back out again.
        var page = SetupSubView.WEBSITE_BLOCKER

        page = page.backTarget() ?: error("Website Blocker must not close the sheet")
        assertEquals("Dismissing the website blocker must reveal Select Apps", SetupSubView.SELECT_APPS, page)

        page = page.backTarget() ?: error("Select Apps must not close the sheet")
        assertEquals(SetupSubView.MAIN, page)

        assertNull("Only MAIN closes the sheet", page.backTarget())
    }

    @Test
    fun `no page ever steps back to itself and every chain reaches main`() {
        SetupSubView.entries.forEach { start ->
            var page = start
            var steps = 0
            while (true) {
                val next = page.backTarget() ?: break
                assertNotEquals("$page steps back to itself", page, next)
                page = next
                steps++
                if (steps > SetupSubView.entries.size) {
                    error("Back navigation from $start does not terminate")
                }
            }
            assertEquals("Back navigation from $start must end at MAIN", SetupSubView.MAIN, page)
        }
    }

    @Test
    fun `existing pages keep their saved ordinals`() {
        // SetupSubViewSaver persists the ordinal, so a sheet left open across a rotation or
        // process death restores by index. New pages must only ever be appended (issue #28).
        assertEquals(0, SetupSubView.MAIN.ordinal)
        assertEquals(1, SetupSubView.FOCUS_TIME.ordinal)
        assertEquals(2, SetupSubView.BREAK.ordinal)
        assertEquals(3, SetupSubView.SELECT_APPS.ordinal)
    }
}
