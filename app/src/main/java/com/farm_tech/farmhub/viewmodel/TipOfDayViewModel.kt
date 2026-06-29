package com.farm_tech.farmhub.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import com.farm_tech.farmhub.models.tips.FarmingTip
import com.farm_tech.farmhub.models.tips.FarmingTipUiModel
import com.farm_tech.farmhub.repository.TipRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TipUiState(
    val isLoading: Boolean = true,
    val tip: FarmingTipUiModel? = null,
    val error: String? = null,
    val categories: List<String> = emptyList()
)

class TipOfDayViewModel(context: Context) : ViewModel() {
    private val appContext = context.applicationContext
    private val _uiState = MutableStateFlow(TipUiState())
    val uiState: StateFlow<TipUiState> = _uiState.asStateFlow()

    init {
        TipRepository.initialize(appContext)
        _uiState.value = TipUiState(
            isLoading = false,
            tip = TipRepository.getRandomTip()?.toUiModel(),
            categories = TipRepository.categories()
        )
    }

    fun refresh(category: String? = null) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        val tip = TipRepository.getRandomTip(category)
        _uiState.value = if (tip != null) {
            _uiState.value.copy(
                isLoading = false,
                tip = tip.toUiModel(),
                error = null,
                categories = TipRepository.categories()
            )
        } else {
            _uiState.value.copy(
                isLoading = false,
                error = "No tips available right now."
            )
        }
    }

    private fun FarmingTip.toUiModel(): FarmingTipUiModel {
        return FarmingTipUiModel(
            id = id,
            category = category,
            title = title,
            tip = tip
        )
    }
}
