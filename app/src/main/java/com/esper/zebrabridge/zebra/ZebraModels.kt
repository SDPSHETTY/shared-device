package com.esper.authapp.zebra

enum class LockScreenState {
    HIDDEN,
    SHOWN
}

data class UserSession(
    val eventType: String?,
    val userId: String?,
    val userRole: String?,
    val userLoggedInState: String?,
    val rawJson: String
)
