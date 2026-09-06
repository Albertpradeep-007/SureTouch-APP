package com.example.suretouchapp.data.model

import com.google.gson.JsonParser

/** Only an explicit ambiguity response opens the chooser; ordinary logins do not. */
fun accountRoleChoices(raw: String): List<String> = runCatching {
    val body = JsonParser().parse(raw).asJsonObject
    if (body.get("code")?.asString != "ACCOUNT_ROLE_REQUIRED") return emptyList()
    body.getAsJsonArray("roles").map { it.asString }
        .filter { it in setOf("STUDENT", "MENTOR", "VOLUNTEER", "TRUSTEE", "COMPANY", "ADMIN") }
        .distinct().takeIf { it.size > 1 }.orEmpty()
}.getOrDefault(emptyList())

fun accountRoleLabel(role: String): String = role.lowercase().replaceFirstChar { it.uppercase() }
