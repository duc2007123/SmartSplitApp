package com.smartsplit.app.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsplit.app.data.BillItem
import com.smartsplit.app.service.ReceiptAiHelper
import com.smartsplit.app.ui.theme.ErrorRed
import com.smartsplit.app.ui.theme.SecondaryIndigo
import kotlinx.coroutines.launch

@Composable
fun VoiceBillDraftDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<BillItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val transcript = remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<BillItem>() }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isAnalyzing = remember { mutableStateOf(false) }
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val recognizedText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                .orEmpty()
            if (recognizedText.isNotBlank()) {
                transcript.value = recognizedText
                items.clear()
                errorMessage.value = null
            } else {
                errorMessage.value = "Không nhận diện được nội dung giọng nói."
            }
        }
    }

    fun analyzeTranscript() {
        scope.launch {
            isAnalyzing.value = true
            errorMessage.value = null
            items.clear()
            try {
                val result = ReceiptAiHelper.parseVoiceTextToDraftItems(transcript.value)
                result.fold(
                    onSuccess = { draftItems ->
                        items.addAll(draftItems.map { item ->
                            BillItem(name = item.name, quantity = item.quantity, unitPrice = item.price)
                        })
                        if (draftItems.isEmpty()) {
                            errorMessage.value = "Gemini chưa tìm thấy món và giá trong câu nói."
                        }
                    },
                    onFailure = { error ->
                        errorMessage.value =
                            "Không thể phân tích câu nói bằng Gemini: ${error.localizedMessage ?: "lỗi không xác định"}"
                    }
                )
            } finally {
                isAnalyzing.value = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhập món bằng giọng nói", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Nhận diện tiếng Việt, kiểm tra câu nói rồi gửi Gemini tách thành món nháp.",
                    fontSize = 12.sp
                )
                Button(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nói tên món và giá tiền")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (error: Exception) {
                            Toast.makeText(
                                context,
                                "Không mở được dịch vụ nhận diện giọng nói: ${error.localizedMessage ?: "lỗi không xác định"}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isAnalyzing.value,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryIndigo)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("  Nhấn để nói tiếng Việt")
                }
                OutlinedTextField(
                    value = transcript.value,
                    onValueChange = {
                        transcript.value = it
                        items.clear()
                        errorMessage.value = null
                    },
                    label = { Text("Câu nói / nội dung bill") },
                    placeholder = { InputPlaceholder("Ví dụ: 2 suất cơm gà 80 nghìn") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = BillInputFieldColors()
                )
                TextButton(
                    onClick = {
                        transcript.value =
                            "Ăn trưa 3 suất cơm sườn 45 nghìn với 3 lon coca 10 nghìn"
                        items.clear()
                        errorMessage.value = null
                    },
                    enabled = !isAnalyzing.value
                ) {
                    Text("Điền câu mẫu")
                }
                TextButton(
                    onClick = ::analyzeTranscript,
                    enabled = transcript.value.isNotBlank() && !isAnalyzing.value
                ) {
                    Text(if (isAnalyzing.value) "Gemini đang phân tích..." else "Gửi câu nói cho Gemini")
                }
                if (isAnalyzing.value) CircularProgressIndicator()
                errorMessage.value?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { value -> items[index] = item.copy(name = value) },
                            label = { Text("Tên món") },
                            modifier = Modifier.weight(2f),
                            colors = BillInputFieldColors(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = item.quantity.toString(),
                            onValueChange = { value ->
                                items[index] = item.copy(quantity = value.toIntOrNull() ?: 0)
                            },
                            label = { Text("SL") },
                            modifier = Modifier.weight(1f),
                            colors = BillInputFieldColors(),
                            singleLine = true
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = item.unitPrice.takeIf { it > 0L }?.toString().orEmpty(),
                            onValueChange = { value ->
                                items[index] = item.copy(unitPrice = value.toLongOrNull() ?: 0L)
                            },
                            label = { Text("Đơn giá (VNĐ)") },
                            modifier = Modifier.weight(1f),
                            colors = BillInputFieldColors(),
                            singleLine = true
                        )
                        IconButton(
                            onClick = { items.removeAt(index) },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa món", tint = ErrorRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(items.toList()) },
                enabled = items.isNotEmpty() && items.all {
                    it.name.isNotBlank() && it.quantity in 1..99 && it.unitPrice > 0L
                }
            ) {
                Text("Đưa vào bản nháp")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
