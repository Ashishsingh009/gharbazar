package com.ashish.gharbazaar.data.remote

import com.ashish.gharbazaar.domain.RemoteAck
import com.ashish.gharbazaar.domain.RemoteStatus
import com.ashish.gharbazaar.domain.SyncOperation
import com.ashish.gharbazaar.domain.SyncRemote
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Live adapter used only when a base URL is configured and the agent feature flag is on.
 * Contract tests and local runs use [FixtureRemote] instead.
 */
class AgentClient(private val baseUrl: String) : SyncRemote {
    override fun push(operation: SyncOperation): RemoteAck {
        val connection = URL("$baseUrl/v1/commands").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            val body = JSONObject()
                .put("installationId", "from-sync")
                .put("idempotencyKey", operation.idempotencyKey)
                .put("command", operation.entityType)
                .put("payload", JSONObject(operation.payload.ifBlank { "{}" }))
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode in 200..299) RemoteAck(RemoteStatus.ACK, operation.entityVersion) else RemoteAck(RemoteStatus.FAIL)
        } catch (_: Exception) {
            RemoteAck(RemoteStatus.FAIL)
        } finally {
            connection.disconnect()
        }
    }
}

class FixtureRemote : SyncRemote {
    override fun push(operation: SyncOperation): RemoteAck = RemoteAck(RemoteStatus.ACK, operation.entityVersion)
}
