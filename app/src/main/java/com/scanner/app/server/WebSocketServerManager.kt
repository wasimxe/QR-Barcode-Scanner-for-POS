package com.scanner.app.server

import android.util.Log
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import java.net.InetSocketAddress

/**
 * WebSocket server for broadcasting scanned barcodes to web browsers.
 * Runs on local WiFi network - no internet required.
 */
class WebSocketServerManager(
    port: Int = 8080,
    private val onClientConnected: (Int) -> Unit = {},
    private val onClientDisconnected: (Int) -> Unit = {},
    private val onError: (Exception) -> Unit = {}
) {
    private var server: BarcodeWebSocketServer? = null
    private val TAG = "WebSocketServer"

    val isRunning: Boolean
        get() = server != null

    val connectedClients: Int
        get() = server?.connections?.size ?: 0

    /**
     * Start WebSocket server
     */
    fun start(port: Int = 8080): Boolean {
        return try {
            if (server != null) {
                Log.w(TAG, "Server already running")
                return false
            }

            server = BarcodeWebSocketServer(port, onClientConnected, onClientDisconnected, onError)
            server?.start()
            Log.i(TAG, "WebSocket server started on port $port")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            onError(e)
            false
        }
    }

    /**
     * Stop WebSocket server
     */
    fun stop() {
        try {
            server?.stop(1000) // Stop with 1 second timeout
            server = null
            Log.i(TAG, "WebSocket server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
            server = null // Force cleanup even on error
        }
    }

    /**
     * Broadcast barcode to all connected clients
     */
    fun broadcastBarcode(barcode: String) {
        try {
            val clientCount = connectedClients
            if (clientCount == 0) {
                Log.w(TAG, "No clients connected to broadcast to")
                return
            }

            server?.broadcast(barcode)
            Log.d(TAG, "Broadcasted barcode to $clientCount client(s): $barcode")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting barcode", e)
            onError(e)
        }
    }

    /**
     * Internal WebSocket server implementation
     */
    private class BarcodeWebSocketServer(
        port: Int,
        private val onClientConnected: (Int) -> Unit,
        private val onClientDisconnected: (Int) -> Unit,
        private val onError: (Exception) -> Unit
    ) : WebSocketServer(InetSocketAddress(port)) {

        init {
            // Enable SO_REUSEADDR to allow immediate port rebinding
            isReuseAddr = true
            connectionLostTimeout = 10 // 10 seconds timeout for lost connections
        }

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            Log.i("WebSocket", "Client connected: ${conn.remoteSocketAddress}")
            onClientConnected(connections.size)
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) {
            Log.i("WebSocket", "Client disconnected: ${conn.remoteSocketAddress}")
            onClientDisconnected(connections.size)
        }

        override fun onMessage(conn: WebSocket, message: String) {
            // We don't expect messages from browser, just ignore
            Log.d("WebSocket", "Received message from client: $message")
        }

        override fun onError(conn: WebSocket?, ex: Exception) {
            Log.e("WebSocket", "Server error", ex)
            onError(ex)
        }

        override fun onStart() {
            Log.i("WebSocket", "Server started successfully")
        }
    }
}
