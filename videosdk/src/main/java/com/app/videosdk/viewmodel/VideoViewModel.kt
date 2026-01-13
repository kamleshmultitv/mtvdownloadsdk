package com.app.videosdk.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.videosdk.model.OptionItemModel
import com.app.videosdk.model.SpeedControlModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class VideoViewModel : ViewModel() {

    private val _speedControlData = MutableLiveData<List<SpeedControlModel>>()
    val speedControlData: LiveData<List<SpeedControlModel>> get() = _speedControlData

    fun getSpeedData() {
        viewModelScope.launch {
            val speedControlArrayList = listOf(
                SpeedControlModel("0.75x", 0.98f, 0.75f),
                SpeedControlModel("1.0x", 1.00f, 1.00f),
                SpeedControlModel("1.25x", 1.02f, 1.25f),
                SpeedControlModel("1.50x", 1.05f, 1.50f),
                SpeedControlModel("2.0x", 1.10f, 2.00f)
            )
            _speedControlData.value = speedControlArrayList
        }
    }

    // Immutable list of options
    private val _options = MutableStateFlow(
        listOf(
            OptionItemModel(1, "Audio Track"),
            OptionItemModel(2, "Close Caption"),
            OptionItemModel(3, "Speed Selector"),
            OptionItemModel(4, "Video Quality")
        )
    )
    val options: StateFlow<List<OptionItemModel>> = _options
}