package com.farm_tech.farmhub.viewmodel

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State

class ThemeViewModel : ViewModel() {
    private val _useDarkTheme = mutableStateOf(false)
    val useDarkTheme: State<Boolean> = _useDarkTheme

    fun toggleTheme() {
        _useDarkTheme.value = !_useDarkTheme.value
    }
}

