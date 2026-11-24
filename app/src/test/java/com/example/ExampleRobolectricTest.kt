package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.viewmodel.MainViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertNotNull(appName)
  }

  @Test
  fun `instantiate viewmodel and verify startup`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(app)
    assertNotNull(viewModel)
  }

  @Test
  fun `launch MainActivity with running app mode and verify`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("max_performance_prefs", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("first_launch", false)
        .putString("app_mode", "RUNNING")
        .apply()
    context.getSharedPreferences("max_user_prefs", Context.MODE_PRIVATE)
        .edit()
        .putString("logged_in_user_id", "user_demo_2")
        .apply()

    val controller = org.robolectric.Robolectric.buildActivity(MainActivity::class.java)
    controller.setup()
    assertNotNull(controller.get())
  }
}
