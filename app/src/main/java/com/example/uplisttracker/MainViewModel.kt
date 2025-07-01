package com.example.uplisttracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val positionRepository: PositionRepository
) : ViewModel() {
    private val _position = MutableStateFlow("--")
    val position: StateFlow<String> = _position

    private val _status = MutableStateFlow("Idle")
    val status: StateFlow<String> = _status

    private val _banner = MutableStateFlow("")
    val banner: StateFlow<String> = _banner

    init {
        viewModelScope.launch {
            positionRepository.position.collect {
                _position.value = it
            }
        }
    }

    fun updateStatus(newStatus: String) {
        _status.value = newStatus
    }

    fun showBanner(message: String) {
        _banner.value = message
    }

    fun clearBanner() {
        _banner.value = ""
    }
} 