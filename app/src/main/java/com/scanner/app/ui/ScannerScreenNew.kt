package com.scanner.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.ToneGenerator
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.scanner.app.data.AppSettings
import com.scanner.app.data.ScanMode
import com.scanner.app.data.ScanResult
import com.scanner.app.scanner.CameraManager
import com.scanner.app.server.WebSocketServerManager
import com.scanner.app.ui.theme.Accent
import com.scanner.app.ui.theme.Background
import com.scanner.app.ui.theme.Surface
import com.scanner.app.utils.NetworkUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreenNew() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }

    // State
    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }
    var showModeSelector by remember { mutableStateOf(false) }

    // Settings state
    val currentMode by settings.scanMode.collectAsState(initial = ScanMode.WIFI_WEBSOCKET)
    val serverPort by settings.serverPort.collectAsState(initial = 8080)

    // WebSocket server
    var wsServer by remember { mutableStateOf<WebSocketServerManager?>(null) }
    var connectedClients by remember { mutableStateOf(0) }
    var serverUrl by remember { mutableStateOf("") }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val permissionGranted = cameraPermissionState.status == com.google.accompanist.permissions.PermissionStatus.Granted

    // Initialize WebSocket server when in WiFi mode
    LaunchedEffect(currentMode, serverPort) {
        if (currentMode == ScanMode.WIFI_WEBSOCKET) {
            // Stop any existing server first
            wsServer?.stop()

            wsServer = WebSocketServerManager(
                port = serverPort,
                onClientConnected = { count ->
                    connectedClients = count
                    // Use Handler to show Toast on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Client connected ($count)", Toast.LENGTH_SHORT).show()
                    }
                },
                onClientDisconnected = { count ->
                    connectedClients = count
                },
                onError = { error ->
                    // Use Handler to show Toast on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "Server error: ${error.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            if (wsServer?.start(serverPort) == true) {
                serverUrl = NetworkUtils.getWebSocketUrl(context, serverPort)
                scope.launch {
                    settings.setLastServerUrl(serverUrl)
                }
            }
        } else {
            wsServer?.stop()
            wsServer = null
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.stopCamera()
            wsServer?.stop()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (permissionGranted) {
            // Camera Preview
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER

                        cameraManager = CameraManager(
                            context = ctx,
                            lifecycleOwner = lifecycleOwner,
                            previewView = this,
                            onBarcodeDetected = { result ->
                                scanResult = result
                                showResult = true

                                // Handle barcode based on mode
                                when (currentMode) {
                                    ScanMode.WIFI_WEBSOCKET -> {
                                        wsServer?.broadcastBarcode(result.rawValue)
                                    }
                                    ScanMode.BLUETOOTH_HID -> {
                                        // TODO: Implement Bluetooth HID
                                        Toast.makeText(ctx, "Bluetooth mode coming soon", Toast.LENGTH_SHORT).show()
                                    }
                                    ScanMode.COPY_ONLY -> {
                                        copyToClipboard(ctx, result.rawValue)
                                    }
                                }

                                // Pause scanning
                                cameraManager?.pauseScanning()

                                // Play success sound
                                try {
                                    val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
                                    toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                                    // Release after a short delay
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        toneGenerator.release()
                                    }, 200)
                                } catch (e: Exception) {
                                    // Ignore sound errors
                                }

                                // Vibrate on successful scan
                                try {
                                    val vibrator = ctx.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        vibrator.vibrate(
                                            android.os.VibrationEffect.createOneShot(
                                                200,
                                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
                                            )
                                        )
                                    } else {
                                        @Suppress("DEPRECATION")
                                        vibrator.vibrate(200)
                                    }
                                } catch (e: Exception) {
                                    // Ignore vibration errors
                                }
                            },
                            onError = { exception ->
                                Toast.makeText(ctx, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                        )

                        scope.launch {
                            cameraManager?.startCamera()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Scanning overlay
            if (!showResult && !showModeSelector) {
                ScanningOverlay(
                    isTorchOn = isTorchOn,
                    onTorchToggle = {
                        isTorchOn = cameraManager?.toggleTorch() ?: false
                    }
                )
            }

            // Top bar with mode and status
            ServerStatusBar(
                mode = currentMode,
                serverUrl = serverUrl,
                connectedClients = connectedClients,
                onModeClick = { showModeSelector = true }
            )

            // Mode selector bottom sheet
            if (showModeSelector) {
                ModeSelectorSheet(
                    currentMode = currentMode,
                    onModeSelected = { mode ->
                        scope.launch {
                            settings.setScanMode(mode)
                        }
                        showModeSelector = false
                    },
                    onDismiss = { showModeSelector = false }
                )
            }

            // Result Card
            AnimatedVisibility(
                visible = showResult,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(300)
                ) + fadeOut()
            ) {
                scanResult?.let { result ->
                    ResultCard(
                        result = result,
                        onDismiss = {
                            showResult = false
                            scanResult = null
                            cameraManager?.resumeScanning()
                        },
                        onCopy = {
                            copyToClipboard(context, result.rawValue)
                        },
                        onShare = {
                            shareText(context, result.rawValue)
                        },
                        onOpenUrl = {
                            if (result.rawValue.startsWith("http://") ||
                                result.rawValue.startsWith("https://")) {
                                openUrl(context, result.rawValue)
                            }
                        }
                    )
                }
            }
        } else {
            // Permission request UI
            PermissionRequestScreen(
                onRequestPermission = {
                    cameraPermissionState.launchPermissionRequest()
                }
            )
        }
    }
}

@Composable
fun ServerStatusBar(
    mode: ScanMode,
    serverUrl: String,
    connectedClients: Int,
    onModeClick: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        Column(
            modifier = Modifier
                .align(if (isLandscape) Alignment.TopStart else Alignment.TopCenter)
                .padding(
                    top = if (isLandscape) 16.dp else 48.dp,
                    start = if (isLandscape) 16.dp else 0.dp,
                    end = if (isLandscape) 0.dp else 0.dp
                )
        ) {
            // Mode selector button
            Surface(
                modifier = Modifier
                    .clickable(onClick = onModeClick)
                    .widthIn(min = 200.dp),
                shape = RoundedCornerShape(12.dp),
                color = Surface.copy(alpha = 0.95f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (mode) {
                            ScanMode.WIFI_WEBSOCKET -> Icons.Default.Wifi
                            ScanMode.BLUETOOTH_HID -> Icons.Default.Bluetooth
                            ScanMode.COPY_ONLY -> Icons.Default.ContentCopy
                        },
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = mode.getDisplayName(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Server status (only for WiFi mode)
            if (mode == ScanMode.WIFI_WEBSOCKET && serverUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (connectedClients > 0) Accent.copy(alpha = 0.9f) else Surface.copy(alpha = 0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = serverUrl,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (connectedClients > 0) "$connectedClients client(s) connected" else "Waiting for connection...",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectorSheet(
    currentMode: ScanMode,
    onModeSelected: (ScanMode) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Select Scan Mode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            ScanMode.values().forEach { mode ->
                ModeOptionCard(
                    mode = mode,
                    isSelected = mode == currentMode,
                    onClick = { onModeSelected(mode) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ModeOptionCard(
    mode: ScanMode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Accent.copy(alpha = 0.2f) else Background,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, Accent) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = when (mode) {
                    ScanMode.WIFI_WEBSOCKET -> Icons.Default.Wifi
                    ScanMode.BLUETOOTH_HID -> Icons.Default.Bluetooth
                    ScanMode.COPY_ONLY -> Icons.Default.ContentCopy
                },
                contentDescription = null,
                tint = if (isSelected) Accent else Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = mode.getDisplayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Accent else Color.White
                )
                Text(
                    text = mode.getDescription(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// Keep existing composables (ScanningOverlay, ScanningFrameSquare, etc.)
// Copy from original file...

// Helper functions
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Scan Result", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to open URL", Toast.LENGTH_SHORT).show()
    }
}
