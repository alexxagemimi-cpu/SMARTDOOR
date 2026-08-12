package com.exhibition.smartdoorlock

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.exhibition.smartdoorlock.ui.AppViewModelFactory
import com.exhibition.smartdoorlock.ui.doorcontrol.DoorControlScreen
import com.exhibition.smartdoorlock.ui.doorcontrol.DoorControlViewModel
import com.exhibition.smartdoorlock.ui.rateproject.RateProjectScreen
import com.exhibition.smartdoorlock.ui.rateproject.RateProjectViewModel
import com.exhibition.smartdoorlock.ui.theme.SmartDoorLockTheme

class MainActivity : ComponentActivity() {

    private val factory by lazy { AppViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartDoorLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    DashboardRoot(factory = factory)
                }
            }
        }
    }
}

private enum class DashboardTab(val label: String) {
    DOOR_CONTROL("Door Control"),
    RATE_PROJECT("Rate Project")
}

@Composable
private fun DashboardRoot(factory: AppViewModelFactory) {
    var selectedTab by rememberSaveable { mutableStateOf(DashboardTab.DOOR_CONTROL) }

    // API 31+ (Android 12+) requires the BLUETOOTH_CONNECT runtime permission before the
    // app can list bonded devices or open a socket. Below API 31, BLUETOOTH/BLUETOOTH_ADMIN
    // are normal permissions granted automatically at install, so no runtime prompt is needed.
    var hasBluetoothPermission by rememberSaveable {
        mutableStateOf(Build.VERSION.SDK_INT < Build.VERSION_CODES.S)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasBluetoothPermission = granted }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* Result ignored — the Connect button re-checks adapter state on next tap. */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.DOOR_CONTROL,
                    onClick = { selectedTab = DashboardTab.DOOR_CONTROL },
                    icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    label = { Text(DashboardTab.DOOR_CONTROL.label) }
                )
                NavigationBarItem(
                    selected = selectedTab == DashboardTab.RATE_PROJECT,
                    onClick = { selectedTab = DashboardTab.RATE_PROJECT },
                    icon = { Icon(Icons.Default.Star, contentDescription = null) },
                    label = { Text(DashboardTab.RATE_PROJECT.label) }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                DashboardTab.DOOR_CONTROL -> {
                    val vm: DoorControlViewModel = viewModel(factory = factory)
                    DoorControlScreen(
                        viewModel = vm,
                        hasBluetoothPermission = hasBluetoothPermission,
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        },
                        onRequestEnableBluetooth = {
                            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }
                    )
                }
                DashboardTab.RATE_PROJECT -> {
                    val vm: RateProjectViewModel = viewModel(factory = factory)
                    RateProjectScreen(viewModel = vm)
                }
            }
        }
    }
}
