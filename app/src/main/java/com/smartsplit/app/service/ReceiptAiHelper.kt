package com.smartsplit.app.service

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.smartsplit.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class DraftScannedItem(
    val name: String,
    val quantity: Int,
    val price: Long
)

object ReceiptAiHelper {
    private const val MODEL_NAME = "gemini-2.5-flash"

    private val visionModel: GenerativeModel by lazy {
        val apiKey = BuildConfig.GEMINI_API_KEY
        require(apiKey.isNotBlank()) {
            "Chưa cấu hình GEMINI_API_KEY trong local.properties."
        }
        GenerativeModel(
            modelName = MODEL_NAME,
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.1f
                responseMimeType = "application/json"
            }
        )
    }

    suspend fun parseVoiceTextToDraftItems(
        spokenText: String
    ): Result<List<DraftScannedItem>> = withContext(Dispatchers.IO) {
        if (spokenText.isBlank()) return@withContext Result.success(emptyList())

        try {
            val prompt = """
                Bạn là trợ lý bóc tách thông tin hóa đơn từ câu nói tiếng Việt.
                Tách câu sau thành từng món hàng, đồ ăn hoặc dịch vụ:
                "$spokenText"

                Quy tắc:
                1. Quy đổi k, nghìn, ngàn, cành thành 1.000 VNĐ; lít thành 100.000 VNĐ;
                   củ/triệu thành 1.000.000 VNĐ. "Củ rưỡi" là 1.500.000 VNĐ.
                2. Trả về đơn giá một đơn vị. Nếu câu nói là tổng tiền cho số lượng,
                   chia tổng tiền cho số lượng để ra đơn giá.
                3. Nếu không nói số lượng, đặt quantity = 1.
                4. Bỏ qua lời chào, người ứng tiền, thời gian và thông tin không phải món.
                5. Chỉ trả về mảng JSON, mỗi phần tử có đúng các trường:
                   {"name":"Tên món","quantity":1,"price":10000}
                Giá price là số nguyên VNĐ.
            """.trimIndent()
            val response = visionModel.generateContent(prompt)
            Result.success(parseJsonToDraftItems(requireNotNull(response.text) {
                "Gemini không trả về nội dung."
            }))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun scanReceiptToDraftItems(
        bitmap: Bitmap
    ): Result<List<DraftScannedItem>> = withContext(Dispatchers.IO) {
        try {
            val prompt = """
                Bạn là hệ thống đọc hóa đơn tính tiền tại Việt Nam.
                Đọc từng dòng hóa đơn trong ảnh, kể cả ảnh nghiêng hoặc chụp chéo.
                Chỉ lấy món ăn, đồ uống và hàng hóa. Bỏ qua tổng cộng, thành tiền,
                giảm giá, thuế, tiền khách đưa/thừa, địa chỉ, số điện thoại và lời cảm ơn.
                Mỗi món chỉ trả về đúng ba trường:
                - name: tên món.
                - quantity: số lượng, mặc định 1 nếu không rõ.
                - price: đơn giá một đơn vị, là số nguyên VNĐ.
                Nếu ảnh chỉ ghi số lượng và tổng dòng tiền, chia tổng cho số lượng
                để lấy đơn giá. Không tự suy diễn giá khi ảnh không đọc rõ.
                Trả về duy nhất mảng JSON, ví dụ:
                [{"name":"Bò mỹ nướng","quantity":2,"price":120000}]
            """.trimIndent()
            val response = visionModel.generateContent(
                content {
                    image(bitmap.toGeminiSize())
                    text(prompt)
                }
            )
            Result.success(parseJsonToDraftItems(requireNotNull(response.text) {
                "Gemini không trả về nội dung."
            }))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun Bitmap.toGeminiSize(maxDimension: Int = 1024): Bitmap {
        val longestSide = maxOf(width, height)
        if (longestSide <= maxDimension) return this
        val scale = maxDimension.toFloat() / longestSide
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
    }

    internal fun parseJsonToDraftItems(json: String): List<DraftScannedItem> {
        val cleanJson = json
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val jsonArray = JSONArray(cleanJson)
        return buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                val name = item.optString("name").trim()
                val quantity = item.optInt("quantity", 1)
                val price = item.optLong("price", 0L)
                if (name.isNotEmpty() && quantity in 1..99 && price > 0L) {
                    add(DraftScannedItem(name, quantity, price))
                }
            }
        }
    }
}
