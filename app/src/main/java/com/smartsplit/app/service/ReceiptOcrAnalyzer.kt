package com.smartsplit.app.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartsplit.app.data.BillItem
import kotlinx.coroutines.tasks.await
import java.io.File

data class ReceiptOcrResult(
    val title: String,
    val items: List<BillItem>,
    val rawText: String
)

object ReceiptOcrAnalyzer {

    fun createCaptureUri(context: Context): Uri {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        val file = File(dir, "ocr_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun loadBitmap(context: Context, uri: Uri): Bitmap {
        val decoded = if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val maxSide = 1920
                val width = info.size.width
                val height = info.size.height
                val longest = maxOf(width, height).toFloat()
                if (longest > maxSide) {
                    val scale = longest / maxSide
                    decoder.setTargetSize((width / scale).toInt(), (height / scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Không đọc được ảnh" }
                requireNotNull(BitmapFactory.decodeStream(input)) { "Không giải mã được ảnh" }
            }
        }
        val rotated = applyExifRotation(context, uri, decoded)
        return if (rotated.config == Bitmap.Config.ARGB_8888) {
            rotated
        } else {
            rotated.copy(Bitmap.Config.ARGB_8888, false) ?: rotated
        }
    }

    suspend fun analyze(context: Context, uri: Uri): ReceiptOcrResult {
        return analyzeBitmap(loadBitmap(context, uri))
    }

    suspend fun analyzeBitmap(bitmap: Bitmap): ReceiptOcrResult {
        val software = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val visionText = recognizer.process(InputImage.fromBitmap(software, 0)).await()
            val lines = visionText.textBlocks.flatMap { block -> block.lines.map { it.text.trim() } }
                .filter { it.isNotBlank() }
            return parse(visionText.text, lines)
        } finally {
            recognizer.close()
        }
    }

    internal fun parse(fullText: String, lines: List<String>): ReceiptOcrResult {
        val title = lines.firstOrNull { line ->
            line.any { it.isLetter() } && extractPrices(line).isEmpty() && !isNoiseLine(line)
        } ?: "Hóa đơn"

        val items = mutableListOf<BillItem>()
        for (line in lines) {
            if (isNoiseLine(line) || isTotalLine(line)) continue
            parseItemLine(line)?.let { items += it }
        }

        val uniqueItems = items
            .distinctBy { it.name.lowercase() to it.unitPrice to it.quantity }
            .filter { it.unitPrice in 1_000L..50_000_000L }

        if (uniqueItems.isNotEmpty()) {
            return ReceiptOcrResult(title = title.take(60), items = uniqueItems, rawText = fullText)
        }

        val totalFromLabel = lines.firstOrNull { isTotalLine(it) }?.let { extractPrices(it).lastOrNull() }
        val fallbackTotal = totalFromLabel ?: extractPrices(fullText).maxOrNull()
        val fallbackItems = if (fallbackTotal != null) {
            listOf(BillItem(name = title, quantity = 1, unitPrice = fallbackTotal))
        } else {
            emptyList()
        }
        return ReceiptOcrResult(title = title.take(60), items = fallbackItems, rawText = fullText)
    }

    private fun parseItemLine(line: String): BillItem? {
        val qtyMatch = QTY_REGEX.find(line)
        val prices = extractPrices(line)
        if (prices.isEmpty()) return null

        val amount = prices.last()
        val quantity: Int
        val unitPrice: Long
        val nameSource: String

        if (qtyMatch != null) {
            quantity = qtyMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 99) ?: 1
            val parsedUnit = parseVnd(qtyMatch.groupValues[2])
            unitPrice = parsedUnit ?: (amount / quantity)
            nameSource = line.replace(qtyMatch.value, " ")
        } else {
            quantity = 1
            unitPrice = amount
            nameSource = line
        }

        val name = PRICE_REGEX.replace(nameSource, " ")
            .replace(Regex("[đ₫]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '-', ':', '.', ',')
        if (name.length < 2) return null
        if (name.none { it.isLetter() }) return null
        return BillItem(name = name, quantity = quantity, unitPrice = unitPrice)
    }

    private fun extractPrices(text: String): List<Long> {
        return PRICE_REGEX.findAll(text).mapNotNull { parseVnd(it.value) }.toList()
    }

    private fun parseVnd(raw: String): Long? {
        val compact = raw.lowercase()
            .replace("vnd", "")
            .replace("vnđ", "")
            .replace("đ", "")
            .replace("₫", "")
            .replace(" ", "")
            .trim()
        val digits = when {
            compact.contains('.') && compact.substringAfterLast('.').length == 3 ->
                compact.replace(".", "").replace(",", "")
            compact.contains(',') && compact.substringAfterLast(',').length == 3 ->
                compact.replace(",", "").replace(".", "")
            else -> compact.filter { it.isDigit() }
        }
        val value = digits.toLongOrNull() ?: return null
        if (value in 1900L..2100L) return null
        return value.takeIf { it in 1_000L..50_000_000L }
    }

    private fun isTotalLine(line: String): Boolean {
        val lower = line.lowercase()
        return listOf("tổng", "tong cong", "total", "thành tiền", "thanh tien", "cần thanh toán")
            .any { lower.contains(it) }
    }

    private fun isNoiseLine(line: String): Boolean {
        val lower = line.lowercase()
        return NOISE_KEYWORDS.any { lower.contains(it) }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val rotation = try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                when (ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
        }
        if (rotation == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private val PRICE_REGEX = Regex(
        """\d{1,3}(?:[.\s]\d{3})+|\d{4,7}""",
        RegexOption.IGNORE_CASE
    )

    private val QTY_REGEX = Regex(
        """(?:sl[:\s]*)?(\d{1,3})\s*[x×]\s*([\d.\s]+)""",
        RegexOption.IGNORE_CASE
    )

    private val NOISE_KEYWORDS = listOf(
        "mst", "vat", "tax", "wifi", "thank", "cảm ơn", "cam on",
        "ngày", "ngay:", "giờ", "gio:", "tel", "hotline", "sđt", "sdt",
        "thu ngân", "thu ngan", "xin chào", "welcome", "invoice",
        "bill no", "số hđ", "so hd", "tiền mặt", "tien mat",
        "visa", "mastercard", "momo", "chuyển khoản", "chuyen khoan",
        "www.", "http", "qr"
    )
}
