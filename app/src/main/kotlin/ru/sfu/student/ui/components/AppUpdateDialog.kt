package ru.sfu.student.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import ru.sfu.student.BuildConfig
import ru.sfu.student.core.AppUpdate
import ru.sfu.student.updates.UpdateDownloadState
import java.io.File

private fun openUpdateInstaller(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).also { it.clipData = ClipData.newRawUri("StudentTable", uri) })
}

@Composable fun AppUpdateDialog(release: AppUpdate, download: UpdateDownloadState, onPromptShown: (Int) -> Unit,
    onDownload: () -> Unit, onDismiss: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val latestDownload by rememberUpdatedState(download)
    fun installReadyApk() {
        val ready = latestDownload as? UpdateDownloadState.Ready ?: return
        try { openUpdateInstaller(context, ready.file); onDismiss() }
        catch (_: Exception) { onError("Не удалось открыть установщик Android. Нажмите «Установить» ещё раз.") }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) installReadyApk()
        else onError("Чтобы установить обновление, разрешите установку из StudentTable и нажмите «Установить».")
    }
    fun requestInstall() {
        if (context.packageManager.canRequestPackageInstalls()) installReadyApk()
        else try { permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())) }
        catch (_: Exception) { onError("Не удалось открыть разрешение на установку. Проверьте настройки Android.") }
    }
    LaunchedEffect(release.versionCode) { onPromptShown(release.versionCode) }
    LaunchedEffect((download as? UpdateDownloadState.Ready)?.file) {
        if (download is UpdateDownloadState.Ready) requestInstall()
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Доступно обновление ${release.versionName}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Установлена версия ${BuildConfig.VERSION_NAME}. Обновление устанавливается поверх неё.")
            when (download) {
                is UpdateDownloadState.Downloading -> {
                    val progress = (download.bytes.toFloat() / download.total).coerceIn(0f, 1f)
                    Text("Скачивание · ${(progress * 100).toInt()} %")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateDownloadState.Failed -> Text(download.message, color = MaterialTheme.colorScheme.error)
                is UpdateDownloadState.Ready -> Text("APK проверен и готов к установке. Android запросит подтверждение и при необходимости разрешение на установку из StudentTable.")
                UpdateDownloadState.Idle -> Text("Размер: ${String.format(Russian, "%.1f", release.sizeBytes / 1_000_000.0)} МБ. Установку нужно подтвердить в Android.")
            }
        }
    }, confirmButton = {
        when (download) {
            is UpdateDownloadState.Downloading -> TextButton(onClick = onDismiss) { Text("Отменить") }
            is UpdateDownloadState.Ready -> TextButton(onClick = { requestInstall() }) { Text("Установить") }
            else -> TextButton(onClick = onDownload) { Text(if (download is UpdateDownloadState.Failed) "Повторить" else "Скачать и установить") }
        }
    }, dismissButton = {
        if (download !is UpdateDownloadState.Downloading) TextButton(onClick = onDismiss) { Text("Позже") }
    })
}
