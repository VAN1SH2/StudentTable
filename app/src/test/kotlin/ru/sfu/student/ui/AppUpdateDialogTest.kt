package ru.sfu.student.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import ru.sfu.student.core.AppUpdate
import ru.sfu.student.ui.components.AppUpdateDialog
import ru.sfu.student.updates.UpdateDownloadState

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class AppUpdateDialogTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var activity: ActivityController<ComponentActivity>
    private val release = AppUpdate(1, "ru.sfu.student", 15, "0.7.1", 26,
        "https://github.com/VAN1SH2/StudentTable/releases/download/build-9-1/StudentTable-0.7.1.apk", "a".repeat(64), 12345)
    private val download = mutableStateOf<UpdateDownloadState>(UpdateDownloadState.Idle)
    private var downloads = 0
    private var dismissals = 0
    private var prompts = 0

    @Before fun createHost() { activity = Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    @After fun destroyHost() { activity.pause().stop().destroy() }
    private fun show() {
        compose.runOnUiThread { activity.get().setContent {
            AppUpdateDialog(release, download.value, { prompts++ }, {
                downloads++; download.value = UpdateDownloadState.Downloading(0, release.sizeBytes)
            }, { dismissals++ }, { error(it) })
        } }
        compose.waitForIdle()
    }

    @Test fun decliningTheOfferDoesNotDownloadAnApk() {
        show()
        compose.onNodeWithText("Доступно обновление 0.7.1").assertExists()
        compose.onNodeWithText("Позже").performClick()
        compose.runOnIdle { assertEquals(1, prompts); assertEquals(1, dismissals); assertEquals(0, downloads) }
    }

    @Test fun downloadRequiresATapAndCanBeCancelled() {
        show()
        compose.runOnIdle { assertEquals(0, downloads) }
        compose.onNodeWithText("Скачать и установить").performClick()
        compose.onNodeWithText("Скачивание · 0 %").assertExists()
        compose.onNodeWithText("Отменить").performClick()
        compose.runOnIdle { assertEquals(1, downloads); assertEquals(1, dismissals) }
    }
}
