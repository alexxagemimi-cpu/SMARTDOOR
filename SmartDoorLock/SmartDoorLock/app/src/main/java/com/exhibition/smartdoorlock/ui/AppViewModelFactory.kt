package com.exhibition.smartdoorlock.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.exhibition.smartdoorlock.bluetooth.BluetoothConnectionManager
import com.exhibition.smartdoorlock.data.local.AppDatabase
import com.exhibition.smartdoorlock.data.repository.ReviewRepository
import com.exhibition.smartdoorlock.speech.TextToSpeechManager
import com.exhibition.smartdoorlock.ui.doorcontrol.DoorControlViewModel
import com.exhibition.smartdoorlock.ui.rateproject.RateProjectViewModel

/**
 * Composition root for the app's two ViewModels. There's no DI framework here —
 * with exactly three dependencies (Bluetooth, TTS, Room), a hand-written factory
 * is simpler to read and modify than pulling in Hilt for an exhibition build.
 *
 * [appContext] must be an application Context (see MainActivity), never an
 * Activity Context, so BluetoothConnectionManager/TextToSpeechManager can't leak one.
 */
class AppViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when {
            modelClass.isAssignableFrom(DoorControlViewModel::class.java) -> {
                DoorControlViewModel(
                    bluetoothManager = BluetoothConnectionManager(appContext),
                    ttsManager = TextToSpeechManager(appContext)
                ) as T
            }
            modelClass.isAssignableFrom(RateProjectViewModel::class.java) -> {
                val dao = AppDatabase.getInstance(appContext).reviewDao()
                RateProjectViewModel(ReviewRepository(dao)) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
