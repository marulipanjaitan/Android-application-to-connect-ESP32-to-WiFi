package com.example.c43

import android.Manifest
import android.Manifest.permission.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var esp32Device: BluetoothDevice
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var outputStream: OutputStream

    private lateinit var ssidEditText: EditText
    private lateinit var passwordEditText: EditText

    companion object {
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ssidEditText = findViewById(R.id.ssidEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        val saveButton: Button = findViewById(R.id.saveButton)
        val deleteConfigButton: Button = findViewById(R.id.deleteConfigButton)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth tidak didukung pada perangkat ini", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        saveButton.setOnClickListener {
            val ssid = ssidEditText.text.toString()
            val password = passwordEditText.text.toString()

            if (!::esp32Device.isInitialized) {
                Toast.makeText(this@MainActivity, "Tidak ada perangkat ESP32 yang terhubung", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendConfig("SSID:$ssid\n$password\n")
        }

        deleteConfigButton.setOnClickListener {
            if (!::esp32Device.isInitialized) {
                Toast.makeText(this@MainActivity, "Tidak ada perangkat ESP32 yang terhubung", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            sendConfig("DeleteConfig\n")
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasBluetoothPermissions()) {
            connectToESP32()
        } else {
            requestBluetoothPermissions()
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        return (ContextCompat.checkSelfPermission(this, BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED)
    }

    private fun requestBluetoothPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                BLUETOOTH,
                BLUETOOTH_ADMIN,
                BLUETOOTH_CONNECT
            ),
            REQUEST_BLUETOOTH_PERMISSIONS
        )
    }

    private fun connectToESP32() {
        val esp32Address = "E0:5A:1B:77:58:BA" // Ganti dengan alamat MAC ESP32 Anda
        esp32Device = bluetoothAdapter.getRemoteDevice(esp32Address)

        Thread {
            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH
                    ) != PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_ADMIN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(
                            Manifest.permission.BLUETOOTH,
                            Manifest.permission.BLUETOOTH_ADMIN
                        ),
                        REQUEST_BLUETOOTH_PERMISSIONS
                    )
                    return@Thread
                }
                bluetoothSocket = esp32Device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
                bluetoothSocket.connect()
                outputStream = bluetoothSocket.outputStream
                Log.d(TAG, "Terhubung dengan ESP32")
            } catch (e: IOException) {
                Log.e(TAG, "Error connecting to ESP32: ${e.message}")
                // Tangani kesalahan di sini
            }
        }.start()

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                connectToESP32()
            } else {
                Toast.makeText(
                    this,
                    "Izin Bluetooth diperlukan untuk menggunakan fitur ini",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }


    private fun sendConfig(config: String) {
        Thread {
            try {
                outputStream.write(config.toByteArray())
                Log.d(TAG, "Mengirim konfigurasi: $config")

                // Setelah mengirim konfigurasi, hubungkan ke WiFi
                val ssidRegex = Regex("SSID:(.*?)\\n")
                val passwordRegex = Regex("\\n(.*?)\\n")
                val ssidMatch = ssidRegex.find(config)
                val passwordMatch = passwordRegex.find(config)
                if (ssidMatch != null && passwordMatch != null) {
                    val ssid = ssidMatch.groupValues[1]
                    val password = passwordMatch.groupValues[1]
                    connectToWifi(ssid, password)
                } else {
                    Log.e(TAG, "SSID atau password tidak ditemukan dalam konfigurasi")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error sending config: ${e.message}")
            }
        }.start()
    }

    private fun connectToWifi(ssid: String, password: String) {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiConfig = WifiConfiguration()
        wifiConfig.SSID = "\"$ssid\""
        wifiConfig.preSharedKey = "\"$password\""

        val networkId = wifiManager.addNetwork(wifiConfig)
        wifiManager.disconnect()
        wifiManager.enableNetwork(networkId, true)
        wifiManager.reconnect()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::bluetoothSocket.isInitialized) {
                bluetoothSocket.close()
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error closing Bluetooth socket: ${e.message}")
        }
    }
}
