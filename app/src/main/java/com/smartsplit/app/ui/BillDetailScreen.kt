package com.smartsplit.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsplit.app.data.*
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
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val allBills by AppRepository.bills.collectAsState()
    val projects by AppRepository.projects.collectAsState()
    val currentUserId by AppRepository.currentUserId.collectAsState()
    val currentUser = AppRepository.getCurrentUser()

    val bill = allBills.firstOrNull { it.id == billId }
    if (bill == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Không tìm thấy hóa đơn!")
        }
        return
    }

    val project = projects.firstOrNull { it.id == bill.projectId }
    val creator = AppRepository.getUserById(bill.creatorId)
    val isCurrentUserCreator = currentUser.id == bill.creatorId

    // Trạng thái của user hiện tại trong bill này
    val myStatus = bill.memberStatuses[currentUser.id]
    val amountToPay = myStatus?.amountOwed ?: 0L

    // Cú pháp quy ước mã chuyển khoản: SM_[MãDựÁn]_[MãBill]_[TênNgườiTrả]
    // [SỬA NHANH KHI VẤN ĐÁP] Cú pháp chuỗi nhận diện VietQR
    val projectCode = project?.inviteCode ?: "P302VN"
    val billCode = if (bill.isMergedParent) "MERGED" else bill.id.take(4).uppercase(Locale.getDefault())
    val payerName = currentUser.name.uppercase(Locale.getDefault())
    val transferCode = "SM_${projectCode}_${billCode}_${payerName}"

    // Dialog state
    var showEditBillDialog by remember { mutableStateOf(false) }
    var showProofImageDialog by remember { mutableStateOf(false) }
    var selectedProofUrl by remember { mutableStateOf<String?>(null) }
    var showNb2EditDialog by remember { mutableStateOf(false) }
    var editingItemId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(bill.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            text = if (bill.isMergedParent) "Hóa đơn Gộp Xoay Vòng Tiền (1.9.5)" else "Chế độ ${bill.mode} • Người ứng: ${creator?.name}",
                            fontSize = 11.sp,
                            color = TextMedium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    // ==========================================
                    // [MỤC 1.9.4.6] NÚT CHỈNH SỬA BILL (CHỈ DÀNH CHO CHỦ BILL)
                    // ==========================================
                    if (isCurrentUserCreator && !bill.isMergedParent) {
                        IconButton(onClick = { showEditBillDialog = true }) {
                            Icon(Icons.Default.Edit, contentDescription = "Sửa Bill", tint = PrimaryTeal)
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
                    ReceiptProofSection(receiptUri = bill.receiptImageUri)
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
                                text = "Chi tiết món & phân chia (1.9.4.2)",
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
                                        if (bill.mode == InputMode.NB2 && bill.nb2Permission.canSelectItem) {
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
                                                        },
                                                        enabled = isMe || isCurrentUserCreator // Chỉ người đó hoặc chủ bill mới đổi
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
                                        if (bill.mode == InputMode.NB2) {
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
                                                        enabled = isCurrentUserCreator || isMe, // Chỉ chủ bill hoặc người đó mới sửa
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
                                        if (isMe && !status.isApproved) {
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
            if (!isCurrentUserCreator && amountToPay > 0L) {
                item {
                    VietQrPaymentSection(
                        receiver = creator ?: AppRepository.sampleUsers.first(),
                        amountToPay = amountToPay,
                        transferCode = transferCode,
                        isPaid = myStatus?.isPaid == true,
                        onMarkPaid = { proofUri ->
                            // Bước 1: Gọi hàm đánh dấu đã trả -> Tự động tích ☑️ Đã duyệt & ☑️ Đã trả (1.9.4.4)
                            AppRepository.markMemberAsPaid(bill.id, currentUser.id, proofUri)
                        },
                        onSimulateBankNotification = {
                            // Bước 2: Nút giả lập ngân hàng gửi thông báo biến động số dư chứa mã SM_...
                            AppRepository.autoGachNoFromBankNotification(transferCode)
                            Toast.makeText(context, "⚡ Đã giả lập Ngân hàng báo nhận tiền thành công!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            // ==========================================
            // [MỤC 1.9.4.5] TAB ĐỐI SOÁT CỦA CHỦ BILL
            // Chỉ hiển thị khi Bạn là Người ứng tiền (currentUser.id == bill.creatorId)
            // ==========================================
            if (isCurrentUserCreator) {
                item {
                    CreatorReconciliationSection(
                        bill = bill,
                        onConfirmUser = { userId ->
                            AppRepository.confirmPaymentByCreator(bill.id, userId)
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

    // Dialog phóng to ảnh minh chứng chuyển khoản (1.9.4.4)
    if (showProofImageDialog && selectedProofUrl != null) {
        AlertDialog(
            onDismissRequest = { showProofImageDialog = false },
            title = { Text("Minh chứng chuyển khoản (1.9.4.4)", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        color = BackgroundSlate,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Receipt, contentDescription = null, tint = PrimaryTeal, modifier = Modifier.size(64.dp))
                            Text(
                                "Ảnh biên lai ngân hàng đã tải lên\n(Mã SM_... hợp lệ)",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 90.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProofImageDialog = false }) { Text("Đóng") }
            }
        )
    }

    // Dialog Chỉnh sửa Bill (Mục 1.9.4.6)
    if (showEditBillDialog) {
        EditBillDialog(
            bill = bill,
            onDismiss = { showEditBillDialog = false },
            onSave = { updatedTitle ->
                AppRepository.updateBill(bill.copy(title = updatedTitle))
                showEditBillDialog = false
            }
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
                    AppRepository.updateBill(
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
fun ReceiptProofSection(receiptUri: String?) {
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
                    text = "Minh chứng hóa đơn gốc (1.9.4.1)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                shape = RoundedCornerShape(10.dp),
                color = BackgroundSlate
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryTealLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = PrimaryTealDark, modifier = Modifier.size(36.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = if (receiptUri != null) "Hóa đơn chụp từ camera AI" else "Hóa đơn tạo bằng phương thức Nhập tay",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Mọi người có thể kiểm tra món ăn và giá tiền đối chiếu.",
                            fontSize = 11.sp,
                            color = TextMedium
                        )
                    }
                }
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
                    text = "Sơ đồ Xoay Vòng Tiền Đã Tối Ưu (1.9.5)",
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
fun VietQrPaymentSection(
    receiver: User,
    amountToPay: Long,
    transferCode: String,
    isPaid: Boolean,
    onMarkPaid: (String?) -> Unit,
    onSimulateBankNotification: () -> Unit
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
                    text = "Mã VietQR Chuyển Khoản (1.9.4.3)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Thẻ VietQR chuẩn vẽ bằng Compose Canvas kết hợp giao diện thẻ ngân hàng
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Header NAPAS247 & VIETQR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("VIETQR", color = SurfaceWhite, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Surface(color = Color(0xFF1E293B), shape = RoundedCornerShape(6.dp)) {
                            Text("NAPAS 247", color = PrimaryTealLight, fontWeight = FontWeight.Bold, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Khung QR Code tự vẽ bằng Canvas
                    Box(
                        modifier = Modifier
                            .size(170.dp)
                            .background(SurfaceWhite, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Vẽ ma trận QR Code mô phỏng
                            val squareSize = size.width / 15f
                            for (r in 0 until 15) {
                                for (c in 0 until 15) {
                                    val isCorner = (r < 4 && c < 4) || (r < 4 && c > 10) || (r > 10 && c < 4)
                                    val isCenter = (r in 1..2 && c in 1..2) || (r in 1..2 && c in 12..13) || (r in 12..13 && c in 1..2)
                                    val isPattern = (r + c * 3) % 4 == 0 || (r * c) % 5 == 0

                                    if (isCorner || isCenter || isPattern) {
                                        drawRect(
                                            color = Color.Black,
                                            topLeft = Offset(c * squareSize, r * squareSize),
                                            size = Size(squareSize - 1.5f, squareSize - 1.5f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Thông tin người nhận & Số tiền cần chuyển
                    Text(
                        text = "Người nhận: ${receiver.name} • ${receiver.bankName}",
                        color = SurfaceWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "STK: ${receiver.accountNumber}",
                        color = PrimaryTealLight,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = AppRepository.formatCurrency(amountToPay),
                        color = Color(0xFFFBBF24), // Màu vàng nổi bật
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Cú pháp nội dung chuyển khoản quy ước (SM_...)
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("TransferCode", transferCode))
                            Toast.makeText(context, "Đã sao chép mã: $transferCode", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nội dung: $transferCode",
                                color = SurfaceWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = PrimaryTealLight, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // ==========================================
            // [MỤC 1.9.4.4] Ô ẤN "ĐÃ TRẢ", TẢI MINH CHỨNG & NÚT TEST TỰ ĐỘNG GẠCH NỢ
            // ==========================================
            Text(
                text = "Xác nhận thanh toán (1.9.4.4):",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Nút Ấn Đã Trả kèm ảnh biên lai
                Button(
                    onClick = {
                        // Bước 1: Giả lập tải ảnh biên lai và tích Đã trả
                        onMarkPaid("https://images.unsplash.com/photo-1554224154-26032ffc0d07?w=400")
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaid) SuccessGreen else PrimaryTeal
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(if (isPaid) Icons.Default.CheckCircle else Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isPaid) "Đã bấm Đã Trả" else "💸 Ấn Đã Trả", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Nút Tải ảnh minh chứng
                OutlinedButton(
                    onClick = {
                        onMarkPaid("https://images.unsplash.com/photo-1554224154-26032ffc0d07?w=400")
                        Toast.makeText(context, "Đã đính kèm ảnh chụp màn hình chuyển khoản!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🖼️ Tải minh chứng", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ==========================================
            // [MỤC 1.9.4.4] NÚT NỔI BẬT: GIẢ LẬP NHẬN THÔNG BÁO NGÂN HÀNG
            // Tự động gạch nợ ngay lập tức, sáng cả 2 ô [☑️ Đã duyệt] và [☑️ Đã trả]
            // ==========================================
            Button(
                onClick = onSimulateBankNotification,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "⚡ Giả lập Ngân hàng báo nhận tiền mã $transferCode",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.Black
                )
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
    onConfirmUser: (String) -> Unit,
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
                    text = "Tab Đối Soát Của Chủ Bill (1.9.4.5)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Bạn là người ứng tiền bill này. Hãy đối soát trạng thái chuyển khoản của các thành viên:",
                fontSize = 11.sp,
                color = TextMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Bảng danh sách các thành viên khác
            bill.memberStatuses.filter { it.key != bill.creatorId }.forEach { (userId, status) ->
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

                            // Xem minh chứng & Nút Confirm
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (status.proofImageUri != null) {
                                    IconButton(
                                        onClick = { onViewProof(status.proofImageUri) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Visibility, contentDescription = "Xem ảnh", tint = PrimaryTeal)
                                    }
                                }

                                Button(
                                    onClick = { onConfirmUser(userId) },
                                    enabled = status.isPaid && !status.isCreatorConfirmed,
                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (status.isCreatorConfirmed) "✅ Đã khớp tiền" else "Confirm đã nhận",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var title by remember { mutableStateOf(bill.title) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chỉnh sửa thông tin Bill (1.9.4.6)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chỉ Người tạo bill (Người ứng tiền) mới có quyền chỉnh sửa mục này.", fontSize = 12.sp, color = TextMedium)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tên hóa đơn") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (title.isNotBlank()) onSave(title) },
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { if (nb2Permission.canEditName) itemName = it },
                    label = { Text("Tên món") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = nb2Permission.canEditName
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = itemQty,
                        onValueChange = { if (nb2Permission.canEditQty) itemQty = it },
                        label = { Text("Số lượng") },
                        modifier = Modifier.weight(1f),
                        enabled = nb2Permission.canEditQty
                    )
                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { if (nb2Permission.canEditPrice) itemPrice = it },
                        label = { Text("Đơn giá") },
                        modifier = Modifier.weight(1f),
                        enabled = nb2Permission.canEditPrice
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
