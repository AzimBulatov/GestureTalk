package com.example.gesturetalk.chat

data class ChatMessage(
    val text: String = "",
    val isUser: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    // Пустой конструктор для Firebase
    constructor() : this("", false, 0L)
}
