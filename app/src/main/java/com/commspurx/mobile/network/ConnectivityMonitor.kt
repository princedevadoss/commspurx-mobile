package com.commspurx.mobile.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.commspurx.mobile.data.api.HealthApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class BackendConnectionStatus {
    Offline,
    Checking,
    Connected,
    Unreachable,
}

class ConnectivityMonitor(
    context: Context,
    private val healthApi: HealthApi,
) {
    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _hasNetwork = MutableStateFlow(hasActiveNetwork())
    val hasNetwork: StateFlow<Boolean> = _hasNetwork.asStateFlow()

    private val _backendStatus = MutableStateFlow(
        if (_hasNetwork.value) BackendConnectionStatus.Checking else BackendConnectionStatus.Offline,
    )
    val backendStatus: StateFlow<BackendConnectionStatus> = _backendStatus.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _hasNetwork.value = hasActiveNetwork()
            if (_hasNetwork.value) {
                probeBackend()
            } else {
                _backendStatus.value = BackendConnectionStatus.Offline
            }
        }

        override fun onLost(network: Network) {
            _hasNetwork.value = hasActiveNetwork()
            if (!_hasNetwork.value) {
                _backendStatus.value = BackendConnectionStatus.Offline
            } else {
                probeBackend()
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities,
        ) {
            val online = networkCapabilities.hasInternetAccess()
            _hasNetwork.value = online
            if (!online) {
                _backendStatus.value = BackendConnectionStatus.Offline
            } else if (
                _backendStatus.value == BackendConnectionStatus.Offline ||
                _backendStatus.value == BackendConnectionStatus.Unreachable
            ) {
                probeBackend()
            }
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        if (_hasNetwork.value) {
            probeBackend()
        } else {
            _backendStatus.value = BackendConnectionStatus.Offline
        }
    }

    /** Call after a successful API sync — only when the device has validated internet. */
    fun noteBackendReachable() {
        if (_hasNetwork.value) {
            _backendStatus.value = BackendConnectionStatus.Connected
        }
    }

    fun probeBackend() {
        if (!_hasNetwork.value) {
            _backendStatus.value = BackendConnectionStatus.Offline
            return
        }
        scope.launch {
            if (_backendStatus.value != BackendConnectionStatus.Connected) {
                _backendStatus.value = BackendConnectionStatus.Checking
            }
            repeat(2) { attempt ->
                try {
                    val response = healthApi.ping()
                    if (response.status == "ok" || response.status == "degraded") {
                        _backendStatus.value = BackendConnectionStatus.Connected
                        return@launch
                    }
                } catch (_: Exception) {
                    if (attempt == 0) delay(400)
                }
            }
            _backendStatus.value = BackendConnectionStatus.Unreachable
        }
    }

    private fun hasActiveNetwork(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        return caps.hasInternetAccess()
    }

    private fun NetworkCapabilities.hasInternetAccess(): Boolean {
        if (!hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            true
        }
    }
}
