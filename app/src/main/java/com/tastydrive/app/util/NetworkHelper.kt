package com.tastydrive.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.tastydrive.app.data.NetworkPreference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkHelper(private val context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _isConnected = MutableStateFlow(isCurrentlyConnected())
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _isWifi = MutableStateFlow(isWifiConnected())
    val isWifi: StateFlow<Boolean> = _isWifi

    private val _isMobile = MutableStateFlow(isMobileDataConnected())
    val isMobile: StateFlow<Boolean> = _isMobile

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkStates()
        }

        override fun onLost(network: Network) {
            updateNetworkStates()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            updateNetworkStates()
        }
    }

    init {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNetworkStates() {
        _isConnected.value = isCurrentlyConnected()
        _isWifi.value = isWifiConnected()
        _isMobile.value = isMobileDataConnected()
    }

    fun isCurrentlyConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isWifiConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun isMobileDataConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun isUploadAllowed(pref: NetworkPreference): Boolean {
        if (!isCurrentlyConnected()) return false
        return when (pref) {
            NetworkPreference.BOTH -> true
            NetworkPreference.WIFI_ONLY -> isWifiConnected()
            NetworkPreference.MOBILE_ONLY -> isMobileDataConnected()
        }
    }

    fun getNetworkStatusDescription(): String {
        return when {
            isWifiConnected() -> "Connected via Wi-Fi ⚡"
            isMobileDataConnected() -> "Connected via Mobile Data 📶"
            isCurrentlyConnected() -> "Connected (Other Network)"
            else -> "Offline 🔴 (No Internet)"
        }
    }

    companion object {
        @Volatile
        private var instance: NetworkHelper? = null

        fun getInstance(context: Context): NetworkHelper {
            return instance ?: synchronized(this) {
                instance ?: NetworkHelper(context.applicationContext).also { instance = it }
            }
        }
    }
}

