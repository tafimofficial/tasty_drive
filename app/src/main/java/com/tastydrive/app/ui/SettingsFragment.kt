package com.tastydrive.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.tastydrive.app.R
import com.tastydrive.app.data.NetworkPreference
import com.tastydrive.app.databinding.FragmentSettingsBinding
import com.tastydrive.app.service.ApiResult
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        val b = _binding ?: return
        val prefs = viewModel.prefs

        // Cloud Bot
        b.etBotToken.setText(prefs.cloudBotToken)
        b.etChannelId.setText(prefs.cloudChannelId)

        // Local Server
        b.etLocalServerUrl.setText(prefs.localServerUrl)
        b.cbSameTokenLocal.isChecked = prefs.useSameTokenForLocal
        b.layoutLocalCustomAuth.visibility = if (prefs.useSameTokenForLocal) View.GONE else View.VISIBLE
        b.etLocalBotToken.setText(prefs.localBotToken)
        b.etLocalChannelId.setText(prefs.localChannelId)

        // Network preference
        when (prefs.networkPreference) {
            NetworkPreference.BOTH -> b.rbNetworkBoth.isChecked = true
            NetworkPreference.WIFI_ONLY -> b.rbNetworkWifi.isChecked = true
            NetworkPreference.MOBILE_ONLY -> b.rbNetworkMobile.isChecked = true
        }

        // Concurrency
        val concurrency = prefs.concurrency
        b.sliderConcurrency.value = concurrency.toFloat()
        b.tvConcurrencyDesc.text = "$concurrency files uploading at the same time"

        b.tvNativeStatus.text = viewModel.getNativeEngineInfo()
        updateHistoryCount()

        // Check live local server status
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.isLocalConnected.collect { isConnected ->
                    _binding?.let { bound ->
                        context?.let { ctx ->
                            if (prefs.localServerUrl.isNotBlank()) {
                                if (isConnected) {
                                    bound.tvDiscoveryStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
                                    bound.tvDiscoveryStatus.text = "Connected 🟢"
                                } else {
                                    bound.tvDiscoveryStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_orange))
                                    bound.tvDiscoveryStatus.text = "Offline 🔴"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateHistoryCount() {
        _binding?.let { b ->
            val count = viewModel.prefs.getTotalUploadedCount()
            b.tvTotalUploadedHistory.text = "Total sent files recorded: $count"
        }
    }

    private fun setupListeners() {
        val b = _binding ?: return

        // --- 1. Test Cloud Bot Button ---
        b.btnTestBot.setOnClickListener {
            val bound = _binding ?: return@setOnClickListener
            val token = bound.etBotToken.text?.toString()?.trim() ?: ""
            if (token.isEmpty()) {
                bound.tvBotTestResult.text = "Please enter Cloud Bot token!"
                return@setOnClickListener
            }

            bound.tvBotTestResult.text = "Checking cloud bot..."
            viewLifecycleOwner.lifecycleScope.launch {
                when (val result = viewModel.testCloudBot(token)) {
                    is ApiResult.Success -> {
                        _binding?.let { cur ->
                            context?.let { ctx ->
                                cur.tvBotTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
                                cur.tvBotTestResult.text = "✔ ${result.data}"
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        _binding?.let { cur ->
                            context?.let { ctx ->
                                cur.tvBotTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.status_red))
                                cur.tvBotTestResult.text = "✖ ${result.message}"
                            }
                        }
                    }
                }
            }
        }

        // --- 2. Auto-Discover Local PC on Wi-Fi Button ---
        b.btnAutoDiscoverLocal.setOnClickListener {
            val bound = _binding ?: return@setOnClickListener
            bound.btnAutoDiscoverLocal.isEnabled = false
            bound.pbDiscoveringLocal.visibility = View.VISIBLE
            context?.let { ctx ->
                bound.tvDiscoveryStatus.setTextColor(ContextCompat.getColor(ctx, R.color.primary_light))
            }
            bound.tvDiscoveryStatus.text = "Scanning Wi-Fi subnet..."

            viewLifecycleOwner.lifecycleScope.launch {
                val result = viewModel.autoDiscoverLocalServer { checked, total ->
                    _binding?.let { cur ->
                        val pct = if (total > 0) (checked * 100) / total else 0
                        cur.tvDiscoveryStatus.text = "Scanning $pct%..."
                    }
                }

                _binding?.let { cur ->
                    cur.btnAutoDiscoverLocal.isEnabled = true
                    cur.pbDiscoveringLocal.visibility = View.GONE

                    context?.let { ctx ->
                        if (result != null) {
                            cur.etLocalServerUrl.setText(result.url)
                            cur.tvDiscoveryStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
                            cur.tvDiscoveryStatus.text = "✔ Found PC at ${result.ip}:${result.port}!"
                            Toast.makeText(
                                ctx,
                                "🎉 Docker Local Server Found: ${result.url}",
                                Toast.LENGTH_LONG
                            ).show()

                            // Auto-test with current token
                            testLocalServerConnection(result.url)
                        } else {
                            cur.tvDiscoveryStatus.setTextColor(ContextCompat.getColor(ctx, R.color.status_red))
                            cur.tvDiscoveryStatus.text = "PC not found on port 8081. Ensure Docker is running!"
                            Toast.makeText(
                                ctx,
                                "Could not detect Docker on port 8081. Check if PC and phone are on same Wi-Fi!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        }

        // --- 3. Same Token Checkbox Listener ---
        b.cbSameTokenLocal.setOnCheckedChangeListener { _, isChecked ->
            _binding?.layoutLocalCustomAuth?.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        // --- 4. Test Local Server Button ---
        b.btnTestLocalServer.setOnClickListener {
            val bound = _binding ?: return@setOnClickListener
            val serverUrl = bound.etLocalServerUrl.text?.toString()?.trim() ?: ""
            if (serverUrl.isEmpty()) {
                bound.tvLocalServerTestResult.text = "Enter Local Server URL or click Auto-Discover!"
                return@setOnClickListener
            }
            testLocalServerConnection(serverUrl)
        }

        // --- 5. Save All Settings Button ---
        b.btnSaveCredentials.setOnClickListener {
            val bound = _binding ?: return@setOnClickListener
            val cloudToken = bound.etBotToken.text?.toString()?.trim() ?: ""
            val cloudChannel = bound.etChannelId.text?.toString()?.trim() ?: ""
            val localUrl = bound.etLocalServerUrl.text?.toString()?.trim() ?: ""
            val sameToken = bound.cbSameTokenLocal.isChecked
            val localToken = bound.etLocalBotToken.text?.toString()?.trim() ?: ""
            val localChannel = bound.etLocalChannelId.text?.toString()?.trim() ?: ""

            viewModel.prefs.cloudBotToken = cloudToken
            viewModel.prefs.cloudChannelId = cloudChannel
            viewModel.prefs.localServerUrl = localUrl
            viewModel.prefs.useSameTokenForLocal = sameToken
            viewModel.prefs.localBotToken = localToken
            viewModel.prefs.localChannelId = localChannel

            // Trigger immediate local server health check
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.uploadManager.checkLocalServerHealth()
            }

            context?.let { ctx ->
                Toast.makeText(
                    ctx,
                    "Settings saved! Dual Bot configuration updated. 🎉",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // Network preference radio
        b.rgNetworkPref.setOnCheckedChangeListener { _, checkedId ->
            val bound = _binding ?: return@setOnCheckedChangeListener
            when (checkedId) {
                bound.rbNetworkBoth.id -> viewModel.prefs.networkPreference = NetworkPreference.BOTH
                bound.rbNetworkWifi.id -> viewModel.prefs.networkPreference = NetworkPreference.WIFI_ONLY
                bound.rbNetworkMobile.id -> viewModel.prefs.networkPreference = NetworkPreference.MOBILE_ONLY
            }
        }

        // Concurrency slider
        b.sliderConcurrency.addOnChangeListener { _, value, _ ->
            val concurrency = value.toInt()
            viewModel.prefs.concurrency = concurrency
            _binding?.tvConcurrencyDesc?.text = "$concurrency files uploading at the same time"
        }

        // Battery Optimization
        b.btnIgnoreBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        // Reset History
        b.btnResetHistory.setOnClickListener {
            val ctx = context ?: return@setOnClickListener
            AlertDialog.Builder(ctx)
                .setTitle("Reset Upload History?")
                .setMessage("This will clear the record of uploaded files, allowing you to re-upload previously backed-up files.")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.prefs.resetUploadedHistory()
                    viewModel.scanMedia()
                    updateHistoryCount()
                    context?.let {
                        Toast.makeText(it, "Upload history reset!", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun testLocalServerConnection(serverUrl: String) {
        val bound = _binding ?: return
        val sameToken = bound.cbSameTokenLocal.isChecked
        val token = if (sameToken || bound.etLocalBotToken.text.isNullOrBlank()) {
            bound.etBotToken.text?.toString()?.trim() ?: ""
        } else {
            bound.etLocalBotToken.text?.toString()?.trim() ?: ""
        }

        if (token.isEmpty()) {
            context?.let { ctx ->
                bound.tvLocalServerTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.status_orange))
            }
            bound.tvLocalServerTestResult.text = "Please enter a Bot Token to test!"
            return
        }

        context?.let { ctx ->
            bound.tvLocalServerTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.primary_light))
        }
        bound.tvLocalServerTestResult.text = "Testing connection..."

        viewLifecycleOwner.lifecycleScope.launch {
            when (val result = viewModel.testLocalServer(serverUrl, token)) {
                is ApiResult.Success -> {
                    _binding?.let { cur ->
                        context?.let { ctx ->
                            cur.tvLocalServerTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.status_green))
                            cur.tvLocalServerTestResult.text = "✔ ${result.data} (Supports 2GB uploads!)"
                        }
                    }
                }
                is ApiResult.Error -> {
                    _binding?.let { cur ->
                        context?.let { ctx ->
                            cur.tvLocalServerTestResult.setTextColor(ContextCompat.getColor(ctx, R.color.status_red))
                            cur.tvLocalServerTestResult.text = "✖ ${result.message}"
                        }
                    }
                }
            }
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = ctx.packageName
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        startActivity(intent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            } else {
                Toast.makeText(ctx, "Battery optimization already disabled! ⚡", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
