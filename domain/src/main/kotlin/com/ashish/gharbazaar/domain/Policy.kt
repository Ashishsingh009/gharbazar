package com.ashish.gharbazaar.domain

import org.json.JSONObject

data class ActionDecision(val allowed: Boolean, val reason: String)

private val sensitiveKeys = setOf(
    "diet", "allergies", "exclusions", "token", "authorization", "email",
    "installationid", "household", "image", "photo",
)

fun evaluateAction(catalogJson: String, name: String, installationId: String?, nativeConfirmation: Boolean, clientSequence: Long, serverSequence: Long): ActionDecision {
    val catalog = JSONObject(catalogJson)
    val actions = catalog.getJSONArray("actions").let { array ->
        (0 until array.length()).map { array.getString(it) }.toSet()
    }
    val commercial = catalog.getJSONArray("commercialActions").let { array ->
        (0 until array.length()).map { array.getString(it) }.toSet()
    }
    if (name in commercial && !nativeConfirmation) {
        return ActionDecision(false, "Commercial actions need native confirmation.")
    }
    if (name !in actions) {
        return ActionDecision(false, "Action is not allowed.")
    }
    if (installationId.isNullOrBlank()) {
        return ActionDecision(false, "An installation identity is required.")
    }
    if (clientSequence < serverSequence) {
        return ActionDecision(false, "The command is stale.")
    }
    return ActionDecision(true, "allowed")
}

fun rejectClientTotal(payload: Map<String, Any?>): Boolean =
    payload.keys.any { it.equals("clientTotal", ignoreCase = true) || it.equals("price", ignoreCase = true) }

fun redactText(value: String): String {
    var redacted = value
    redacted = Regex("Bearer\\s+[A-Za-z0-9._\\-]+", RegexOption.IGNORE_CASE).replace(redacted, "Bearer [redacted]")
    redacted = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+").replace(redacted, "[redacted-email]")
    return redacted
}

fun redactFields(fields: Map<String, String?>): Map<String, String> =
    fields.filterKeys { it.lowercase() !in sensitiveKeys }
        .mapValues { redactText(it.value.orEmpty()) }

class MetricLog {
    data class Event(val name: String, val correlationId: String, val latencyMs: Long, val attributes: Map<String, String>)

    private val recorded = mutableListOf<Event>()

    fun record(name: String, correlationId: String, latencyMs: Long, attributes: Map<String, String>): Boolean {
        if (attributes.keys.any { it.lowercase() in sensitiveKeys }) return false
        if (attributes.values.any { value -> sensitiveKeys.any { key -> value.lowercase().contains(key) && value.contains("@") } }) {
            return false
        }
        recorded += Event(name, correlationId, latencyMs, attributes.mapValues { redactText(it.value) })
        return true
    }

    fun events(): List<Event> = recorded.toList()
}

object AccessibilityPolicy {
    const val MIN_TOUCH_TARGET_DP = 48
}
