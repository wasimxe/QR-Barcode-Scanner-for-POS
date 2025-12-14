package com.scanner.app.scanner

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.scanner.app.data.BarcodeFormat
import com.scanner.app.data.ScanResult

/**
 * High-performance barcode analyzer optimized for speed and battery life.
 * Uses ML Kit for instant on-device scanning with minimal latency.
 * Optimized for both QR codes and barcodes with parallel processing.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (ScanResult) -> Unit,
    private val onError: (Exception) -> Unit = {}
) : ImageAnalysis.Analyzer {

    // ML Kit barcode scanner - optimized for maximum speed
    // Enable all common formats for instant detection
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_CODABAR,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_PDF417,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    // Control scanning state
    private var isScanning = true

    // Minimal throttle for instant scanning feel
    private var lastScanTime = 0L
    private var lastScannedValue = ""
    private val scanThrottleMs = 300L // 0.3 seconds for instant response

    fun pause() {
        isScanning = false
    }

    fun resume() {
        isScanning = true
        lastScannedValue = ""
        lastScanTime = 0L
    }

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        // If scanning is paused, just close and return
        if (!isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image

        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            // Process with ML Kit - extremely fast on-device processing
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (isScanning) { // Double-check before handling
                        handleBarcodes(barcodes)
                    }
                }
                .addOnFailureListener { exception ->
                    onError(exception)
                }
                .addOnCompleteListener {
                    // CRITICAL: Close image to prevent memory leaks and maintain performance
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun handleBarcodes(barcodes: List<Barcode>) {
        val currentTime = System.currentTimeMillis()

        for (barcode in barcodes) {
            val rawValue = barcode.rawValue ?: continue

            // Throttle duplicate scans for better UX and battery life
            if (rawValue == lastScannedValue &&
                currentTime - lastScanTime < scanThrottleMs) {
                continue
            }

            lastScannedValue = rawValue
            lastScanTime = currentTime

            val scanResult = ScanResult(
                rawValue = rawValue,
                format = BarcodeFormat.fromMLKitFormat(barcode.format),
                timestamp = currentTime
            )

            onBarcodeDetected(scanResult)
            break // Only process first detected code for better performance
        }
    }

    /**
     * Clean up resources when analyzer is no longer needed
     */
    fun release() {
        scanner.close()
    }
}
