package com.tastydrive.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.tastydrive.app.R
import com.tastydrive.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkManageStoragePermission()
        viewModel.scanMedia()
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.scanMedia()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupViewPager()
        setupBottomNavigation()
        setupHeader()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateBotStatusPill()
    }

    private fun setupViewPager() {
        binding.viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = 4

            override fun createFragment(position: Int): Fragment {
                return when (position) {
                    0 -> QueueFragment()
                    1 -> GalleryFragment()
                    2 -> LargeFilesFragment()
                    3 -> SettingsFragment()
                    else -> QueueFragment()
                }
            }
        }

        binding.viewPager.offscreenPageLimit = 3

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                binding.bottomNavigation.menu.getItem(position).isChecked = true
            }
        })
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_queue -> {
                    binding.viewPager.currentItem = 0
                    true
                }
                R.id.nav_gallery -> {
                    binding.viewPager.currentItem = 1
                    true
                }
                R.id.nav_large -> {
                    binding.viewPager.currentItem = 2
                    true
                }
                R.id.nav_settings -> {
                    binding.viewPager.currentItem = 3
                    true
                }
                else -> false
            }
        }
    }

    private fun setupHeader() {
        binding.btnQuickRescan.setOnClickListener {
            viewModel.scanMedia()
            Toast.makeText(this, "Scanning all files (APKs, PDFs, Photos, Videos)...", Toast.LENGTH_SHORT).show()
        }

        binding.tvBotStatusPill.setOnClickListener {
            binding.viewPager.currentItem = 3
        }

        updateBotStatusPill()
    }

    private fun updateBotStatusPill() {
        if (viewModel.prefs.isConfigured()) {
            binding.tvBotStatusPill.text = "● Connected"
            binding.tvBotStatusPill.setTextColor(ContextCompat.getColor(this, R.color.status_green))
        } else {
            binding.tvBotStatusPill.text = "● Set Bot ⚙"
            binding.tvBotStatusPill.setTextColor(ContextCompat.getColor(this, R.color.status_orange))
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_VIDEO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // Android 9 to 12
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkManageStoragePermission()
            viewModel.scanMedia()
        }
    }

    private fun checkManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    } catch (ex: Exception) {
                        ex.printStackTrace()
                    }
                }
            }
        }
    }
}
