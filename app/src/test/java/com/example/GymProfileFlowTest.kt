package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.model.GymProfile
import com.example.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GymProfileFlowTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        viewModel = MainViewModel(app)
        db = AppDatabase.getDatabase(app)
    }

    @Test
    fun testSelectGymProfile() = runBlocking {
        assertNull(viewModel.activeGymProfile.value)

        val profile = GymProfile(
            id = "test-gym-1",
            name = "SmartFit Mariana",
            latitude = -23.5,
            longitude = -46.6,
            availableExercisesIds = listOf("ex1", "ex2"),
            unavailableExercisesIds = emptyList()
        )

        viewModel.selectGymProfile(profile)
        assertEquals("SmartFit Mariana", viewModel.activeGymProfile.value?.name)
    }

    @Test
    fun testSaveGymProfileFromSession() = runBlocking {
        val exerciseIds = listOf("ex_bench_press", "ex_leg_press")
        
        viewModel.saveGymProfileFromSession(
            name = "BlueFit Centro",
            lat = -23.123,
            lng = -46.456,
            usedExercisesIds = exerciseIds
        )

        // Poll with short delays to let Room background thread insert and Main thread activeGymProfile update complete
        var attempts = 0
        while (viewModel.activeGymProfile.value == null && attempts < 40) {
            delay(50)
            ShadowLooper.idleMainLooper()
            attempts++
        }

        val createdProfile = viewModel.activeGymProfile.value
        
        assertNotNull(createdProfile)
        assertEquals("BlueFit Centro", createdProfile?.name)
        assertEquals(-23.123, createdProfile?.latitude ?: 0.0, 0.001)
        assertEquals(-46.456, createdProfile?.longitude ?: 0.0, 0.001)
        assertEquals(exerciseIds, createdProfile?.availableExercisesIds)
    }
}
