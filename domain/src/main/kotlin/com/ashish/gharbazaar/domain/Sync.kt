package com.ashish.gharbazaar.domain

enum class SyncState { PENDING, ACKNOWLEDGED, FAILED }

data class SyncOperation(
    val idempotencyKey: String,
    val entityType: String,
    val entityVersion: Long,
    val timestampEpochMs: Long,
    val payload: String,
    val state: SyncState = SyncState.PENDING,
    val retryCount: Int = 0,
)

enum class RemoteStatus { ACK, FAIL, STALE }

data class RemoteAck(val status: RemoteStatus, val serverVersion: Long = 0)

fun interface SyncRemote {
    fun push(operation: SyncOperation): RemoteAck
}

data class SyncReport(
    val acknowledged: List<SyncOperation>,
    val pending: List<SyncOperation>,
    val localDraftPreserved: Boolean,
)

class SyncCoordinator(private val remote: SyncRemote) {
    private val queue = linkedMapOf<String, SyncOperation>()
    private val applied = mutableSetOf<String>()

    fun enqueue(operation: SyncOperation) {
        if (operation.idempotencyKey in applied || operation.idempotencyKey in queue) return
        queue[operation.idempotencyKey] = operation.copy(state = SyncState.PENDING)
    }

    fun pending(): List<SyncOperation> = queue.values.toList()

    fun flush(): SyncReport {
        val acknowledged = mutableListOf<SyncOperation>()
        val stillPending = linkedMapOf<String, SyncOperation>()
        var draftPreserved = false
        queue.values.forEach { operation ->
            if (operation.idempotencyKey in applied) return@forEach
            val ack = remote.push(operation)
            when (ack.status) {
                RemoteStatus.ACK -> {
                    applied += operation.idempotencyKey
                    acknowledged += operation.copy(state = SyncState.ACKNOWLEDGED, entityVersion = ack.serverVersion)
                }
                RemoteStatus.STALE, RemoteStatus.FAIL -> {
                    draftPreserved = true
                    stillPending[operation.idempotencyKey] = operation.copy(
                        state = SyncState.FAILED,
                        retryCount = operation.retryCount + 1,
                    )
                }
            }
        }
        queue.clear()
        queue.putAll(stillPending)
        return SyncReport(acknowledged, queue.values.toList(), draftPreserved || queue.isNotEmpty())
    }
}
