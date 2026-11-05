package com.smartsplit.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.ContextCompat
import com.smartsplit.app.data.BillItem
import com.smartsplit.app.service.ReceiptOcrAnalyzer
import com.smartsplit.app.service.ReceiptAiHelper
import com.smartsplit.app.ui.theme.ErrorRed
import com.smartsplit.app.ui.theme.PrimaryTeal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReceiptBillDraftDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, List<BillItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val billTitle = remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<BillItem>() }
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isAnalyzing = remember { mutableStateOf(false) }
    val pendingCaptureUri = remember { mutableStateOf<Uri?>(null) }

    fun analyzeUri(uri: Uri) {
        scope.launch {
            isAnalyzing.value = true
            errorMessage.value = null
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    ReceiptOcrAnalyzer.loadBitmap(context, uri)
                }
                val aiResult = ReceiptAiHelper.scanReceiptToDraftItems(bitmap)
                val scannedItems = aiResult.getOrNull()

                if (!scannedItems.isNullOrEmpty()) {
                    billTitle.value = "Hóa đơn quét ảnh"
                    items.clear()
                    items.addAll(scannedItems.map { item ->
                        BillItem(name = item.name, quantity = item.quantity, unitPrice = item.price)
                    })
                } else {
                    val ocrResult = withContext(Dispatchers.IO) {
                        ReceiptOcrAnalyzer.analyzeBitmap(bitmap)
                    }
                    if (ocrResult.items.isNotEmpty()) {
                        billTitle.value = ocrResult.title.ifBlank { "Hóa đơn quét ảnh" }
                        items.clear()
                        items.addAll(ocrResult.items)
                        Toast.makeText(
                            context,
                            "Đã quét món bằng bộ nhận diện OCR trên máy",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (aiResult.isFailure) {
                        val error = aiResult.exceptionOrNull()
                        errorMessage.value =
                            "Không phân tích được ảnh: ${error?.localizedMessage ?: "lỗi không xác định"}"
                    } else {
                        errorMessage.value = "Gemini không tìm thấy món có giá đọc được trong ảnh."
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                errorMessage.value =
                    "Không phân tích được ảnh: ${error.localizedMessage ?: "lỗi không xác định"}"
            } finally {
                isAnalyzing.value = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) analyzeUri(uri)
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri.value
        if (success && uri != null) {
            analyzeUri(uri)
        } else if (!success) {
            errorMessage.value = "Chưa nhận được ảnh. Hãy chụp lại hoặc chọn ảnh có sẵn."
        }
    }

    fun startCamera() {
        try {
            val uri = ReceiptOcrAnalyzer.createCaptureUri(context)
            pendingCaptureUri.value = uri
            cameraLauncher.launch(uri)
        } catch (error: Exception) {
            errorMessage.value =
                "Không mở được camera: ${error.localizedMessage ?: "lỗi không xác định"}"
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else errorMessage.value = "Cần quyền Camera để chụp hóa đơn."
    }

    AlertDialog(
        onDismissRequest = { if (!isAnalyzing.value) onDismiss() },
        title = { Text("Chụp ảnh OCR hóa đơn", fontWeight = FontWeight.Bold) },
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
                    "Chụp hoặc chọn ảnh để Gemini Vision tách món. Kiểm tra bản nháp trước khi thêm vào bill.",
                    fontSize = 12.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val hasCameraPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA
                            ) == PackageManager.PERMISSION_GRANTED
                            if (hasCameraPermission) startCamera()
                            else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        enabled = !isAnalyzing.value,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Text(" Chụp ảnh", fontSize = 12.sp)
                    }
                    OutlinedButton(
                        onClick = { galleryLauncher.launch("image/*") },
                        enabled = !isAnalyzing.value,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Text(" Chọn ảnh", fontSize = 12.sp)
                    }
                }
                if (isAnalyzing.value) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text("Đang đọc hóa đơn...")
                    }
                }
                errorMessage.value?.let {
                    Text(it, color = ErrorRed, fontSize = 12.sp)
                }
                if (items.isNotEmpty()) {
                    OutlinedTextField(
                        value = billTitle.value,
                        onValueChange = { billTitle.value = it },
                        label = { Text("Tên hóa đơn") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                items.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = item.name,
                            onValueChange = { value -> items[index] = item.copy(name = value) },
                            label = { Text("Tên món") },
                            modifier = Modifier.weight(2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = item.quantity.toString(),
                            onValueChange = { value ->
                                items[index] = item.copy(
                                    quantity = value.filter(Char::isDigit).toIntOrNull() ?: 0
                                )
                            },
                            label = { Text("SL") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = item.unitPrice.takeIf { it > 0L }?.toString().orEmpty(),
                            onValueChange = { value ->
                                items[index] = item.copy(
                                    unitPrice = value.filter(Char::isDigit).toLongOrNull() ?: 0L
                                )
                            },
                            label = { Text("Đơn giá (VNĐ)") },
                            modifier = Modifier.weight(1f),
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
                onClick = { onConfirm(billTitle.value, items.toList()) },
                enabled = !isAnalyzing.value && items.isNotEmpty() && items.all {
                    it.name.isNotBlank() && it.quantity in 1..99 && it.unitPrice > 0L
                }
            ) {
                Text("Đưa vào bản nháp")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isAnalyzing.value
            ) {
                Text("Hủy")
            }
        }
    )
}
