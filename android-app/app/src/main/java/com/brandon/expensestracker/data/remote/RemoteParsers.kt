package com.brandon.expensestracker.data.remote

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

fun JsonObject.string(key: String): String =
    if (has(key) && !get(key).isJsonNull) get(key).asString else ""

fun parseRemoteDataMap(rawData: JsonElement?, gson: Gson): Map<String, String> {
    if (rawData == null || rawData.isJsonNull) {
        return emptyMap()
    }

    val asObject = when {
        rawData.isJsonObject -> rawData.asJsonObject
        rawData.isJsonPrimitive && rawData.asJsonPrimitive.isString -> {
            runCatching { JsonParser.parseString(rawData.asString) }
                .getOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
        }
        else -> null
    } ?: return emptyMap()

    val result = mutableMapOf<String, String>()
    asObject.entrySet().forEach { (key, value) ->
        result[key] = if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
            value.asString
        } else {
            gson.toJson(value)
        }
    }
    return result
}
