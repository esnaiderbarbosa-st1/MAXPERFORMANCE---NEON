package com.example.data.model

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RecordedKmSplitsTest {

    @Test
    fun split1002mWithRealDuration436AndPace435_preservesPace435AndDuration436() {
        val json = listOf(
            RecordedKmSplit(
                splitNumber = 1,
                splitLabel = "1,0 km",
                distanceMeters = 1002,
                durationSec = 436,
                paceSecPerKm = 435,
                avgHr = 154,
                phaseTag = "RUN"
            )
        ).toJsonString()

        val parsed = json.toRecordedKmSplits()

        assertEquals(1, parsed.size)
        val split = parsed.first()
        assertEquals(1002, split.distanceMeters)
        assertEquals(436, split.durationSec)
        assertEquals(435, split.paceSecPerKm)
    }

    @Test
    fun legacyRecord1002mWithDuration435AndPace435_keepsPace435WithoutDoubleDivision() {
        val legacyJson = """[{"splitNumber":1,"splitLabel":"1,0 km","distanceMeters":1002,"durationSec":435,"paceSecPerKm":435}]"""

        val parsed = legacyJson.toRecordedKmSplits()

        assertEquals(1, parsed.size)
        val split = parsed.first()
        assertEquals(1002, split.distanceMeters)
        assertEquals(435, split.durationSec)
        assertEquals(435, split.paceSecPerKm)
    }

    @Test
    fun split1000m_remainsUnchanged() {
        val json = listOf(
            RecordedKmSplit(
                splitNumber = 1,
                splitLabel = "KM 1",
                distanceMeters = 1000,
                durationSec = 381,
                paceSecPerKm = 381,
                avgHr = 158,
                phaseTag = "RUN"
            )
        ).toJsonString()

        val parsed = json.toRecordedKmSplits()

        assertEquals(1, parsed.size)
        val split = parsed.first()
        assertEquals(1000, split.distanceMeters)
        assertEquals(381, split.durationSec)
        assertEquals(381, split.paceSecPerKm)
    }

    @Test
    fun splitWithoutPersistedPace_computesPaceFromDurationAndDistanceOrZero() {
        val jsonWithoutPace = """[
            {"splitNumber":1,"splitLabel":"0,94 km","distanceMeters":940,"durationSec":400,"paceSecPerKm":0},
            {"splitNumber":2,"splitLabel":"2,0 km","distanceMeters":1000,"durationSec":0,"paceSecPerKm":0}
        ]"""

        val parsed = jsonWithoutPace.toRecordedKmSplits()

        assertEquals(2, parsed.size)
        assertEquals(426, parsed[0].paceSecPerKm)
        assertEquals(400, parsed[0].durationSec)
        assertEquals(0, parsed[1].paceSecPerKm)
        assertEquals(0, parsed[1].durationSec)
    }
}
