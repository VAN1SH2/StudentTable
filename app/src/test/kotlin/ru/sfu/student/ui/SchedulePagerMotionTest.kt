package ru.sfu.student.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController
import ru.sfu.student.ui.components.SchedulePagerMotion
import ru.sfu.student.ui.components.rememberSchedulePagerMotion
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SchedulePagerMotionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val selected = mutableIntStateOf(0)
    private lateinit var motion: SchedulePagerMotion
    private lateinit var activity: ActivityController<ComponentActivity>

    @Before fun createHost() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    }

    @After fun destroyHost() {
        activity.pause().stop().destroy()
    }

    private fun showPager() {
        compose.runOnUiThread { activity.get().setContent {
            motion = rememberSchedulePagerMotion(selected.intValue, LocalDate.of(2026, 10, 5)) {
                selected.intValue = it
            }
            HorizontalPager(motion.pager, modifier = Modifier.fillMaxSize().testTag("pager"),
                beyondViewportPageCount = 1, overscrollEffect = null) { page ->
                BasicText("Day $page")
            }
        } }
        compose.waitForIdle()
    }

    @Test fun rapidDateChangesDuringSlideAndFadeFinishAtTheLastSelectedDay() {
        showPager()
        compose.mainClock.autoAdvance = false
        listOf(1, 6, 10, 2).forEach { page ->
            compose.runOnIdle { selected.intValue = page }
            compose.mainClock.advanceTimeBy(32)
        }
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(2, selected.intValue)
            assertEquals(2, motion.pager.currentPage)
            assertEquals(2, motion.pager.settledPage)
            assertEquals(1f, motion.opacity, 0f)
        }
    }

    @Test fun swipingAfterADistantDateSelectionUpdatesTheSelectedDay() {
        showPager()
        compose.runOnIdle { selected.intValue = 6 }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(6, motion.pager.settledPage) }
        compose.onNodeWithTag("pager").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(7, selected.intValue)
            assertEquals(7, motion.pager.settledPage)
            assertEquals(1f, motion.opacity, 0f)
        }
    }
}
