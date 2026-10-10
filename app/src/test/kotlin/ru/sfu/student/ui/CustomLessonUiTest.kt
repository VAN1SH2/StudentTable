package ru.sfu.student.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ru.sfu.student.core.*
import ru.sfu.student.data.*
import ru.sfu.student.ui.components.*
import ru.sfu.student.ui.screens.*
import java.time.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CustomLessonUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val group = SavedGroup(1, "Группа", weekAnchorMonday = "2026-10-05", weekConfirmed = true)
    private val date = LocalDate.of(2028, 4, 3)
    private val now = Instant.parse("2026-10-05T08:00:00Z")
    @Before fun createHost() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    @After fun destroyHost() { activity.pause().stop().destroy() }

    @Test fun addingFromCalendarUsesItsSelectedFarFutureDate() {
        var selected: LocalDate? = null
        val state = StudentState(Settings(activeGroupId = 1), listOf(group), ready = true)
        compose.runOnUiThread { activity.get().setContent {
            CalendarScreen(state, now, date, false, {}, {}, {}, {}, {}, { _, _ -> }, { selected = it }, { _, _, _ -> })
        } }
        compose.onNodeWithText("+ Своя пара").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(date, selected) }
    }
    @Test fun localClassEditorSavesTheCalendarDateAndEnteredSubject() {
        var result: StoredCustomLesson? = null
        val state = StudentState(Settings(activeGroupId = 1), listOf(group), ready = true)
        compose.runOnUiThread { activity.get().setContent {
            CustomLessonEditor(null, group, date, false, state, false, {}, { result = it })
        } }
        compose.onNodeWithText("Предмет *").performTextInput("Консультация")
        compose.onNodeWithText("Сохранить").performClick()
        compose.runOnIdle {
            assertEquals(date.toString(), result!!.data.startDate)
            assertEquals("Консультация", result!!.data.subject)
            assertEquals(0, result!!.data.repeat)
            assertEquals(1L, result!!.groupId)
        }
    }
    @Test fun resetRequiresConfirmationAndCancelDoesNotResetAnything() {
        val visible = mutableStateOf(true)
        var resets = 0
        compose.runOnUiThread { activity.get().setContent {
            if (visible.value) ScheduleResetDialog("Группа", 3, { resets++; visible.value = false }, { visible.value = false })
        } }
        compose.runOnIdle { assertEquals(0, resets) }
        compose.onNodeWithText("Отмена").performClick()
        compose.runOnIdle { assertEquals(0, resets); visible.value = true }
        compose.onNodeWithText("Сбросить").performClick()
        compose.runOnIdle { assertEquals(1, resets) }
    }
    @Test fun editingTheCardDoesNotCreateATaskOrDeleteTheClass() {
        var tasks = 0
        var edits = 0
        var deletes = 0
        val lesson = CustomLesson(1, CustomLessonDetails("Своя", "10:00", "11:30", date.toString())).template()
        compose.runOnUiThread { activity.get().setContent {
            LessonCard(lesson, emptyList(), now, onCreateTask = { tasks++ }, onEdit = { edits++ }, onDelete = { deletes++ })
        } }
        compose.onNodeWithText("⋮").performClick()
        compose.onNodeWithText("Изменить").performClick()
        compose.runOnIdle { assertEquals(1, edits); assertEquals(0, tasks); assertEquals(0, deletes) }
    }
    @Test fun ownOnlineClassShowsALinkWithoutRequiringASpecialBuildingName() {
        val lesson = CustomLesson(1, CustomLessonDetails("Консультация", "10:00", "11:30", date.toString(),
            room = "https://example.org/meeting")).template()
        compose.runOnUiThread { activity.get().setContent { LessonCard(lesson, emptyList(), now) } }
        compose.onNodeWithText("Открыть ссылку ↗").assertExists()
        compose.onNodeWithText("Онлайн").assertExists()
        compose.onNodeWithText("https://example.org/meeting").assertDoesNotExist()
    }
}
