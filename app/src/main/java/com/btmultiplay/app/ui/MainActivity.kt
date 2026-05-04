package com.btmultiplay.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.btmultiplay.app.R
import com.btmultiplay.app.databinding.ActivityMainBinding
import com.btmultiplay.app.service.BluetoothConnectionService
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var appBarConfig: AppBarConfiguration
    private val viewModel: MainViewModel by viewModels()

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onPermissionsGranted()
        } else {
            showSnack("Bluetooth is required for this app")
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            viewModel.onPermissionsGranted()
            checkBluetoothEnabled()
        } else {
            showPermissionRationale()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHost.navController

        appBarConfig = AppBarConfiguration(
            setOf(R.id.scanFragment, R.id.connectedFragment, R.id.settingsFragment)
        )
        setupActionBarWithNavController(navController, appBarConfig)
        binding.bottomNav.setupWithNavController(navController)

        viewModel.initialize()
        observeUiState()
        startBtService()
        requestPermissions()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.updateResult?.let { result ->
                        if (result.appUpdateAvailable) {
                            showUpdateDialog(result.latestVersion ?: "")
                        } else if (result.success) {
                            showSnack("Capability data updated (${result.devicesUpdated} devices)")
                        }
                        viewModel.dismissUpdateResult()
                    }
                }
            }
        }
    }

    private fun startBtService() {
        val intent = Intent(this, BluetoothConnectionService::class.java)
        startForegroundService(intent)
    }

    private fun requestPermissions() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }.toTypedArray()

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            viewModel.onPermissionsGranted()
            checkBluetoothEnabled()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun checkBluetoothEnabled() {
        val adapter = viewModel.scanner.bluetoothAdapter
        if (adapter == null) {
            AlertDialog.Builder(this)
                .setTitle("Bluetooth Not Available")
                .setMessage("This device does not have Bluetooth hardware.")
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
            return
        }
        if (!adapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage(
                "BT MultiPlay needs Bluetooth and Location permissions to scan for and connect to " +
                "speakers. Without these, the app cannot function.\n\nPlease grant permissions in Settings."
            )
            .setPositiveButton("Grant") { _, _ -> requestPermissions() }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun showUpdateDialog(version: String) {
        AlertDialog.Builder(this)
            .setTitle("App Update Available")
            .setMessage("Version $version is available. Download and install the latest APK for new features and bug fixes.")
            .setPositiveButton("Later", null)
            .show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh_update -> {
                viewModel.checkForUpdates()
                showSnack("Checking for updates…")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp(appBarConfig) || super.onSupportNavigateUp()

    private fun showSnack(msg: String) {
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
    }
}
