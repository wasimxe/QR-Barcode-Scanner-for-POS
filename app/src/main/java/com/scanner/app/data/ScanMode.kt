package com.scanner.app.data

enum class ScanMode {
    WIFI_WEBSOCKET,    // WebSocket server mode for WiFi connectivity
    BLUETOOTH_HID,     // Bluetooth keyboard emulation mode
    COPY_ONLY;         // Just copy to clipboard (original mode)

    fun getDisplayName(): String = when (this) {
        WIFI_WEBSOCKET -> "WiFi Scanner"
        BLUETOOTH_HID -> "Bluetooth Keyboard"
        COPY_ONLY -> "Copy Only"
    }

    fun getDescription(): String = when (this) {
        WIFI_WEBSOCKET -> "Broadcast scans to browsers via WiFi"
        BLUETOOTH_HID -> "Act as Bluetooth keyboard (coming soon)"
        COPY_ONLY -> "Copy barcode to clipboard"
    }
}
