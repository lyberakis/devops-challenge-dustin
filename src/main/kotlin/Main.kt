import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import org.slf4j.event.Level

fun main() {
    val logLevel = (System.getenv("LOG_LEVEL") ?: "INFO").uppercase()
    require(logLevel in setOf("DEBUG", "INFO")) {
        "LOG_LEVEL must be DEBUG, or INFO"
    }
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel)

    val environment = System.getenv("ENVIRONMENT") ?: "development"
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val port = (System.getenv("PORT") ?: "8080").toInt()
    val logger = LoggerFactory.getLogger("app")
    logger.info("Starting server on {}:{} in environment {}", host, port, environment)
    logger.debug("Logging configured at {} level", logLevel)

    embeddedServer(Netty, host = host, port = port) {
        module(environment)
    }.start(wait = true)
}

fun Application.module(environment: String) {
    val logger = LoggerFactory.getLogger("app")
    install(ContentNegotiation) { json() }
    install(CallLogging) { level = Level.INFO }
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, mapOf("error" to "Not found"))
        }
        status(HttpStatusCode.MethodNotAllowed) { call, status ->
            call.response.headers.append(HttpHeaders.Allow, "GET")
            call.respond(status, mapOf("error" to "Method not allowed"))
        }
    }
    routing {
        get("/health") {
            logger.debug("Returning healthy status")
            call.respond(mapOf("status" to "healthy"))
        }
        get("/") {
            logger.debug("Returning greeting for environment {}", environment)
            call.respond(mapOf("message" to "Hello, World!", "environment" to environment))
        }
    }
}
