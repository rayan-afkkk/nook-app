package com.nook.app.data.repo

/** Usernames: permanent, unique, lowercase. Mirrors the regex in firebase/firestore.rules. */
object UsernameRules {
    const val MIN = 3
    const val MAX = 20
    private val allowed = Regex("^[a-z0-9_.]+$")

    fun normalize(input: String): String = input.trim().lowercase().removePrefix("@")

    /** Returns an error message or null if valid. Expects a normalized value. */
    fun validate(username: String): String? = when {
        username.length < MIN -> "At least $MIN characters"
        username.length > MAX -> "At most $MAX characters"
        !allowed.matches(username) -> "Only a–z, 0–9, _ and ."
        username.startsWith('.') || username.endsWith('.') -> "Can't start or end with a dot"
        ".." in username -> "No double dots"
        else -> null
    }
}
