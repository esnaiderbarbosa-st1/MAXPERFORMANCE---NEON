package com.example.data

import com.example.data.model.toHrTimelineList
import org.junit.Assert.assertEquals
import org.junit.Test

class HrTimelineParserTest {

    @Test
    fun testToHrTimelineList_parsesSecondColonBpmFormat() {
        val raw = "0:155,10:158,20:160"
        assertEquals(listOf(155, 158, 160), raw.toHrTimelineList())
    }

    @Test
    fun testToHrTimelineList_parsesPureBpmFormat() {
        val raw = "155,158"
        assertEquals(listOf(155, 158), raw.toHrTimelineList())
    }

    @Test
    fun testToHrTimelineList_parsesMixedFormatsAndFiltersInvalidRange() {
        val raw = "0:155, 10:25, 20:250, 162, invalid, 30:170, :145, 40:"
        assertEquals(listOf(155, 162, 170, 145), raw.toHrTimelineList())
    }

    @Test
    fun testToHrTimelineList_handlesNullOrBlankSafely() {
        val nullStr: String? = null
        assertEquals(emptyList<Int>(), nullStr.toHrTimelineList())
        assertEquals(emptyList<Int>(), "   ".toHrTimelineList())
    }
}
