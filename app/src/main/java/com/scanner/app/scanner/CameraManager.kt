package com.scanner.app.scanner

import android.content.Context
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.scanner.app.data.ScanResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Optimized camera manager for high-speed barcode scanning
 * with battery efficiency in mind.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val onBarcodeDetected: (ScanResult) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var barcodeAnalyzer: BarcodeAnalyzer? = null

    // Dedicated executor for fast image analysis
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    /**
     * Start camera with optimized settings for speed and battery
     */
    suspend fun startCamera() {
        try {
            cameraProvider = getCameraProvider()

            // Unbind any existing use cases
            cameraProvider?.unbindAll()

            // Configure preview for smooth rendering
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            // Configure image analysis for maximum speed and full-screen scanning
            imageAnalysis = ImageAnalysis.Builder()
                // Higher resolution for better full-screen detection
                .setTargetResolution(Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            // Create barcode analyzer
            barcodeAnalyzer = BarcodeAnalyzer(
                onBarcodeDetected = onBarcodeDetected,
                onError = onError
            )

            // Use dedicated executor for faster processing (not main thread)
            imageAnalysis?.setAnalyzer(
                analysisExecutor,
                barcodeAnalyzer!!
            )

            // Select back camera
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            // Bind use cases to lifecycle
            camera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Toggle flashlight for low-light scanning
     */
    fun toggleTorch(): Boolean {
        return try {
            val currentState = camera?.cameraInfo?.torchState?.value == TorchState.ON
            camera?.cameraControl?.enableTorch(!currentState)
            !currentState
        } catch (e: Exception) {
            onError(e)
            false
        }
    }

    /**
     * Get torch state
     */
    fun isTorchOn(): Boolean {
        return camera?.cameraInfo?.torchState?.value == TorchState.ON
    }

    /**
     * Pause barcode scanning
     */
    fun pauseScanning() {
        barcodeAnalyzer?.pause()
    }

    /**
     * Resume barcode scanning
     */
    fun resumeScanning() {
        barcodeAnalyzer?.resume()
    }

    /**
     * Stop camera and release resources for battery optimization
     */
    fun stopCamera() {
        try {
            barcodeAnalyzer?.release()
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            camera = null
            imageAnalysis = null
            barcodeAnalyzer = null
        } catch (e: Exception) {
            onError(e)
        }
    }

    /**
     * Get camera provider with proper coroutine handling
     */
    private suspend fun getCameraProvider(): ProcessCameraProvider =
        suspendCancellableCoroutine { continuation ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    continuation.resume(future.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))

            continuation.invokeOnCancellation {
                // Clean up if coroutine is cancelled
            }
        }
}
