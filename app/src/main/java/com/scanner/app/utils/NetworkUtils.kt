package com.scanner.app.utils

import android.content.Context
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Network utility functions for getting device IP address.
 */
object NetworkUtils {

    /**
     * Get the device's local IP address on WiFi network.
     * Returns IP in format: "192.168.1.100"
     */
    fun getLocalIpAddress(context: Context): String? {
        return try {
            // Try WiFi first
            val wifiIp = getWifiIpAddress(context)
            if (wifiIp != null) return wifiIp

            // Fallback to all network interfaces
            val interfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in interfaces) {
                val addresses = networkInterface.inetAddresses
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get IP address from WiFi connection
     */
    private fun getWifiIpAddress(context: Context): String? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val wifiInfo = wifiManager?.connectionInfo
            val ipInt = wifiInfo?.ipAddress ?: return null

            // Return null if IP is 0 (0.0.0.0) - WiFi not fully connected
            if (ipInt == 0) return null

            // Convert int to IP string
            String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get WebSocket URL for display
     */
    fun getWebSocketUrl(context: Context, port: Int): String {
        val ip = getLocalIpAddress(context)
        return if (ip != null) {
            "ws://$ip:$port"
        } else {
            "Not connected to WiFi"
        }
    }
}
