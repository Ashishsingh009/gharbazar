package com.ashish.gharbazaar.domain

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

sealed class GateResult {
    data class Accepted(val surfaceId: String, val sequence: Long) : GateResult()
    data class Rejected(val reason: String) : GateResult()
}

class CatalogGate(catalogJson: String) {
    private val catalog = JSONObject(catalogJson)
    private val catalogId = catalog.getString("catalogId")
    private val protocolVersion = catalog.getString("protocolVersion")
    private val components = catalog.getJSONObject("components")
    private val lastSequence = mutableMapOf<String, Long>()

    fun accept(messageJson: String): GateResult {
        val root = try {
            JSONTokener(messageJson).nextValue()
        } catch (_: Exception) {
            return GateResult.Rejected("Malformed message.")
        }
        if (root !is JSONObject) return GateResult.Rejected("Malformed message.")
        val version = root.optString("version")
        if (version != protocolVersion) return GateResult.Rejected("Unsupported protocol version.")
        if (!root.has("sequence")) return GateResult.Rejected("Missing sequence.")
        val sequence = root.optLong("sequence", -1)
        if (sequence < 0) return GateResult.Rejected("Missing sequence.")
        val envelopes = listOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface").count { root.has(it) }
        if (envelopes != 1) return GateResult.Rejected("Malformed message.")
        val surfaceId = surfaceId(root) ?: return GateResult.Rejected("Missing surface.")
        val previous = lastSequence[surfaceId]
        if (previous != null && sequence <= previous) return GateResult.Rejected("Stale or duplicate message.")
        val decision = when {
            root.has("createSurface") -> validateCreate(root.getJSONObject("createSurface"))
            root.has("updateComponents") -> validateComponents(root.getJSONObject("updateComponents"))
            root.has("updateDataModel") -> GateResult.Accepted(surfaceId, sequence)
            else -> GateResult.Accepted(surfaceId, sequence)
        }
        if (decision is GateResult.Accepted) lastSequence[surfaceId] = sequence
        return decision
    }

    private fun validateCreate(message: JSONObject): GateResult {
        val id = message.optString("catalogId")
        if (id != catalogId) return GateResult.Rejected("Unsupported catalog version.")
        return GateResult.Accepted(message.getString("surfaceId"), 0)
    }

    private fun validateComponents(message: JSONObject): GateResult {
        val surfaceId = message.getString("surfaceId")
        val componentsArray = message.optJSONArray("components") ?: return GateResult.Rejected("Malformed message.")
        val seen = mutableSetOf<String>()
        for (index in 0 until componentsArray.length()) {
            val component = componentsArray.getJSONObject(index)
            val id = component.optString("id")
            if (id.isBlank() || !seen.add(id)) return GateResult.Rejected("Duplicate component id.")
            val type = component.optString("component")
            if (!components.has(type)) return GateResult.Rejected("Unknown component.")
            val spec = components.getJSONObject(type)
            val allowed = stringSet(spec.getJSONArray("required")) + stringSet(spec.getJSONArray("optional"))
            val required = stringSet(spec.getJSONArray("required"))
            val keys = component.keys().asSequence().filter { it != "id" && it != "component" }.toSet()
            if (keys.any { it !in allowed }) return GateResult.Rejected("Unknown property.")
            if (required.any { !component.has(it) }) return GateResult.Rejected("Missing property.")
            val invalid = keys.any { key -> !validValue(component.get(key)) }
            if (invalid) return GateResult.Rejected("Invalid binding.")
        }
        return GateResult.Accepted(surfaceId, 0)
    }

    private fun validValue(value: Any?): Boolean = when (value) {
        null, is String, is Number, is Boolean -> true
        is JSONArray -> (0 until value.length()).all { validValue(value.get(it)) }
        else -> false
    }

    private fun surfaceId(root: JSONObject): String? {
        val key = listOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface").firstOrNull { root.has(it) }
            ?: return null
        return root.getJSONObject(key).optString("surfaceId").ifBlank { null }
    }

    private fun stringSet(array: JSONArray): Set<String> = (0 until array.length()).map { array.getString(it) }.toSet()
}
