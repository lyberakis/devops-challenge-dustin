import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun happyFlow() = testApplication {
        application { module("test") }

        val health = client.get("/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertEquals(ContentType.Application.Json, health.contentType()?.withoutParameters())
        assertEquals(Json.parseToJsonElement("""{"status":"healthy"}"""), Json.parseToJsonElement(health.bodyAsText()))

        val greeting = client.get("/")
        assertEquals(HttpStatusCode.OK, greeting.status)
        assertEquals(ContentType.Application.Json, greeting.contentType()?.withoutParameters())
        assertEquals(
            "test",
            Json.parseToJsonElement(greeting.bodyAsText()).jsonObject["environment"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun metricsExposeJvmAndHttpMetrics() = testApplication {
        application { module("test") }

        client.get("/health")
        val metrics = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, metrics.status)
        val body = metrics.bodyAsText()
        assertTrue("jvm_memory_used_bytes" in body, "JVM metrics missing")
        assertTrue("ktor_http_server_requests" in body, "HTTP request metrics missing")
    }
}
