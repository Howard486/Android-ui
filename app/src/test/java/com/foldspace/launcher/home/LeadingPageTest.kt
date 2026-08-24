package com.foldspace.launcher.home

import com.foldspace.launcher.spaces.SpaceId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The leading page used to differ per Space, so the news existed only in 通用
 * and the summary only in 工作. Pinning it here is what stops that coming back
 * one convenient special case at a time.
 */
class LeadingPageTest {

    @Test
    fun `the paged spaces get the same leading page`() {
        assertEquals(PageKind.Hub, HomeLayout.leadingFor(SpaceId.General))
        assertEquals(PageKind.Hub, HomeLayout.leadingFor(SpaceId.Work))
    }

    @Test
    fun `簡易 has no leading page, because it has no pages`() {
        assertNull(HomeLayout.leadingFor(SpaceId.Simple))
    }

    @Test
    fun `an empty layout carries the hub`() {
        assertEquals(PageKind.Hub, HomeLayout.empty(SpaceId.General, Posture.Folded).leading)
        assertEquals(PageKind.Hub, HomeLayout.empty(SpaceId.Work, Posture.Unfolded).leading)
    }

    @Test
    fun `a stored key from an older build still parses`() {
        // Feed and Work rows may exist in a database written before the hub.
        assertEquals(PageKind.Feed, PageKind.fromKey("feed"))
        assertEquals(PageKind.Work, PageKind.fromKey("work"))
        assertEquals(PageKind.Hub, PageKind.fromKey("hub"))
        assertEquals(PageKind.Grid, PageKind.fromKey("nonsense"))
    }

    @Test
    fun `a quick launch block has no label of its own`() {
        val item = HomeItem(
            id = 1L,
            type = HomeItemType.QuickLaunch,
            cellX = 0,
            cellY = 0,
            spanX = 2,
            spanY = 2,
            app = null,
            folderTitle = null,
        )
        assertEquals("", item.label)
    }
}
