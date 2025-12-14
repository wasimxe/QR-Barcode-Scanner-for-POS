package com.scanner.app.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScanResult(
    val rawValue: String,
    val format: BarcodeFormat,
    val timestamp: Long = System.currentTimeMillis()
) : Parcelable

enum class BarcodeFormat {
    QR_CODE,
    CODE_128,
    CODE_39,
    CODE_93,
    CODABAR,
    EAN_8,
    EAN_13,
    ITF,
    UPC_A,
    UPC_E,
    PDF417,
    AZTEC,
    DATA_MATRIX,
    UNKNOWN;

    companion object {
        fun fromMLKitFormat(format: Int): BarcodeFormat {
            return when (format) {
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE -> QR_CODE
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_128 -> CODE_128
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_39 -> CODE_39
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODE_93 -> CODE_93
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_CODABAR -> CODABAR
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_8 -> EAN_8
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_EAN_13 -> EAN_13
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_ITF -> ITF
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_A -> UPC_A
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_UPC_E -> UPC_E
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_PDF417 -> PDF417
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_AZTEC -> AZTEC
                com.google.mlkit.vision.barcode.common.Barcode.FORMAT_DATA_MATRIX -> DATA_MATRIX
                else -> UNKNOWN
            }
        }
    }
}
