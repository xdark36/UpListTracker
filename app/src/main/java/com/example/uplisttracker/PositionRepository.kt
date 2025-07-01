package com.example.uplisttracker

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PositionRepository {
    private val _position = MutableStateFlow("--")
    val position: StateFlow<String> = _position

    fun updatePosition(newPosition: String) {
        _position.value = newPosition
    }
} 