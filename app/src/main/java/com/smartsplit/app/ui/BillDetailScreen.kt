package com.smartsplit.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.smartsplit.app.data.*
import com.smartsplit.app.service.ReceiptOcrAnalyzer
import com.smartsplit.app.ui.theme.*
import java.util.Locale
import kotlin.math.abs

// ==========================================
// [MỤC 1.9.4.1 ➔ 1.9.4.6] MÀN HÌNH CHI TIẾT HÓA ĐƠN (BILL DETAIL SCREEN)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDetailScreen(
    billId: String,
    onBack: (projectId: String) -> Unit
) {
    val context = LocalContext.current
    val allBills by AppRepository.bills.collectAsState()
    val projects by AppRepository.projects.collectAsState()
    val currentUserId by AppRepository.currentUserId.collectAsState()
    var currentUser by remember { mutableStateOf(AppRepository.getCurrentUser()) }

    LaunchedEffect(currentUserId) {
        if (currentUserId != null) {
            val dbUser = AppRepository.getCurrentUserFromDb()
            if (dbUser != null) {
                currentUser = dbUser
            }
        }
    }

    val bill = allBills.firstOrNull { it.id == billId }
    if (bill == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Không tìm thấy hóa đơn!")
        }
        return
    }

    val project = projects.firstOrNull { it.id == bill.projectId }
    val creator = AppRepository.getUserById(bill.creatorId)
    val advanceUser = bill.advanceUserId?.let(AppRepository::getUserById)
    val isCurrentUserCreator = currentUser.id == bill.creatorId
    val creditorUserId = bill.advanceUserId ?: bill.debtObject?.ownerUserId ?: bill.creatorId
    val isCurrentUserCreditor = currentUser.id == creditorUserId

    // Trạng thái của user hiện tại trong bill này
    val myStatus = bill.memberStatuses[currentUser.id]
    val amountToPay = myStatus?.amountOwed ?: 0L

    // Cú pháp quy ước mã chuyển khoản: SM_[MãDựÁn]_[MãBill]_[TênNgườiTrả]
    // [SỬA NHANH KHI VẤN ĐÁP] Cú pháp chuỗi nhận diện VietQR
    val projectCode = project?.inviteCode ?: bill.projectId
    val billCode = if (bill.isMergedParent) "MERGED" else bill.id.take(4).uppercase(Locale.getDefault())
    val payerName = currentUser.name.uppercase(Locale.getDefault())
    val transferCode = "SM_${projectCode}_${billCode}_${payerName}"

    // Dialog state
    var showEditBillDialog by remember { mutableStateOf(false) }
    var showBillEditHistoryDialog by remember { mutableStateOf(false) }
    var showProofImageDialog by remember { mutableStateOf(false) }
    var selectedProofUrl by remember { mutableStateOf<String?>(null) }
    var pendingReceiptCaptureUri by remember { mutableStateOf<String?>(null) }
    val receiptCameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { captured ->
        val uri = pendingReceiptCaptureUri
        pendingReceiptCaptureUri = null
        if (captured && uri != null) {
            AppRepository.updateBill(bill.copy(receiptImageUri = uri))
            Toast.makeText(context, "Đã lưu ảnh hóa đơn gốc.", Toast.LENGTH_SHORT).show()
        } else if (!captured) {
            Toast.makeText(context, "Chưa chụp được ảnh hóa đơn.", Toast.LENGTH_LONG).show()
        }
    }
    val receiptGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                AppRepository.updateBill(bill.copy(receiptImageUri = uri.toString()))
                Toast.makeText(context, "Đã lưu ảnh hóa đơn gốc.", Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, "Không thể cấp quyền đọc ảnh đã chọn.", Toast.LENGTH_LONG).show()
            }
        }
    }
    fun startReceiptCamera() {
        val uri = ReceiptOcrAnalyzer.createCaptureUri(context)
        pendingReceiptCaptureUri = uri.toString()
        receiptCameraLauncher.launch(uri)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startReceiptCamera()
        } else {
            Toast.makeText(context, "Cần quyền camera để chụp ảnh hóa đơn.", Toast.LENGTH_LONG).show()
        }
    }
    var showNb2EditDialog by remember { mutableStateOf(false) }
    var editingItemId by remember { mutableStateOf<String?>(null) }
    var showDeleteBillDialog by remember { mutableStateOf(false) }
    val proofPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                AppRepository.saveMemberPaymentProof(bill.id, currentUser.id, uri.toString())
                Toast.makeText(context, "Đã tải minh chứng. Bạn vẫn cần bấm “Báo đã trả”.", Toast.LENGTH_SHORT).show()
            } catch (_: SecurityException) {
                Toast.makeText(context, "Không thể cấp quyền đọc ảnh minh chứng.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(bill.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = if (bill.isMergedParent) {
                                "Hóa đơn Gộp Xoay Vòng Tiền"
                            } else if (bill.debtObject != null) {
                                "Chế độ ${bill.mode} • ${bill.debtObject.name}"
                            } else {
                                "Chế độ ${bill.mode} • Người ứng: ${advanceUser?.name ?: creator?.name}"
                            },
                            fontSize = 11.sp,
                            color = TextMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onBack(bill.projectId) }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    // ==========================================
                    // Chỉ chủ bill được chỉnh sửa nội dung bill.
                    // ==========================================
                    if (
                        isCurrentUserCreator &&
                        !bill.isMergedParent &&
                        bill.mergedIntoBillId == null
                    ) {
                        IconButton(onClick = { showEditBillDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Sửa Bill", tint = PrimaryTeal)
                        }
                    }
                    IconButton(onClick = { showBillEditHistoryDialog = true }) {
                        Icon(Icons.Default.History, contentDescription = "Lịch sử sửa bill", tint = PrimaryTeal)
                    }
                    if (
                        isCurrentUserCreator &&
                        !bill.isMergedParent &&
                        bill.mergedIntoBillId == null
                    ) {
                        IconButton(onClick = { showDeleteBillDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Xóa bill", tint = ErrorRed)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundSlate),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // ==========================================
            // [MỤC 1.9.4.1] MINH CHỨNG KHI TẠO BILL & THÔNG TIN BILL CON ĐÃ GỘP
            // ==========================================
            item {
                if (bill.isMergedParent) {
                    // Nếu đây là Bill Gộp Mới 1.9.5: Hiển thị các bill con và sơ đồ xoay vòng tiền
                    MergedBillOverviewSection(bill = bill, allBills = allBills)
                } else {
                    // Hóa đơn thông thường: Hiển thị minh chứng hóa đơn gốc
                    ReceiptProofSection(
                        receiptUri = bill.receiptImageUri,
                        canEdit = isCurrentUserCreator && !bill.isMergedParent && bill.mergedIntoBillId == null,
                        onPickFromGallery = { receiptGalleryLauncher.launch(arrayOf("image/*")) },
                        onTakePhoto = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                startReceiptCamera()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onViewImage = { uri ->
                            selectedProofUrl = uri
                            showProofImageDialog = true
                        }
                    )
                }
            }

            // ==========================================
            // [MỤC 1.9.4.2] CHI TIẾT SỐ TIỀN & SỐ LƯỢNG ĐỒ TỪNG NGƯỜI
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chi tiết món & phân chia",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Tổng: ${AppRepository.formatCurrency(bill.totalAmount)}",
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTealDark,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Danh sách món đồ trong bill (Mục 1.9.4.2)
                        if (bill.items.isNotEmpty()) {
                            Text(
                                text = if (bill.mode == InputMode.NB2) "Món đồ (NB2: Tick món bạn đã ăn):" else "Món đồ trong bill:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            bill.items.forEach { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                                    border = CardDefaults.outlinedCardBorder().copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(item.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                Text(
                                                    text = "SL: ${item.quantity} x ${AppRepository.formatCurrency(item.unitPrice)}",
                                                    fontSize = 11.sp,
                                                    color = TextMedium
                                                )
                                            }
                                            Text(
                                                text = AppRepository.formatCurrency(item.totalPrice),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = PrimaryTealDark
                                            )
                                        }

                                        // Chế độ NB2: Hiển thị danh sách thành viên có thể tick món này
                                        if (
                                            bill.mode == InputMode.NB2 &&
                                            bill.nb2Permission.canSelectItem &&
                                            !bill.isMergedParent &&
                                            bill.mergedIntoBillId == null
                                        ) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Ai ăn món này:", fontSize = 11.sp, color = TextMedium, fontWeight = FontWeight.SemiBold)
                                            Spacer(modifier = Modifier.height(4.dp))

                                            project?.memberIds?.forEach { memberId ->
                                                val user = AppRepository.getUserById(memberId)
                                                val isMe = memberId == currentUser.id
                                                val isPayer = item.payerUserIds.contains(memberId)

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Checkbox(
                                                        checked = isPayer,
                                                        onCheckedChange = { checked ->
                                                            // Cập nhật danh sách người trả món này
                                                            val newPayerIds = if (checked) {
                                                                item.payerUserIds + memberId
                                                            } else {
                                                                item.payerUserIds - memberId
                                                            }
                                                            AppRepository.updateItemPayerUserIds(bill.id, item.id, newPayerIds)
                                                        }
                                                    )
                                                    Text(
                                                        text = if (isMe) "${user?.name} (Bạn)" else (user?.name ?: "N/A"),
                                                        fontSize = 11.sp,
                                                        color = if (isMe) PrimaryTealDark else TextDark
                                                    )
                                                }
                                            }
                                        }

                                        // Chế độ NB2: Nút sửa món đồ nếu có quyền
                                        if (
                                            bill.mode == InputMode.NB2 &&
                                            !bill.isMergedParent &&
                                            bill.mergedIntoBillId == null
                                        ) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                if (bill.nb2Permission.canEditName || bill.nb2Permission.canEditQty || bill.nb2Permission.canEditPrice) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            editingItemId = item.id
                                                            showNb2EditDialog = true
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                    ) {
                                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Sửa món", fontSize = 11.sp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        // Danh sách từng thành viên trong bill
                        Text(
                            text = "Số tiền cụ thể của từng người:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        bill.memberStatuses.forEach { (userId, status) ->
                            val user = AppRepository.getUserById(userId)
                            val isMe = userId == currentUser.id

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                color = if (isMe) PrimaryTealContainer.copy(alpha = 0.5f) else BackgroundSlate,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(if (isMe) PrimaryTeal else TextMedium),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(user?.name?.take(1) ?: "", color = SurfaceWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = if (isMe) "${user?.name} (Bạn)" else (user?.name ?: "N/A"),
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = if (status.isApproved) "☑️ Đã duyệt" else "⬜ Chưa duyệt",
                                                fontSize = 10.sp,
                                                color = if (status.isApproved) SuccessGreen else TextMedium
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = AppRepository.formatCurrency(status.amountOwed),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (status.isPaid) SuccessGreen else ErrorRed
                                        )

                                        // Nút [👍 Bấm Duyệt phần của tôi] (Mục 1.9.4.2)
                                        if (
                                            isMe &&
                                            !status.isApproved &&
                                            (status.amountOwed == 0L || status.isCreatorConfirmed)
                                        ) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { AppRepository.approveMemberPortion(bill.id, currentUser.id) },
                                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("👍 Duyệt phần tôi", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==========================================
            // [MỤC 1.9.4.3 & 1.9.4.4] MÃ QR VIETQR, SỐ TIỀN CẦN CHUYỂN & NÚT ĐÃ TRẢ
            // Chỉ hiển thị cho người cần chuyển tiền (khi amountToPay > 0 và chưa là người ứng tiền)
            // ==========================================
            if (amountToPay > 0L && myStatus?.isCreatorConfirmed != true) {
                item {
                    if (bill.debtObject != null) {
                        DebtObjectPaymentSection(
                            debtObjectName = bill.debtObject.name,
                            amountToPay = amountToPay,
                            isPaid = myStatus?.isPaid == true,
                            onMarkPaid = {
                                AppRepository.markMemberAsPaid(bill.id, currentUser.id)
                            }
                        )
                    } else if (myStatus?.isPaid == true) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = PrimaryTealContainer),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Text(
                                "Bạn đã báo đã trả ${AppRepository.formatCurrency(amountToPay)}. Khoản nợ chỉ được duyệt sau khi người ứng xác nhận đã nhận tiền.",
                                modifier = Modifier.padding(16.dp),
                                fontSize = 12.sp,
                                color = PrimaryTealDark
                            )
                        }
                    } else if (advanceUser != null) {
                        VietQrPaymentSection(
                            receiver = advanceUser,
                            amountToPay = amountToPay,
                            transferCode = transferCode,
                            qrImageUri = advanceUser.qrImageUri,
                            isPaid = myStatus?.isPaid == true,
                            paymentProofUri = myStatus?.proofImageUri,
                            onPickProof = { proofPicker.launch(arrayOf("image/*")) },
                            onReportPaid = {
                                AppRepository.markMemberAsPaid(bill.id, currentUser.id)
                            }
                        )
                    }
                }
            }

            // ==========================================
            // [MỤC 1.9.4.5] TAB ĐỐI SOÁT CỦA CHỦ BILL
            // Chỉ hiển thị khi Bạn là Người ứng tiền (currentUser.id == bill.creatorId)
            // ==========================================
            if (isCurrentUserCreditor) {
                item {
                    CreatorReconciliationSection(
                        bill = bill,
                        creditorUserId = creditorUserId,
                        debtObjectName = bill.debtObject?.name,
                        onConfirmUser = { userId ->
                            AppRepository.confirmPaymentByCreator(bill.id, userId)
                        },
                        onRejectUser = { userId ->
                            AppRepository.rejectPaymentByCreator(bill.id, userId)
                        },
                        onViewProof = { uri ->
                            selectedProofUrl = uri
                            showProofImageDialog = true
                        }
                    )
                }
            }
        }
    }

    // Phóng ảnh minh chứng ngay trên bill.
    if (showProofImageDialog && selectedProofUrl != null) {
        ZoomableBillImageDialog(
            uri = selectedProofUrl.orEmpty(),
            onDismiss = { showProofImageDialog = false }
        )
    }

    if (showDeleteBillDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteBillDialog = false },
            title = { Text("Xóa hóa đơn?") },
            text = { Text("Bill “${bill.title}” sẽ bị xóa khỏi dự án và không thể khôi phục.") },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = AppRepository.deleteBill(bill.id)
                        showDeleteBillDialog = false
                        if (deleted) {
                            Toast.makeText(context, "Đã xóa bill.", Toast.LENGTH_SHORT).show()
                            onBack(bill.projectId)
                        } else {
                            Toast.makeText(context, "Không thể xóa bill này.", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) {
                    Text("Xóa bill")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBillDialog = false }) { Text("Hủy") }
            }
        )
    }

    // Dialog Chỉnh sửa Bill (Mục 1.9.4.6)
    if (showEditBillDialog) {
        EditBillDialog(
            bill = bill,
            participants = (project?.memberIds ?: bill.memberStatuses.keys.toList()).mapIndexed { index, userId ->
                AppRepository.getUserById(userId) ?: User(userId, "Thành viên ${index + 1}", "", "", "", "")
            },
            onDismiss = { showEditBillDialog = false },
            onSave = { updatedTitle, updatedItems ->
                if (!AppRepository.saveBillEdits(bill.copy(title = updatedTitle, items = updatedItems))) {
                    Toast.makeText(context, "Không thể lưu thay đổi bill.", Toast.LENGTH_LONG).show()
                }
                showEditBillDialog = false
            }
        )
    }

    if (showBillEditHistoryDialog) {
        BillEditHistoryDialog(
            bills = allBills,
            projectNames = projects.associate { it.id to it.name },
            onlyBillId = bill.id,
            onDismiss = { showBillEditHistoryDialog = false }
        )
    }

    // Dialog Chỉnh sửa món đồ NB2 (Mục 1.9.3 - thành viên sửa trực tiếp)
    if (showNb2EditDialog && editingItemId != null) {
        val itemToEdit = bill.items.firstOrNull { it.id == editingItemId }
        if (itemToEdit != null) {
            Nb2EditItemDialog(
                item = itemToEdit,
                nb2Permission = bill.nb2Permission,
                onDismiss = { showNb2EditDialog = false; editingItemId = null },
                onSave = { updatedItem ->
                    AppRepository.saveBillEdits(
                        bill.copy(
                            items = bill.items.map { if (it.id == editingItemId) updatedItem else it }
                        )
                    )
                    showNb2EditDialog = false
                    editingItemId = null
                }
            )
        }
    }
}

// ==========================================
// [MỤC 1.9.4.1] KHUNG HIỂN THỊ MINH CHỨNG KHI TẠO BILL
// ==========================================
@Composable
fun ReceiptProofSection(
    receiptUri: String?,
    canEdit: Boolean,
    onPickFromGallery: () -> Unit,
    onTakePhoto: () -> Unit,
    onViewImage: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Image, contentDescription = null, tint = PrimaryTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Minh chứng hóa đơn gốc",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (receiptUri != null) {
                LocalImagePreview(
                    uri = receiptUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onViewImage(receiptUri) },
                    contentDescription = "Ảnh hóa đơn gốc",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
                Text(
                    "Chạm vào ảnh để xem rõ hơn.",
                    fontSize = 11.sp,
                    color = TextMedium,
                    modifier = Modifier.padding(top = 5.dp)
                )
            } else {
                Text(
                    "Chưa có ảnh hóa đơn gốc.",
                    fontSize = 12.sp,
                    color = TextMedium
                )
            }

            if (canEdit) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPickFromGallery,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Thư viện")
                    }
                    OutlinedButton(
                        onClick = onTakePhoto,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text("Chụp ảnh")
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomableBillImageDialog(uri: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            LocalImagePreview(
                uri = uri,
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentDescription = "Ảnh minh chứng",
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Đóng ảnh", tint = Color.White)
            }
        }
    }
}

// ==========================================
// [MỤC 1.9.4.1 & 1.9.5] HIỂN THỊ CÁC BILL CON VÀ SƠ ĐỒ XOAY VÒNG TIỀN TRONG BILL GỘP
// ==========================================
@Composable
fun MergedBillOverviewSection(
    bill: Bill,
    allBills: List<Bill>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SecondaryIndigoLight)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = SecondaryIndigo)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sơ đồ Xoay Vòng Tiền Đã Tối Ưu",
                    fontWeight = FontWeight.Bold,
                    color = SecondaryIndigo,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Hệ thống đã gộp ${bill.childBillIds.size} bill cũ thành ${bill.optimizedTransfers.size} lệnh chuyển tiền duy nhất (Minimum Cash Flow):",
                fontSize = 12.sp,
                color = TextDark
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Hiển thị từng lệnh chuyển tiền đã tối ưu
            bill.optimizedTransfers.forEach { transfer ->
                val fromUser = AppRepository.getUserById(transfer.fromUserId)
                val toUser = AppRepository.getUserById(transfer.toUserId)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = SurfaceWhite
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${fromUser?.name} ➔ ${toUser?.name}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextDark
                            )
                            Text(
                                text = AppRepository.formatCurrency(transfer.amount),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SecondaryIndigo
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Mã CK: ${transfer.transferCode}",
                            fontSize = 11.sp,
                            color = TextMedium,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusBadgeBox(
                                isCompleted = transfer.isPaid,
                                completedText = "☑️ Đã chuyển",
                                uncompletedText = "⏳ Chưa chuyển",
                                activeColor = SuccessGreen
                            )

                            if (!transfer.isPaid) {
                                Button(
                                    onClick = { AppRepository.markOptimizedTransferPaid(transfer.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryIndigo),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Bấm Đã Trả", fontSize = 10.sp)
                                }
                            } else if (!transfer.isConfirmed) {
                                Button(
                                    onClick = { AppRepository.confirmOptimizedTransfer(transfer.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("✅ Xác nhận đã nhận", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Danh sách các bill con đã gộp vào
            Text(
                text = "Tra cứu các bill con cũ đã gộp vào:",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = TextDark
            )
            Spacer(modifier = Modifier.height(4.dp))

            bill.childBillIds.forEach { childId ->
                val childBill = allBills.firstOrNull { it.id == childId }
                if (childBill != null) {
                    val cCreator = AppRepository.getUserById(childBill.creatorId)
                    Text(
                        text = "• ${childBill.title} (${AppRepository.formatCurrency(childBill.totalAmount)}) - Người ứng: ${cCreator?.name}",
                        fontSize = 11.sp,
                        color = TextMedium
                    )
                }
            }
        }
    }
}

// ==========================================
// [MỤC 1.9.4.3 & 1.9.4.4] KHU VỰC VẼ MÃ VIETQR VÀ NÚT ẤN ĐÃ TRẢ
// ==========================================
@Composable
fun DebtObjectPaymentSection(
    debtObjectName: String,
    amountToPay: Long,
    isPaid: Boolean,
    onMarkPaid: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Khoản nợ chung", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                "Chưa có thành viên ứng tiền. Khoản nợ của bạn đang được ghi cho \"$debtObjectName\".",
                fontSize = 12.sp,
                color = TextMedium
            )
            Text(
                AppRepository.formatCurrency(amountToPay),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = if (isPaid) SuccessGreen else PrimaryTealDark
            )
            Button(
                onClick = onMarkPaid,
                enabled = !isPaid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isPaid) SuccessGreen else PrimaryTeal
                )
            ) {
                Text(if (isPaid) "Đã báo thanh toán" else "Đánh dấu đã thanh toán")
            }
        }
    }
}

@Composable
fun VietQrPaymentSection(
    receiver: User,
    amountToPay: Long,
    transferCode: String,
    qrImageUri: String,
    isPaid: Boolean,
    paymentProofUri: String?,
    onPickProof: () -> Unit,
    onReportPaid: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Tiêu đề
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCode2, contentDescription = null, tint = PrimaryTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mã VietQR Chuyển Khoản",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hiển thị ảnh QR người nhận đã tải lên, không tạo mã QR giả.
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qrImageUri.isNotBlank()) {
                    LocalImagePreview(
                        uri = qrImageUri,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceWhite, RoundedCornerShape(12.dp))
                            .padding(6.dp),
                        contentDescription = "QR nhận tiền của ${receiver.name}"
                    )
                } else {
                    Text(
                        "Người nhận chưa tải ảnh QR.\nVui lòng yêu cầu họ tải QR ở trang chủ.",
                        color = SurfaceWhite,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }

            Text(
                text = "Người nhận: ${receiver.name} • ${receiver.bankName}",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text("STK: ${receiver.accountNumber}", fontSize = 12.sp, color = TextMedium)
            Text(
                AppRepository.formatCurrency(amountToPay),
                color = PrimaryTealDark,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp
            )
            Surface(
                color = Color(0xFF1E293B),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("TransferCode", transferCode))
                    Toast.makeText(context, "Đã sao chép mã: $transferCode", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(
                    text = "Nội dung: $transferCode",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = SurfaceWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // Minh chứng là tùy chọn riêng; báo đã trả không yêu cầu phải tải ảnh.
            Text(
                text = "Thanh toán (minh chứng không bắt buộc):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onPickProof,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (paymentProofUri == null) "Tải minh chứng (tùy chọn)" else "Đổi minh chứng", fontSize = 12.sp)
            }

            if (paymentProofUri != null) {
                Spacer(modifier = Modifier.height(6.dp))
                LocalImagePreview(
                    uri = paymentProofUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    contentDescription = "Minh chứng thanh toán đã tải"
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isPaid) {
                Text(
                    "Bạn đã báo đã trả; đang chờ người ứng xác nhận.",
                    fontSize = 12.sp,
                    color = PrimaryTealDark
                )
            } else {
                Button(
                    onClick = onReportPaid,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Báo đã trả", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

// ==========================================
// [MỤC 1.9.4.5] TAB ĐỐI SOÁT CỦA CHỦ BILL
// ==========================================
@Composable
fun CreatorReconciliationSection(
    bill: Bill,
    creditorUserId: String = bill.creatorId,
    debtObjectName: String? = null,
    onConfirmUser: (String) -> Unit,
    onRejectUser: (String) -> Unit,
    onViewProof: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FactCheck, contentDescription = null, tint = PrimaryTealDark)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Đối soát khoản phải thu",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (debtObjectName != null) {
                    "Bạn đang quản lý khoản nợ chung \"$debtObjectName\". Xác nhận khi thành viên đã thanh toán:"
                } else {
                    "Bạn là người nhận tiền ứng trước. Hãy đối soát trạng thái thanh toán của các thành viên:"
                },
                fontSize = 11.sp,
                color = TextMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bảng danh sách các thành viên khác
            val debtorStatuses = bill.memberStatuses.filter {
                (debtObjectName != null || it.key != creditorUserId) && it.value.amountOwed > 0L
            }
            val unpaidTotal = debtorStatuses.values.filterNot { it.isCreatorConfirmed }.sumOf { it.amountOwed }
            Text(
                "Đã xác nhận: ${AppRepository.formatCurrency(debtorStatuses.values.filter { it.isCreatorConfirmed }.sumOf { it.amountOwed })} • Còn phải thu: ${AppRepository.formatCurrency(unpaidTotal)}",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryTealDark
            )
            if (debtorStatuses.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Chưa có khoản phải thu từ thành viên khác.", fontSize = 12.sp, color = TextMedium)
            }
            debtorStatuses.forEach { (userId, status) ->
                val user = AppRepository.getUserById(userId)

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    color = BackgroundSlate,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(user?.name ?: "N/A", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                text = AppRepository.formatCurrency(status.amountOwed),
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTealDark,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Trạng thái Đã duyệt & Đã trả
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                StatusBadgeBox(
                                    isCompleted = status.isApproved,
                                    completedText = "Đã duyệt",
                                    uncompletedText = "Chưa duyệt",
                                    activeColor = SuccessGreen
                                )
                                StatusBadgeBox(
                                    isCompleted = status.isPaid,
                                    completedText = "Đã trả",
                                    uncompletedText = "Chưa trả",
                                    activeColor = PrimaryTeal
                                )
                            }

                            // View proof and let the creditor accept or reject a reported payment.
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (status.proofImageUri != null) {
                                    IconButton(
                                        onClick = { onViewProof(status.proofImageUri) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = "Xem ảnh", tint = PrimaryTeal)
                                    }
                                }

                                if (debtObjectName != null) {
                                    Text(
                                        if (status.isCreatorConfirmed) "Người nợ đã xác nhận" else "Chờ người nợ thanh toán",
                                        fontSize = 10.sp,
                                        color = if (status.isCreatorConfirmed) SuccessGreen else TextMedium
                                    )
                                } else if (status.isCreatorConfirmed) {
                                    Text("✅ Đã nhận", fontSize = 10.sp, color = SuccessGreen)
                                } else if (status.isPaid) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Button(
                                            onClick = { onConfirmUser(userId) },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text("Xác nhận nhận", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        TextButton(
                                            onClick = { onRejectUser(userId) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                        ) {
                                            Text("Từ chối, yêu cầu gửi lại", fontSize = 10.sp, color = ErrorRed)
                                        }
                                    }
                                } else {
                                    Text("Chưa báo đã trả", fontSize = 10.sp, color = TextMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// [MỤC 1.9.4.6] DIALOG CHỈNH SỬA THÔNG TIN BILL
// ==========================================
@Composable
fun EditBillDialog(
    bill: Bill,
    participants: List<User>,
    onDismiss: () -> Unit,
    onSave: (String, List<BillItem>) -> Unit
) {
    var title by remember { mutableStateOf(bill.title) }
    var items by remember { mutableStateOf(bill.items) }
    var newItemName by remember { mutableStateOf("") }
    var newItemQuantity by remember { mutableStateOf("") }
    var newItemPrice by remember { mutableStateOf("") }
    var validationMessage by remember { mutableStateOf<String?>(null) }
    val validItems = items.all {
        it.name.isNotBlank() && it.quantity > 0 && it.unitPrice > 0L
    } && items.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa thông tin Bill", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Chỉnh sửa sẽ cập nhật lại phần chia và đưa trạng thái duyệt/thanh toán về chưa xác nhận.",
                    fontSize = 12.sp,
                    color = TextMedium
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên hóa đơn") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = BillInputFieldColors(),
                    singleLine = true
                )
                items.forEachIndexed { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = BackgroundSlate),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Món ${index + 1}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                IconButton(
                                    onClick = { items = items.filterIndexed { itemIndex, _ -> itemIndex != index } },
                                    enabled = items.size > 1
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Xóa món", tint = ErrorRed)
                                }
                            }
                            OutlinedTextField(
                                value = item.name,
                                onValueChange = { value ->
                                    items = items.mapIndexed { itemIndex, current ->
                                        if (itemIndex == index) current.copy(name = value) else current
                                    }
                                },
                                label = { Text("Tên món") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = BillInputFieldColors(),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = item.quantity.toString(),
                                    onValueChange = { value ->
                                        val quantity = value.filter(Char::isDigit).toIntOrNull() ?: 0
                                        items = items.mapIndexed { itemIndex, current ->
                                            if (itemIndex == index) current.copy(quantity = quantity) else current
                                        }
                                    },
                                    label = { Text("Số lượng") },
                                    modifier = Modifier.weight(1f),
                                    colors = BillInputFieldColors(),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = item.unitPrice.toString(),
                                    onValueChange = { value ->
                                        val price = value.filter(Char::isDigit).toLongOrNull() ?: 0L
                                        items = items.mapIndexed { itemIndex, current ->
                                            if (itemIndex == index) current.copy(unitPrice = price) else current
                                        }
                                    },
                                    label = { Text("Đơn giá (VNĐ)") },
                                    modifier = Modifier.weight(1f),
                                    colors = BillInputFieldColors(),
                                    singleLine = true
                                )
                            }
                            Text(
                                "Người chia món:",
                                fontSize = 11.sp,
                                color = TextMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            val selectedPayers = item.payerUserIds.ifEmpty { participants.map { it.id } }
                            participants.forEach { participant ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = participant.id in selectedPayers,
                                        onCheckedChange = { checked ->
                                            if (checked || selectedPayers.size > 1) {
                                                val nextPayers = if (checked) {
                                                    (selectedPayers + participant.id).distinct()
                                                } else {
                                                    selectedPayers - participant.id
                                                }
                                                items = items.mapIndexed { itemIndex, current ->
                                                    if (itemIndex == index) current.copy(payerUserIds = nextPayers) else current
                                                }
                                            }
                                        }
                                    )
                                    Text(participant.name, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Text("Thêm món", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                OutlinedTextField(
                    value = newItemName,
                    onValueChange = { newItemName = it; validationMessage = null },
                    label = { Text("Tên món mới") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = BillInputFieldColors(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newItemQuantity,
                        onValueChange = { newItemQuantity = it.filter(Char::isDigit); validationMessage = null },
                        label = { Text("Số lượng") },
                        modifier = Modifier.weight(1f),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newItemPrice,
                        onValueChange = { newItemPrice = it.filter(Char::isDigit); validationMessage = null },
                        label = { Text("Đơn giá") },
                        modifier = Modifier.weight(1f),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )
                }
                OutlinedButton(
                    onClick = {
                        val quantity = newItemQuantity.toIntOrNull()
                        val price = newItemPrice.toLongOrNull()
                        when {
                            newItemName.isBlank() -> validationMessage = "Vui lòng nhập tên món."
                            quantity == null || quantity <= 0 -> validationMessage = "Số lượng phải lớn hơn 0."
                            price == null || price <= 0L -> validationMessage = "Đơn giá phải lớn hơn 0."
                            else -> {
                                items = items + BillItem(
                                    name = newItemName.trim(),
                                    quantity = quantity,
                                    unitPrice = price,
                                    payerUserIds = participants.map { it.id }
                                )
                                newItemName = ""
                                newItemQuantity = ""
                                newItemPrice = ""
                                validationMessage = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Thêm món vào bill")
                }
                Text(
                    "Tổng bill: ${AppRepository.formatCurrency(items.sumOf { it.totalPrice })}",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTealDark
                )
                validationMessage?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, items) },
                enabled = title.isNotBlank() && validItems,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text("Lưu thay đổi")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

// ==========================================
// [MỤC 1.9.3 NB2] DIALOG CHỈNH SỬA MÓN ĐỒ (THÀNH VIÊN SỬA TRỰC TIẾP)
// ==========================================
@Composable
fun Nb2EditItemDialog(
    item: BillItem,
    nb2Permission: Nb2Permission,
    onDismiss: () -> Unit,
    onSave: (BillItem) -> Unit
) {
    var itemName by remember { mutableStateOf(item.name) }
    var itemQty by remember { mutableStateOf(item.quantity.toString()) }
    var itemPrice by remember { mutableStateOf(item.unitPrice.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa món đồ (NB2)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { if (nb2Permission.canEditName) itemName = it },
                    label = { Text("Tên món") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nb2Permission.canEditName,
                    colors = BillInputFieldColors()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = itemQty,
                        onValueChange = { if (nb2Permission.canEditQty) itemQty = it },
                        label = { Text("Số lượng") },
                        modifier = Modifier.weight(1f),
                        enabled = nb2Permission.canEditQty,
                        colors = BillInputFieldColors()
                    )
                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { if (nb2Permission.canEditPrice) itemPrice = it },
                        label = { Text("Đơn giá") },
                        modifier = Modifier.weight(1f),
                        enabled = nb2Permission.canEditPrice,
                        colors = BillInputFieldColors()
                    )
                }

                val qty = itemQty.toIntOrNull() ?: item.quantity
                val price = itemPrice.toLongOrNull() ?: item.unitPrice
                val total = qty * price

                Text(
                    text = "Tổng: ${AppRepository.formatCurrency(total)}",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTealDark,
                    fontSize = 14.sp
                )

                if (!nb2Permission.canEditName && !nb2Permission.canEditQty && !nb2Permission.canEditPrice) {
                    Text(
                        "Bạn không có quyền sửa bất kỳ tham số nào của món này.",
                        fontSize = 11.sp,
                        color = ErrorRed
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedItem = item.copy(
                        name = itemName,
                        quantity = itemQty.toIntOrNull() ?: item.quantity,
                        unitPrice = itemPrice.toLongOrNull() ?: item.unitPrice
                    )
                    onSave(updatedItem)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                enabled = nb2Permission.canEditName || nb2Permission.canEditQty || nb2Permission.canEditPrice
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
