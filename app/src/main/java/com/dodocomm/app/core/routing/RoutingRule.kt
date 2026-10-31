package com.dodocomm.app.core.routing

import java.time.LocalTime
import org.json.JSONArray
import org.json.JSONObject

/**
 * One routing rule. Conditions are ANDed; an empty condition is ignored.
 * The first matching rule wins unless [continueMatching] is true.
 */
data class RoutingRule(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val senderContains: String = "",
    val senderRegex: String = "",
    val keywordContains: String = "",
    val keywordRegex: String = "",
    val simSlot: Int = 0,
    val timeStartMinutes: Int = -1,
    val timeEndMinutes: Int = -1,
    val targetChannelIds: List<String> = emptyList(),
    val continueMatching: Boolean = false,
    val fallbackToDefault: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("enabled", enabled)
        put("senderContains", senderContains)
        put("senderRegex", senderRegex)
        put("keywordContains", keywordContains)
        put("keywordRegex", keywordRegex)
        put("simSlot", simSlot)
        put("timeStartMinutes", timeStartMinutes)
        put("timeEndMinutes", timeEndMinutes)
        put("targetChannelIds", JSONArray(targetChannelIds))
        put("continueMatching", continueMatching)
        put("fallbackToDefault", fallbackToDefault)
    }

    companion object {
        fun fromJson(json: JSONObject): RoutingRule = RoutingRule(
            id = json.optString("id"),
            name = json.optString("name").ifBlank { "规则" },
            enabled = json.optBoolean("enabled", true),
            senderContains = json.optString("senderContains"),
            senderRegex = json.optString("senderRegex"),
            keywordContains = json.optString("keywordContains"),
            keywordRegex = json.optString("keywordRegex"),
            simSlot = json.optInt("simSlot", 0),
            timeStartMinutes = json.optInt("timeStartMinutes", -1),
            timeEndMinutes = json.optInt("timeEndMinutes", -1),
            targetChannelIds = json.optJSONArray("targetChannelIds")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.orEmpty(),
            continueMatching = json.optBoolean("continueMatching", false),
            fallbackToDefault = json.optBoolean("fallbackToDefault", true),
        )

        fun create(name: String = "新规则"): RoutingRule = RoutingRule(
            id = "rule-" + java.util.UUID.randomUUID().toString().take(8),
            name = name,
        )
    }
}

data class RoutingContext(
    val sender: String,
    val body: String,
    val simLabel: String?,
    val nowMinutes: Int = LocalTime.now().let { it.hour * 60 + it.minute },
) {
    val simSlot: Int?
        get() = simLabel?.let { label ->
            Regex("SIM\\s*(\\d+)", RegexOption.IGNORE_CASE).find(label)
                ?.groupValues?.getOrNull(1)?.toIntOrNull()
        }
}

object RoutingEngine {
    /** Returns the channels that should receive this message. */
    fun targets(
        context: RoutingContext,
        rules: List<RoutingRule>,
        activeChannels: List<com.dodocomm.app.core.channel.PushChannel>,
    ): List<com.dodocomm.app.core.channel.PushChannel> {
        if (activeChannels.isEmpty()) return emptyList()
        val matchedTargets = LinkedHashSet<String>()
        var matched = false
        for (rule in rules) {
            if (!rule.enabled || !rule.matches(context)) continue
            matched = true
            matchedTargets += rule.targetChannelIds
            if (!rule.continueMatching) break
        }
        if (!matched) return activeChannels
        val selected = activeChannels.filter { it.id in matchedTargets }
        return if (selected.isEmpty()) {
            if (rules.any { it.matches(context) && it.fallbackToDefault }) activeChannels else emptyList()
        } else {
            selected
        }
    }

    fun RoutingRule.matches(context: RoutingContext): Boolean {
        if (!enabled) return false
        if (senderContains.isNotBlank() && !context.sender.contains(senderContains, ignoreCase = true)) return false
        if (senderRegex.isNotBlank() && !safeRegexMatches(senderRegex, context.sender)) return false
        if (keywordContains.isNotBlank() && !context.body.contains(keywordContains, ignoreCase = true)) return false
        if (keywordRegex.isNotBlank() && !safeRegexMatches(keywordRegex, context.body)) return false
        if (simSlot > 0 && context.simSlot != simSlot) return false
        if (timeStartMinutes >= 0 && timeEndMinutes >= 0 && !inTimeWindow(context.nowMinutes)) return false
        return true
    }

    private fun safeRegexMatches(pattern: String, value: String): Boolean =
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(value) }.getOrDefault(false)

    private fun RoutingRule.inTimeWindow(now: Int): Boolean {
        if (timeStartMinutes < 0 || timeEndMinutes < 0) return true
        return if (timeStartMinutes <= timeEndMinutes) {
            now in timeStartMinutes..timeEndMinutes
        } else {
            now >= timeStartMinutes || now <= timeEndMinutes
        }
    }
}
