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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun ScannerScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var scanResult by remember { mutableStateOf<ScanResult?>(null) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var isTorchOn by remember { mutableStateOf(false) }
    var showResult by remember { mutableStateOf(false) }

    // WebSocket server state
    var wsServer by remember { mutableStateOf<WebSocketServerManager?>(null) }
    var connectedClients by remember { mutableStateOf(0) }
    var serverUrl by remember { mutableStateOf("") }

    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val permissionGranted = cameraPermissionState.status == com.google.accompanist.permissions.PermissionStatus.Granted

    // Initialize WebSocket server
    LaunchedEffect(Unit) {
        // Stop any existing server first
        wsServer?.stop()

        wsServer = WebSocketServerManager(
            port = 8080,
            onClientConnected = { count ->
                connectedClients = count
                // Use Handler to show Toast on main thread
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "Browser connected ($count)", Toast.LENGTH_SHORT).show()
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

        if (wsServer?.start(8080) == true) {
            serverUrl = NetworkUtils.getWebSocketUrl(context, 8080)
        }
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            // Permission granted - camera will start when AndroidView is created
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

                                // Broadcast barcode via WebSocket
                                wsServer?.broadcastBarcode(result.rawValue)

                                // Pause scanning to prevent continuous vibration
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
            if (!showResult) {
                ScanningOverlay(
                    isTorchOn = isTorchOn,
                    onTorchToggle = {
                        isTorchOn = cameraManager?.toggleTorch() ?: false
                    }
                )
            }

            // Server status indicator - moved to bottom
            if (serverUrl.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (connectedClients > 0) Accent.copy(alpha = 0.95f) else Surface.copy(alpha = 0.95f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Wifi,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = serverUrl,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        if (connectedClients > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "✓ $connectedClients browser(s) connected",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
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
                            // Resume scanning when user dismisses result
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

    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.stopCamera()
            wsServer?.stop()
        }
    }
}

@Composable
fun ScanningOverlay(
    isTorchOn: Boolean,
    onTorchToggle: () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight
        val topPadding = if (isLandscape) 16.dp else 40.dp
        val bottomPadding = if (isLandscape) 16.dp else 48.dp

        // Overlapping scanning frames in center
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Both frames overlapped - full screen scans
            ScanningFrameSquare()
            ScanningFrameRectangle()
        }

        // Top instruction
        Text(
            text = "Point camera at QR or Barcode",
            style = if (isLandscape) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleSmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topPadding, start = 16.dp, end = 16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // Torch button
        IconButton(
            onClick = onTorchToggle,
            modifier = Modifier
                .align(if (isLandscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .padding(
                    end = if (isLandscape) 16.dp else 0.dp,
                    bottom = if (isLandscape) 0.dp else bottomPadding
                )
                .size(56.dp)
                .background(
                    color = if (isTorchOn) Accent else Surface,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Torch",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun ScanningFrameSquare() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = -80f,
        targetValue = 80f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline"
    )

    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerLength = 40.dp.toPx()
            val strokeWidth = 4.dp.toPx()

            // Draw all four corners
            drawCorners(Accent, cornerLength, strokeWidth)

            // Horizontal scanning line
            drawLine(
                color = Accent.copy(alpha = 0.8f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2 + animatedOffset),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2 + animatedOffset),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

@Composable
fun ScanningFrameRectangle() {
    val infiniteTransition = rememberInfiniteTransition(label = "scanning")
    val animatedOffset by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanline"
    )

    Box(
        modifier = Modifier
            .width(280.dp)
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cornerLength = 30.dp.toPx()
            val strokeWidth = 4.dp.toPx()

            // Draw all four corners
            drawCorners(Accent, cornerLength, strokeWidth)

            // Horizontal scanning line
            drawLine(
                color = Accent.copy(alpha = 0.8f),
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2 + animatedOffset),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2 + animatedOffset),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}

// Helper function to draw corner markers
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCorners(
    color: androidx.compose.ui.graphics.Color,
    cornerLength: Float,
    strokeWidth: Float
) {
    // Top-left
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(cornerLength, 0f),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(0f, cornerLength),
        strokeWidth = strokeWidth
    )

    // Top-right
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(size.width, 0f),
        end = androidx.compose.ui.geometry.Offset(size.width - cornerLength, 0f),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(size.width, 0f),
        end = androidx.compose.ui.geometry.Offset(size.width, cornerLength),
        strokeWidth = strokeWidth
    )

    // Bottom-left
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, size.height),
        end = androidx.compose.ui.geometry.Offset(cornerLength, size.height),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(0f, size.height),
        end = androidx.compose.ui.geometry.Offset(0f, size.height - cornerLength),
        strokeWidth = strokeWidth
    )

    // Bottom-right
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(size.width, size.height),
        end = androidx.compose.ui.geometry.Offset(size.width - cornerLength, size.height),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = color,
        start = androidx.compose.ui.geometry.Offset(size.width, size.height),
        end = androidx.compose.ui.geometry.Offset(size.width, size.height - cornerLength),
        strokeWidth = strokeWidth
    )
}

@Composable
fun ResultCard(
    result: ScanResult,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onOpenUrl: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        val isLandscape = maxWidth > maxHeight
        val cardPadding = if (isLandscape) 16.dp else 24.dp
        val iconSize = if (isLandscape) 48.dp else 64.dp
        val spacerHeight = if (isLandscape) 8.dp else 16.dp

        Card(
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.9f else 1f)
                .padding(cardPadding)
                .heightIn(max = if (isLandscape) maxHeight * 0.95f else maxHeight),
            shape = RoundedCornerShape(if (isLandscape) 16.dp else 24.dp),
            colors = CardDefaults.cardColors(containerColor = Surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(if (isLandscape) 16.dp else 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success icon
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(iconSize)
                )

                Spacer(modifier = Modifier.height(spacerHeight))

                Text(
                    text = "Scan Successful",
                    style = if (isLandscape) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(spacerHeight / 2))

                Text(
                    text = result.format.name.replace("_", " "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent
                )

                Spacer(modifier = Modifier.height(if (isLandscape) 12.dp else 24.dp))

                // Result value
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Background
                ) {
                    Text(
                        text = result.rawValue,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy")
                    }

                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }

                if (result.rawValue.startsWith("http://") ||
                    result.rawValue.startsWith("https://")) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenUrl,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Accent)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open URL")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Scan Again")
                }
            }
        }
    }
}

@Composable
fun PermissionRequestScreen(onRequestPermission: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "This app needs camera access to scan QR codes and barcodes",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onRequestPermission,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Grant Permission",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

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
