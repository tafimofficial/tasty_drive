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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
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
        val prefs = viewModel.prefs

        // Cloud Bot
        binding.etBotToken.setText(prefs.cloudBotToken)
        binding.etChannelId.setText(prefs.cloudChannelId)

        // Local Server
        binding.etLocalServerUrl.setText(prefs.localServerUrl)
        binding.cbSameTokenLocal.isChecked = prefs.useSameTokenForLocal
        binding.layoutLocalCustomAuth.visibility = if (prefs.useSameTokenForLocal) View.GONE else View.VISIBLE
        binding.etLocalBotToken.setText(prefs.localBotToken)
        binding.etLocalChannelId.setText(prefs.localChannelId)

        // Network preference
        when (prefs.networkPreference) {
            NetworkPreference.BOTH -> binding.rbNetworkBoth.isChecked = true
            NetworkPreference.WIFI_ONLY -> binding.rbNetworkWifi.isChecked = true
            NetworkPreference.MOBILE_ONLY -> binding.rbNetworkMobile.isChecked = true
        }

        // Concurrency
        val concurrency = prefs.concurrency
        binding.sliderConcurrency.value = concurrency.toFloat()
        binding.tvConcurrencyDesc.text = "$concurrency files uploading at the same time"

        binding.tvNativeStatus.text = viewModel.getNativeEngineInfo()
        updateHistoryCount()

        // Check live local server status
        lifecycleScope.launch {
            viewModel.isLocalConnected.collect { isConnected ->
                if (prefs.localServerUrl.isNotBlank()) {
                    if (isConnected) {
                        binding.tvDiscoveryStatus.setTextColor(resources.getColor(R.color.status_green, null))
                        binding.tvDiscoveryStatus.text = "Connected 🟢"
                    } else {
                        binding.tvDiscoveryStatus.setTextColor(resources.getColor(R.color.status_orange, null))
                        binding.tvDiscoveryStatus.text = "Offline 🔴"
                    }
                }
            }
        }
    }

    private fun updateHistoryCount() {
        val count = viewModel.prefs.getTotalUploadedCount()
        binding.tvTotalUploadedHistory.text = "Total sent files recorded: $count"
    }

    private fun setupListeners() {
        // --- 1. Test Cloud Bot Button ---
        binding.btnTestBot.setOnClickListener {
            val token = binding.etBotToken.text?.toString()?.trim() ?: ""
            if (token.isEmpty()) {
                binding.tvBotTestResult.text = "Please enter Cloud Bot token!"
                return@setOnClickListener
            }

            binding.tvBotTestResult.text = "Checking cloud bot..."
            lifecycleScope.launch {
                when (val result = viewModel.testCloudBot(token)) {
                    is ApiResult.Success -> {
                        binding.tvBotTestResult.setTextColor(resources.getColor(R.color.status_green, null))
                        binding.tvBotTestResult.text = "✔ ${result.data}"
                    }
                    is ApiResult.Error -> {
                        binding.tvBotTestResult.setTextColor(resources.getColor(R.color.status_red, null))
                        binding.tvBotTestResult.text = "✖ ${result.message}"
                    }
                }
            }
        }

        // --- 2. Auto-Discover Local PC on Wi-Fi Button ---
        binding.btnAutoDiscoverLocal.setOnClickListener {
            binding.btnAutoDiscoverLocal.isEnabled = false
            binding.pbDiscoveringLocal.visibility = View.VISIBLE
            binding.tvDiscoveryStatus.setTextColor(resources.getColor(R.color.primary_light, null))
            binding.tvDiscoveryStatus.text = "Scanning Wi-Fi subnet..."

            lifecycleScope.launch {
                val result = viewModel.autoDiscoverLocalServer { checked, total ->
                    val pct = if (total > 0) (checked * 100) / total else 0
                    binding.tvDiscoveryStatus.text = "Scanning $pct%..."
                }

                binding.btnAutoDiscoverLocal.isEnabled = true
                binding.pbDiscoveringLocal.visibility = View.GONE

                if (result != null) {
                    binding.etLocalServerUrl.setText(result.url)
                    binding.tvDiscoveryStatus.setTextColor(resources.getColor(R.color.status_green, null))
                    binding.tvDiscoveryStatus.text = "✔ Found PC at ${result.ip}:${result.port}!"
                    Toast.makeText(
                        requireContext(),
                        "🎉 Docker Local Server Found: ${result.url}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Auto-test with current token
                    testLocalServerConnection(result.url)
                } else {
                    binding.tvDiscoveryStatus.setTextColor(resources.getColor(R.color.status_red, null))
                    binding.tvDiscoveryStatus.text = "PC not found on port 8081. Ensure Docker is running!"
                    Toast.makeText(
                        requireContext(),
                        "Could not detect Docker on port 8081. Check if PC and phone are on same Wi-Fi!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // --- 3. Same Token Checkbox Listener ---
        binding.cbSameTokenLocal.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutLocalCustomAuth.visibility = if (isChecked) View.GONE else View.VISIBLE
        }

        // --- 4. Test Local Server Button ---
        binding.btnTestLocalServer.setOnClickListener {
            val serverUrl = binding.etLocalServerUrl.text?.toString()?.trim() ?: ""
            if (serverUrl.isEmpty()) {
                binding.tvLocalServerTestResult.text = "Enter Local Server URL or click Auto-Discover!"
                return@setOnClickListener
            }
            testLocalServerConnection(serverUrl)
        }

        // --- 5. Save All Settings Button ---
        binding.btnSaveCredentials.setOnClickListener {
            val cloudToken = binding.etBotToken.text?.toString()?.trim() ?: ""
            val cloudChannel = binding.etChannelId.text?.toString()?.trim() ?: ""
            val localUrl = binding.etLocalServerUrl.text?.toString()?.trim() ?: ""
            val sameToken = binding.cbSameTokenLocal.isChecked
            val localToken = binding.etLocalBotToken.text?.toString()?.trim() ?: ""
            val localChannel = binding.etLocalChannelId.text?.toString()?.trim() ?: ""

            viewModel.prefs.cloudBotToken = cloudToken
            viewModel.prefs.cloudChannelId = cloudChannel
            viewModel.prefs.localServerUrl = localUrl
            viewModel.prefs.useSameTokenForLocal = sameToken
            viewModel.prefs.localBotToken = localToken
            viewModel.prefs.localChannelId = localChannel

            // Trigger immediate local server health check
            lifecycleScope.launch {
                viewModel.uploadManager.checkLocalServerHealth()
            }

            Toast.makeText(
                requireContext(),
                "Settings saved! Dual Bot configuration updated. 🎉",
                Toast.LENGTH_SHORT
            ).show()
        }

        // Network preference radio
        binding.rgNetworkPref.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.rbNetworkBoth.id -> viewModel.prefs.networkPreference = NetworkPreference.BOTH
                binding.rbNetworkWifi.id -> viewModel.prefs.networkPreference = NetworkPreference.WIFI_ONLY
                binding.rbNetworkMobile.id -> viewModel.prefs.networkPreference = NetworkPreference.MOBILE_ONLY
            }
        }

        // Concurrency slider
        binding.sliderConcurrency.addOnChangeListener { _, value, _ ->
            val concurrency = value.toInt()
            viewModel.prefs.concurrency = concurrency
            binding.tvConcurrencyDesc.text = "$concurrency files uploading at the same time"
        }

        // Battery Optimization
        binding.btnIgnoreBattery.setOnClickListener {
            requestIgnoreBatteryOptimization()
        }

        // Reset History
        binding.btnResetHistory.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Reset Upload History?")
                .setMessage("This will clear the record of uploaded files, allowing you to re-upload previously backed-up files.")
                .setPositiveButton("Reset") { _, _ ->
                    viewModel.prefs.resetUploadedHistory()
                    viewModel.scanMedia()
                    updateHistoryCount()
                    Toast.makeText(requireContext(), "Upload history reset!", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun testLocalServerConnection(serverUrl: String) {
        val sameToken = binding.cbSameTokenLocal.isChecked
        val token = if (sameToken || binding.etLocalBotToken.text.isNullOrBlank()) {
            binding.etBotToken.text?.toString()?.trim() ?: ""
        } else {
            binding.etLocalBotToken.text?.toString()?.trim() ?: ""
        }

        if (token.isEmpty()) {
            binding.tvLocalServerTestResult.setTextColor(resources.getColor(R.color.status_orange, null))
            binding.tvLocalServerTestResult.text = "Please enter a Bot Token to test!"
            return
        }

        binding.tvLocalServerTestResult.setTextColor(resources.getColor(R.color.primary_light, null))
        binding.tvLocalServerTestResult.text = "Testing connection..."

        lifecycleScope.launch {
            when (val result = viewModel.testLocalServer(serverUrl, token)) {
                is ApiResult.Success -> {
                    binding.tvLocalServerTestResult.setTextColor(resources.getColor(R.color.status_green, null))
                    binding.tvLocalServerTestResult.text = "✔ ${result.data} (Supports 2GB uploads!)"
                }
                is ApiResult.Error -> {
                    binding.tvLocalServerTestResult.setTextColor(resources.getColor(R.color.status_red, null))
                    binding.tvLocalServerTestResult.text = "✖ ${result.message}"
                }
            }
        }
    }

    private fun requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
            val packageName = requireContext().packageName
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    startActivity(intent)
                }
            } else {
                Toast.makeText(requireContext(), "Battery optimization already disabled! ⚡", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
