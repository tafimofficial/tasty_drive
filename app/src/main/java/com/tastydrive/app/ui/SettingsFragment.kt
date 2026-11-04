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
        binding.etBotToken.setText(prefs.botToken)
        binding.etChannelId.setText(prefs.channelId)

        when (prefs.networkPreference) {
            NetworkPreference.BOTH -> binding.rbNetworkBoth.isChecked = true
            NetworkPreference.WIFI_ONLY -> binding.rbNetworkWifi.isChecked = true
            NetworkPreference.MOBILE_ONLY -> binding.rbNetworkMobile.isChecked = true
        }

        val concurrency = prefs.concurrency
        binding.sliderConcurrency.value = concurrency.toFloat()
        binding.tvConcurrencyDesc.text = "$concurrency files uploading at the same time"

        binding.tvNativeStatus.text = viewModel.getNativeEngineInfo()
        updateHistoryCount()
    }

    private fun updateHistoryCount() {
        val count = viewModel.prefs.getTotalUploadedCount()
        binding.tvTotalUploadedHistory.text = "Total sent files recorded: $count"
    }

    private fun setupListeners() {
        // Test Bot Button
        binding.btnTestBot.setOnClickListener {
            val token = binding.etBotToken.text?.toString()?.trim() ?: ""
            if (token.isEmpty()) {
                binding.tvBotTestResult.text = "Please enter bot token!"
                return@setOnClickListener
            }

            binding.tvBotTestResult.text = "Checking bot..."
            lifecycleScope.launch {
                when (val result = viewModel.testBot(token)) {
                    is ApiResult.Success -> {
                        binding.tvBotTestResult.setTextColor(resources.getColor(com.tastydrive.app.R.color.status_green, null))
                        binding.tvBotTestResult.text = "✔ ${result.data}"
                    }
                    is ApiResult.Error -> {
                        binding.tvBotTestResult.setTextColor(resources.getColor(com.tastydrive.app.R.color.status_red, null))
                        binding.tvBotTestResult.text = "✖ ${result.message}"
                    }
                }
            }
        }

        // Test Channel Button
        binding.btnTestChannel.setOnClickListener {
            val token = binding.etBotToken.text?.toString()?.trim() ?: ""
            val channel = binding.etChannelId.text?.toString()?.trim() ?: ""
            if (token.isEmpty() || channel.isEmpty()) {
                binding.tvChannelTestResult.text = "Enter bot token & channel id!"
                return@setOnClickListener
            }

            binding.tvChannelTestResult.text = "Checking channel..."
            lifecycleScope.launch {
                when (val result = viewModel.testChannel(token, channel)) {
                    is ApiResult.Success -> {
                        binding.tvChannelTestResult.setTextColor(resources.getColor(com.tastydrive.app.R.color.status_green, null))
                        binding.tvChannelTestResult.text = "✔ ${result.data}"
                    }
                    is ApiResult.Error -> {
                        binding.tvChannelTestResult.setTextColor(resources.getColor(com.tastydrive.app.R.color.status_red, null))
                        binding.tvChannelTestResult.text = "✖ ${result.message}"
                    }
                }
            }
        }

        // Save Settings Button
        binding.btnSaveCredentials.setOnClickListener {
            val token = binding.etBotToken.text?.toString()?.trim() ?: ""
            val channel = binding.etChannelId.text?.toString()?.trim() ?: ""

            viewModel.prefs.botToken = token
            viewModel.prefs.channelId = channel

            Toast.makeText(requireContext(), "Settings saved successfully! 🎉", Toast.LENGTH_SHORT).show()
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

