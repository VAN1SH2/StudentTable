package ru.sfu.student.updates

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import ru.sfu.student.core.AppUpdate

class GitHubUpdateSourceTest {
    private val release = AppUpdate(1, "ru.sfu.student", 15, "0.7.1", 26,
        "https://github.com/VAN1SH2/StudentTable/releases/download/build-9-1/StudentTable-0.7.1.apk", "a".repeat(64), 12345)
    @Test fun checksThePublicReleaseManifestWithoutGitHubCredentials() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals(AppUpdate.MANIFEST_URL, chain.request().url.toString())
            assertNull(chain.request().header("Authorization"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(release.toJson().toResponseBody()).build()
        }.build()
        assertEquals(release, GitHubUpdateSource(client).fetch())
    }

    @Test fun rejectsNetworkErrorsAndOversizedResponses() = runBlocking {
        listOf(404 to "Not found", 200 to " ".repeat(16_385), 200 to "<html>GitHub error</html>").forEach { (status, body) ->
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(status).message("Response")
                    .body(body.toResponseBody()).build()
            }.build()
            assertTrue(runCatching { GitHubUpdateSource(client).fetch() }.isFailure)
        }
    }
}
