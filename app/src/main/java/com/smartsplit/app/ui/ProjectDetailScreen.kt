package com.smartsplit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsplit.app.data.AppRepository
import com.smartsplit.app.data.Bill
import com.smartsplit.app.data.BillDebtObject
import com.smartsplit.app.data.BillItem
import com.smartsplit.app.data.BudgetConfig
import com.smartsplit.app.data.InputMode
import com.smartsplit.app.data.MemberBillStatus
import com.smartsplit.app.data.Nb2Permission
import com.smartsplit.app.data.Project
import com.smartsplit.app.data.User
import com.smartsplit.app.data.unpaidAmountFor
import com.smartsplit.app.ui.theme.*
import android.widget.Toast

// ==========================================
// [MỤC 1.9.x] MÀN HÌNH CHI TIẾT DỰ ÁN ĐÃ VÔ (PROJECT DETAIL SCREEN)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onNavigateToBill: (String) -> Unit
) {
    val context = LocalContext.current
    val projects by AppRepository.projects.collectAsState()
    val allBills by AppRepository.bills.collectAsState()
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

    val project = projects.firstOrNull { it.id == projectId }
    if (project == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Không tìm thấy dự án!")
        }
        return
    }

    // Lọc danh sách bill thuộc dự án này
    val projectBills = allBills.filter { it.projectId == projectId }

    // State cho các bộ lọc (Mục 1.9.2)
    var searchQuery by remember { mutableStateOf("") }
    var approvalFilter by remember { mutableStateOf("Tất cả") } // Tất cả, Đã duyệt, Chưa duyệt
    var paymentFilter by remember { mutableStateOf("Tất cả") }   // Tất cả, Đã thanh toán, Chưa thanh toán
    var dateFilter by remember { mutableStateOf<String?>(null) } // Lọc theo ngày tháng
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showUnpaidBillsDialog by remember { mutableStateOf(false) }

    // State hiển thị Dialog
    var showCreateBillDialog by remember { mutableStateOf(false) }
    var billPendingDeletion by remember { mutableStateOf<Bill?>(null) }

    // Tính toán số bill đủ điều kiện gộp (Mục 1.9.5)
    val eligibleMergeBills = projectBills.filter {
        !it.isMergedParent && it.mergedIntoBillId == null && it.isAllApproved && !it.isAllPaid
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(project.name, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = PrimaryTealContainer,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = project.inviteCode,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryTealDark,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${project.memberIds.size} thành viên: ${project.memberIds.mapNotNull { AppRepository.getUserById(it)?.name }.joinToString(", ")}",
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
                    IconButton(onClick = { showCreateBillDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Thêm bill", tint = PrimaryTeal)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceWhite)
            )
        },
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(BackgroundSlate),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            item {
                val debtUserId = currentUserId ?: currentUser.id
                val unpaidAmount = AppRepository.unpaidAmountForUser(projectBills, debtUserId)
                val unpaidBillsCount = projectBills.count { it.unpaidAmountFor(debtUserId) > 0L }
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showUnpaidBillsDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryTeal)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Nợ bill chưa trả trong ${project.name}",
                                color = SurfaceWhite.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryTealLight, modifier = Modifier.size(18.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = AppRepository.formatCurrency(unpaidAmount),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                        Text(
                            text = "$unpaidBillsCount bill chưa thanh toán",
                            fontSize = 12.sp,
                            color = SurfaceWhite.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = SurfaceWhite.copy(alpha = 0.35f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hạn mức còn lại: ${AppRepository.formatCurrency(project.budgetConfig.remainingAmount)}",
                            fontSize = 13.sp,
                            color = SurfaceWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (project.budgetConfig.remainingAmount < 0L) {
                            Text(
                                text = "Cảnh báo: đã vượt hạn mức ${AppRepository.formatCurrency(-project.budgetConfig.remainingAmount)}",
                                fontSize = 12.sp,
                                color = Color(0xFFFFD1D1),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ==========================================
            // [MỤC 1.9.5] NÚT NỔI BẬT: GỘP CÁC BILL ĐÃ DUYỆT & THUẬT TOÁN XOAY VÒNG TIỀN
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (eligibleMergeBills.size >= 2) SecondaryIndigoLight else BackgroundSlate
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = androidx.compose.ui.graphics.SolidColor(if (eligibleMergeBills.size >= 2) SecondaryIndigo else DividerBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.SyncAlt,
                                contentDescription = null,
                                tint = if (eligibleMergeBills.size >= 2) SecondaryIndigo else TextMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Gộp Bill & Xoay Vòng Tiền",
                                fontWeight = FontWeight.Bold,
                                color = if (eligibleMergeBills.size >= 2) SecondaryIndigo else TextDark,
                                fontSize = 14.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Hiện có ${eligibleMergeBills.size} bill đã duyệt đang chờ gộp. Thuật toán Minimum Cash Flow sẽ triệt tiêu nợ chéo để rút gọn còn số lần chuyển ít nhất!",
                            fontSize = 12.sp,
                            color = TextMedium,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Nút bấm thực thi thuật toán gộp bill 1.9.5
                        Button(
                            onClick = {
                                // Bước 1: Thực thi thuật toán gộp các bill đã duyệt trong AppRepository
                                val mergedBill = AppRepository.mergeApprovedBills(projectId)
                                if (mergedBill != null) {
                                    // Tự động mở chi tiết Bill Gộp mới vừa tạo
                                    onNavigateToBill(mergedBill.id)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = eligibleMergeBills.size >= 2, // [SỬA NHANH KHI VẤN ĐÁP] Cần tối thiểu 2 bill đã duyệt để gộp
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryIndigo),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.MergeType, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (eligibleMergeBills.size >= 2) "🔄 Gộp ${eligibleMergeBills.size} Bill Đã Duyệt ngay" else "Cần ít nhất 2 bill đã duyệt để gộp",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // [MỤC 1.9.2] TÌM KIẾM & BỘ LỌC CÁC BILL TRONG DỰ ÁN
            // ==========================================
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ô tìm kiếm từ khóa tên bill
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { InputPlaceholder("Tìm tên bill hoặc món ăn...", 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMedium) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = DividerBorder
                        )
                    )

                    // 3 Bộ lọc Filter Chips: Ngày tháng, Trạng thái Duyệt & Trạng thái Thanh toán
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Lọc ngày tháng (Mục 1.9.2.1)
                        item {
                            FilterChip(
                                selected = dateFilter != null,
                                onClick = { showDatePickerDialog = true },
                                label = { Text(if (dateFilter != null) "📅 ${dateFilter}" else "📅 Ngày", fontSize = 11.sp) }
                            )
                        }

                        // Lọc duyệt (Mục 1.9.2.2)
                        item {
                            FilterChip(
                                selected = approvalFilter == "Tất cả",
                                onClick = { approvalFilter = "Tất cả" },
                                label = { Text("Tất cả", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = approvalFilter == "Đã duyệt",
                                onClick = { approvalFilter = "Đã duyệt" },
                                label = { Text("☑️ Đã duyệt", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = approvalFilter == "Chưa duyệt",
                                onClick = { approvalFilter = "Chưa duyệt" },
                                label = { Text("⬜ Chưa duyệt", fontSize = 11.sp) }
                            )
                        }

                        // Lọc thanh toán (Mục 1.9.2.3)
                        item {
                            FilterChip(
                                selected = paymentFilter == "Đã thanh toán",
                                onClick = { paymentFilter = if (paymentFilter == "Đã thanh toán") "Tất cả" else "Đã thanh toán" },
                                label = { Text("💸 Đã trả", fontSize = 11.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = paymentFilter == "Chưa thanh toán",
                                onClick = { paymentFilter = if (paymentFilter == "Chưa thanh toán") "Tất cả" else "Chưa thanh toán" },
                                label = { Text("⏳ Chưa trả", fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // [MỤC 1.9.4 & 1.9.5] DANH SÁCH CÁC Ô BILL ĐÃ THÊM & LÀM TỐI MÀU CÁC BILL CŨ
            // ==========================================
            val filteredBills = projectBills.filter { bill ->
                val query = searchQuery.trim()
                val matchSearch = query.isEmpty() ||
                    bill.title.contains(query, ignoreCase = true) ||
                    bill.items.any { it.name.contains(query, ignoreCase = true) }
                val matchApproval = when (approvalFilter) {
                    "Đã duyệt" -> bill.isAllApproved
                    "Chưa duyệt" -> !bill.isAllApproved
                    else -> true
                }
                val matchPayment = when (paymentFilter) {
                    "Đã thanh toán" -> bill.isAllPaid
                    "Chưa thanh toán" -> !bill.isAllPaid
                    else -> true
                }
                val matchDate = if (dateFilter != null) {
                    billDateLabel(bill.billDate) == dateFilter
                } else {
                    true
                }
                matchSearch && matchApproval && matchPayment && matchDate
            }

            item {
                Button(
                    onClick = { showCreateBillDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Icon(Icons.Default.ReceiptLong, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Thêm Bill (NB1/NB2)", fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text(
                    text = "Danh sách hóa đơn (${filteredBills.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDark
                )
            }

            if (filteredBills.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 30.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Không có hóa đơn nào khớp với bộ lọc!", color = TextLight, fontSize = 13.sp)
                    }
                }
            } else {
                items(filteredBills) { bill ->
                    // ==========================================
                    // [MỤC 1.9.5] QUY TẮC HIỂN THỊ BILL CŨ ĐÃ BỊ GỘP:
                    // Làm tối màu (alpha = 0.45f), gắn nhãn "Đã gộp",
                    // và khi ấn vào TỰ ĐỘNG NHẢY THẲNG ĐẾN "BILL ĐÃ GỘP" CHỨA NÓ!
                    // ==========================================
                    val isMergedOldBill = bill.mergedIntoBillId != null
                    // [SỬA NHANH KHI VẤN ĐÁP] Độ tối alpha của bill cũ đã gộp
                    val cardAlpha = if (isMergedOldBill) 0.45f else 1.0f

                    BillCardItem(
                        bill = bill,
                        alpha = cardAlpha,
                        isMergedOldBill = isMergedOldBill,
                        onDelete = if (
                            bill.creatorId == currentUser.id &&
                            !bill.isMergedParent &&
                            bill.mergedIntoBillId == null
                        ) {
                            { billPendingDeletion = bill }
                        } else {
                            null
                        },
                        onClick = {
                            if (isMergedOldBill && bill.mergedIntoBillId != null) {
                                // Bước 1: Khi ấn vào bill cũ tối màu -> Tự động nhảy thẳng đến Bill Đã Gộp chứa nó!
                                onNavigateToBill(bill.mergedIntoBillId)
                            } else {
                                // Mở chi tiết bill bình thường
                                onNavigateToBill(bill.id)
                            }
                        }
                    )
                }
            }
        }
    }

    // Dialog Thêm Bill Mới đầy đủ NB1/NB2/Xét lịch (Mục 1.9.3)
    if (showCreateBillDialog) {
        CreateBillDialog(
            project = project,
            currentUser = currentUser,
            onDismiss = { showCreateBillDialog = false },
            onAddBill = { newBill ->
                AppRepository.addBill(newBill)
                showCreateBillDialog = false
            }
        )
    }

    billPendingDeletion?.let { bill ->
        AlertDialog(
            onDismissRequest = { billPendingDeletion = null },
            title = { Text("Xóa bill?") },
            text = { Text("Bill “${bill.title}” sẽ bị xóa khỏi dự án và không thể khôi phục.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (AppRepository.deleteBill(bill.id)) {
                            Toast.makeText(context, "Đã xóa bill.", Toast.LENGTH_SHORT).show()
                            billPendingDeletion = null
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
                TextButton(onClick = { billPendingDeletion = null }) { Text("Hủy") }
            }
        )
    }

    // Dialog Lọc theo ngày tháng (Mục 1.9.2.1)
    if (showDatePickerDialog) {
        ProjectDatePickerDialog(
            projectBills = projectBills,
            onDismiss = { showDatePickerDialog = false },
            onDateSelected = { dateStr ->
                dateFilter = dateStr
                showDatePickerDialog = false
            }
        )
    }

    if (showUnpaidBillsDialog) {
        UnpaidBillsDialog(
            bills = projectBills,
            userId = currentUserId ?: currentUser.id,
            projectNames = mapOf(project.id to project.name),
            onDismiss = { showUnpaidBillsDialog = false },
            onOpenBill = { billId ->
                showUnpaidBillsDialog = false
                onNavigateToBill(billId)
            }
        )
    }
}

// ==========================================
// [MỤC 1.9.4] THẺ Ô BILL MẶT NGOÀI TRÊN DANH SÁCH DỰ ÁN
// ==========================================
@Composable
fun BillCardItem(
    bill: Bill,
    alpha: Float,
    isMergedOldBill: Boolean,
    onDelete: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val advanceUser = bill.advanceUserId?.let(AppRepository::getUserById)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha) // [SỬA NHANH KHI VẤN ĐÁP] Làm tối màu bill cũ đã gộp
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (bill.isMergedParent) SecondaryIndigoLight else SurfaceWhite
        ),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (bill.isMergedParent) SecondaryIndigo else DividerBorder
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Hàng 1: Tiêu đề + Huy hiệu chế độ / Đã gộp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (bill.isMergedParent) {
                        Icon(Icons.Default.AutoMode, contentDescription = null, tint = SecondaryIndigo, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    Text(
                        text = bill.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Nhãn trạng thái (NB1 / NB2 / Đã gộp)
                    if (isMergedOldBill) {
                        Surface(
                            color = Color.DarkGray,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Đã gộp vào Bill Gộp",
                                fontSize = 10.sp,
                                color = SurfaceWhite,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (bill.isMergedParent) {
                        Surface(
                            color = SecondaryIndigo,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "BILL GỘP TỐI ƯU",
                                fontSize = 10.sp,
                                color = SurfaceWhite,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = if (bill.mode == InputMode.NB1) PrimaryTealLight else WarningAmberLight,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (bill.mode == InputMode.NB1) "NB1 (Cá nhân)" else "NB2 (Cùng sửa)",
                                fontSize = 10.sp,
                                color = if (bill.mode == InputMode.NB1) PrimaryTealDark else WarningAmber,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Xóa bill ${bill.title}",
                                tint = ErrorRed,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Hàng 2: Ngày tạo & Người ứng tiền
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ngày: ${billDateLabel(bill.billDate)}",
                    fontSize = 11.sp,
                    color = TextLight
                )
                Text(
                    text = if (bill.debtObject != null) {
                        "Chưa có ai ứng"
                    } else {
                        "Người ứng: ${advanceUser?.name ?: "N/A"}"
                    },
                    fontSize = 12.sp,
                    color = TextMedium,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hàng 3: Tổng tiền & 2 Ô TICK TRẠNG THÁI [☑️ ĐÃ DUYỆT] VÀ [☑️ ĐÃ TRẢ] (Mục 1.9.4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppRepository.formatCurrency(bill.totalAmount),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (bill.isMergedParent) SecondaryIndigo else PrimaryTealDark
                )

                // 2 ô tick trạng thái bắt buộc theo thiết kế 1.9.4
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ô 1: Trạng thái Đã duyệt
                    StatusBadgeBox(
                        isCompleted = bill.isAllApproved,
                        completedText = "☑️ Đã duyệt",
                        uncompletedText = "⬜ Chưa duyệt",
                        activeColor = SuccessGreen
                    )

                    // Ô 2: Trạng thái Đã trả
                    StatusBadgeBox(
                        isCompleted = bill.isAllPaid,
                        completedText = "☑️ Đã trả",
                        uncompletedText = "⏳ Chưa trả",
                        activeColor = PrimaryTeal
                    )
                }
            }

            // Ghi chú click vào bill cũ đã gộp
            if (isMergedOldBill) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "👉 Nhấn vào đây sẽ mở thẳng bill gộp chứa hóa đơn này",
                    fontSize = 11.sp,
                    color = SecondaryIndigo,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Huy hiệu tick trạng thái ngoài mặt bill
@Composable
fun StatusBadgeBox(
    isCompleted: Boolean,
    completedText: String,
    uncompletedText: String,
    activeColor: Color
) {
    Surface(
        color = if (isCompleted) activeColor.copy(alpha = 0.15f) else Color.LightGray.copy(alpha = 0.2f),
        shape = RoundedCornerShape(8.dp),
        border = if (isCompleted) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(activeColor)) else null
    ) {
        Text(
            text = if (isCompleted) completedText else uncompletedText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCompleted) activeColor else TextMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ==========================================
// [MỤC 1.9.3] DIALOG THÊM BILL MỚI (CHẾ ĐỘ NB1 / NB2, 4 QUYỀN VÀ XÉT LỊCH)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBillDialog(
    project: Project,
    currentUser: User,
    onDismiss: () -> Unit,
    onAddBill: (Bill) -> Unit
) {
    val allBills by AppRepository.bills.collectAsState()
    // Chọn quyền chỉnh sửa bill: chỉ người tạo hoặc cả nhóm.
    var selectedMode by remember { mutableStateOf(InputMode.NB1) }
    var manualAdvanceUserId by remember {
        mutableStateOf(
            project.memberIds.firstOrNull { it == currentUser.id }
                ?: project.memberIds.firstOrNull()
        )
    }
    var autoSelectAdvanceUser by remember(project.id) { mutableStateOf(true) }
    var selectedParticipantIds by remember(project.id, project.memberIds) {
        mutableStateOf(project.memberIds.toSet())
    }
    val automaticAdvanceUserId = remember(
        project.id,
        project.memberIds,
        selectedParticipantIds,
        allBills,
        currentUser.id
    ) {
        val participantDebts = selectedParticipantIds.associateWith { memberId ->
            allBills.asSequence()
                .filter { it.projectId == project.id }
                .sumOf { it.unpaidAmountFor(memberId) }
        }
        val highestDebt = participantDebts.values.maxOrNull()
        val highestDebtMembers = participantDebts
            .filterValues { it == highestDebt }
            .keys
            .toList()
        when {
            highestDebtMembers.isEmpty() -> null
            currentUser.id in highestDebtMembers -> currentUser.id
            else -> highestDebtMembers.random()
        }
    }
    val advanceUserId = if (autoSelectAdvanceUser) {
        automaticAdvanceUserId
    } else {
        manualAdvanceUserId?.takeIf { it in selectedParticipantIds }
    }
    var billTitle by remember { mutableStateOf("") }
    var billDate by remember { mutableLongStateOf(todayBillDateMillis()) }
    var showBillDatePicker by remember { mutableStateOf(false) }
    val billDatePickerState = rememberDatePickerState(initialSelectedDateMillis = billDate)
    var inputMethod by remember { mutableStateOf("Nhập tay") } // Chụp ảnh AI, Giọng nói AI, Nhập tay

    // Danh sách món đồ
    var itemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var savedItems by remember { mutableStateOf(emptyList<BillItem>()) }
    var inputError by remember { mutableStateOf<String?>(null) }
    val draftItem = remember(itemName, itemQty, itemPrice, selectedParticipantIds) {
        val quantity = itemQty.toIntOrNull()
        val price = itemPrice.toLongOrNull()
        if (itemName.isNotBlank() && quantity != null && quantity > 0 && price != null && price > 0L) {
            BillItem(
                name = itemName.trim(),
                quantity = quantity,
                unitPrice = price,
                payerUserIds = project.memberIds.filter { it in selectedParticipantIds }
            )
        } else {
            null
        }
    }

    // Điều chỉnh số tiền từng người (Mục 1.9.3 - Mặc định chia đều nhưng có thể chỉnh)
    var customSplitEnabled by remember { mutableStateOf(false) }
    val totalEstimated = savedItems.sumOf { it.totalPrice } + (draftItem?.totalPrice ?: 0L)
    val memberAmounts = remember(totalEstimated, project.memberIds, selectedParticipantIds) {
        mutableStateMapOf<String, String>().apply {
            val participants = project.memberIds.filter { it in selectedParticipantIds }
            val baseAmount = if (participants.isNotEmpty()) totalEstimated / participants.size else 0L
            val remainder = if (participants.isNotEmpty()) totalEstimated % participants.size else 0L
            project.memberIds.forEach { this[it] = "0" }
            participants.forEachIndexed { index, memberId ->
                this[memberId] = (baseAmount + if (index < remainder) 1L else 0L).toString()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm Bill vào ${project.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ==========================================
                // Quyền chỉnh sửa bill
                // ==========================================
                item {
                    Text("Ai có thể chỉnh sửa bill?", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == InputMode.NB1,
                            onClick = { selectedMode = InputMode.NB1 },
                            label = { Text("Chỉ người tạo") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedMode == InputMode.NB2,
                            onClick = { selectedMode = InputMode.NB2 },
                            label = { Text("Cả nhóm") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3 Phương thức nhập liệu
                item {
                    Text("Phương thức nhập liệu:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("📷 Chụp ảnh OCR", "🎤 Giọng nói AI", "✍️ Nhập tay").forEach { method ->
                            FilterChip(
                                selected = inputMethod == method,
                                onClick = {
                                    inputMethod = method
                                },
                                label = { Text(method, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Ai tham gia bill này?", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Mặc định tất cả thành viên đều được chọn.", fontSize = 11.sp, color = TextMedium)
                            project.memberIds.forEach { memberId ->
                                val member = AppRepository.getUserById(memberId)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedParticipantIds =
                                                if (memberId in selectedParticipantIds) {
                                                    selectedParticipantIds - memberId
                                                } else {
                                                    selectedParticipantIds + memberId
                                                }
                                        },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = memberId in selectedParticipantIds,
                                        onCheckedChange = { checked ->
                                            selectedParticipantIds =
                                                if (checked) selectedParticipantIds + memberId
                                                else selectedParticipantIds - memberId
                                        }
                                    )
                                    Text(
                                        if (memberId == currentUser.id) "${member?.name ?: "Bạn"} (bạn)"
                                        else member?.name ?: "Thành viên",
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PrimaryTealContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Ai ứng tiền bill này?",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = PrimaryTealDark
                                )
                                Text("Tự chọn", fontSize = 11.sp)
                                Switch(
                                    checked = autoSelectAdvanceUser,
                                    onCheckedChange = { autoSelectAdvanceUser = it }
                                )
                            }
                            if (autoSelectAdvanceUser) {
                                val selectedPayer = automaticAdvanceUserId?.let(AppRepository::getUserById)
                                Text(
                                    selectedPayer?.let { "Tự động chọn: ${it.name}" }
                                        ?: "Chọn ít nhất một người tham gia bill.",
                                    fontSize = 12.sp,
                                    color = PrimaryTealDark
                                )
                                Text(
                                    "Ưu tiên người đang nợ chưa trả nhiều nhất; nếu đồng hạng, ưu tiên người tạo bill, nếu không thì chọn ngẫu nhiên.",
                                    fontSize = 10.sp,
                                    color = TextMedium
                                )
                            } else {
                                project.memberIds.filter { it in selectedParticipantIds }.forEach { memberId ->
                                    val member = AppRepository.getUserById(memberId)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { manualAdvanceUserId = memberId },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = advanceUserId == memberId,
                                            onClick = { manualAdvanceUserId = memberId }
                                        )
                                        Text(
                                            if (memberId == currentUser.id) "${member?.name ?: "Bạn"} (bạn)" else member?.name ?: "Thành viên",
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        autoSelectAdvanceUser = false
                                        manualAdvanceUserId = null
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !autoSelectAdvanceUser && advanceUserId == null,
                                    onClick = {
                                        autoSelectAdvanceUser = false
                                        manualAdvanceUserId = null
                                    }
                                )
                                Column {
                                    Text("Chưa có ai ứng", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        "Khoản nợ sẽ được ghi cho từng thành viên theo số tiền đã chia.",
                                        fontSize = 11.sp,
                                        color = TextMedium
                                    )
                                }
                            }
                        }
                    }
                }

                // Tên bill & thông tin món
                item {
                    OutlinedTextField(
                        value = billTitle,
                        onValueChange = { billTitle = it; inputError = null },
                        label = { Text("Tên hóa đơn") },
                        placeholder = { InputPlaceholder("Nhập tên hóa đơn") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showBillDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Ngày có bill: ${billDateLabel(billDate)}")
                    }
                }

                item {
                    Text(
                        "Danh sách món trong bill (${savedItems.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = PrimaryTealDark
                    )
                    if (savedItems.isEmpty()) {
                        Text("Thêm ít nhất một món để tạo bill.", fontSize = 12.sp, color = TextMedium)
                    }
                    savedItems.forEachIndexed { index, savedItem ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(savedItem.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text(
                                        "${savedItem.quantity} × ${AppRepository.formatCurrency(savedItem.unitPrice)} = ${AppRepository.formatCurrency(savedItem.totalPrice)}",
                                        fontSize = 11.sp,
                                        color = TextMedium
                                    )
                                }
                                IconButton(onClick = {
                                    savedItems = savedItems.filterIndexed { itemIndex, _ -> itemIndex != index }
                                    inputError = null
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Xóa món", tint = ErrorRed)
                                }
                            }
                        }
                    }
                }

                item {
                    Text("Thêm món mới", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it; inputError = null },
                            label = { Text("Tên món") },
                            placeholder = { InputPlaceholder("Nhập tên món") },
                            modifier = Modifier.weight(2f),
                            colors = BillInputFieldColors(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = itemQty,
                            onValueChange = { itemQty = it.filter(Char::isDigit); inputError = null },
                            label = { Text("SL") },
                            placeholder = { InputPlaceholder("0") },
                            modifier = Modifier.weight(1f),
                            colors = BillInputFieldColors(),
                            singleLine = true
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { itemPrice = it.filter(Char::isDigit); inputError = null },
                        label = { Text("Đơn giá (VNĐ)") },
                        placeholder = { InputPlaceholder("Nhập đơn giá") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Tổng tiền bill: ${AppRepository.formatCurrency(totalEstimated)}",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTealDark,
                            fontSize = 14.sp
                        )
                        OutlinedButton(
                            onClick = {
                                when {
                                    itemName.isBlank() -> inputError = "Vui lòng nhập tên món."
                                    itemQty.toIntOrNull() == null || itemQty.toInt() <= 0 ->
                                        inputError = "Số lượng phải lớn hơn 0."
                                    itemPrice.toLongOrNull() == null || itemPrice.toLong() <= 0L ->
                                        inputError = "Đơn giá phải lớn hơn 0."
                                    else -> {
                                        savedItems = savedItems + draftItem!!
                                        itemName = ""
                                        itemQty = ""
                                        itemPrice = ""
                                        inputError = null
                                    }
                                }
                            },
                            enabled = draftItem != null
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Thêm món")
                        }
                    }
                }

                inputError?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                }

                // Điều chỉnh số tiền từng người (Mục 1.9.3)
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryTealContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Điều chỉnh số tiền từng người", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryTealDark)
                                Switch(checked = customSplitEnabled, onCheckedChange = { customSplitEnabled = it })
                            }

                            if (customSplitEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val selectedCount = selectedParticipantIds.size
                                Text(
                                    "Mặc định chia đều (${AppRepository.formatCurrency(if (selectedCount > 0) totalEstimated / selectedCount else 0L)}/người). Bạn có thể chỉnh sửa:",
                                    fontSize = 11.sp,
                                    color = TextDark
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                project.memberIds.filter { it in selectedParticipantIds }.forEach { memberId ->
                                    val user = AppRepository.getUserById(memberId)
                                    val currentAmount = memberAmounts[memberId] ?: "0"

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${user?.name}:", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        OutlinedTextField(
                                            value = currentAmount,
                                            onValueChange = { memberAmounts[memberId] = it.filter(Char::isDigit) },
                                            modifier = Modifier.width(120.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp),
                                            colors = BillInputFieldColors()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        if (selectedMode == InputMode.NB1) {
                            "Chỉ bạn được chỉnh sửa bill sau khi tạo."
                        } else {
                            "Mọi thành viên trong nhóm đều có thể chỉnh sửa bill."
                        },
                        fontSize = 11.sp,
                        color = PrimaryTealDark
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val participatingMembers = project.memberIds.filter { it in selectedParticipantIds }
                    val enteredAmounts = participatingMembers.associateWith { memberAmounts[it]?.toLongOrNull() }
                    val hasDraftInput = itemName.isNotBlank() || itemQty.isNotBlank() || itemPrice.isNotBlank()
                    when {
                        billTitle.isBlank() -> inputError = "Vui lòng nhập tên hóa đơn."
                        participatingMembers.isEmpty() -> inputError = "Hãy chọn ít nhất một người tham gia bill."
                        savedItems.isEmpty() -> inputError = "Vui lòng thêm ít nhất một món vào bill."
                        hasDraftInput && draftItem == null -> inputError = "Vui lòng nhập đầy đủ tên món, số lượng và đơn giá."
                        hasDraftInput -> inputError = "Bấm “Thêm món” để đưa món đang nhập vào bill."
                        customSplitEnabled && enteredAmounts.values.any { it == null } ->
                            inputError = "Vui lòng nhập số tiền hợp lệ cho từng thành viên."
                        customSplitEnabled && enteredAmounts.values.filterNotNull().sum() != totalEstimated ->
                            inputError = "Tổng số tiền chia cho thành viên phải bằng tổng bill."
                        else -> {
                            val total = totalEstimated
                            val baseSplit = total / participatingMembers.size
                            val remainder = total % participatingMembers.size
                            val amountByMember = project.memberIds.associateWith { 0L }.toMutableMap().apply {
                                participatingMembers.forEachIndexed { index, userId ->
                                    put(
                                        userId,
                                        if (customSplitEnabled) {
                                            enteredAmounts.getValue(userId)!!
                                        } else {
                                            baseSplit + if (index < remainder) 1L else 0L
                                        }
                                    )
                                }
                            }.toMap()
                            val newBill = Bill(
                                projectId = project.id,
                                title = billTitle.trim(),
                                creatorId = currentUser.id,
                                billDate = billDate,
                                advanceUserId = advanceUserId,
                                debtObject = if (advanceUserId == null) {
                                    BillDebtObject(ownerUserId = currentUser.id)
                                } else {
                                    null
                                },
                                mode = selectedMode,
                                nb2Permission = Nb2Permission(),
                                receiptImageUri = null,
                                items = savedItems.map { it.copy(payerUserIds = participatingMembers) },
                                memberStatuses = project.memberIds.associateWith { uId ->
                                    MemberBillStatus(
                                        userId = uId,
                                        itemCount = if (uId in selectedParticipantIds) savedItems.size else 0,
                                        amountOwed = amountByMember[uId] ?: 0L,
                                        isApproved = (uId == advanceUserId || (amountByMember[uId] ?: 0L) == 0L),
                                        isPaid = (uId == advanceUserId || (amountByMember[uId] ?: 0L) == 0L),
                                        isCreatorConfirmed = (uId == advanceUserId || (amountByMember[uId] ?: 0L) == 0L)
                                    )
                                }
                            )
                            onAddBill(newBill)
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text("Đăng Bill")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )

    if (showBillDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showBillDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        billDatePickerState.selectedDateMillis?.let { billDate = it }
                        showBillDatePicker = false
                    }
                ) { Text("Chọn ngày") }
            },
            dismissButton = {
                TextButton(onClick = { showBillDatePicker = false }) { Text("Hủy") }
            }
        ) {
            DatePicker(state = billDatePickerState, showModeToggle = true)
        }
    }
}

// ==========================================
// [MỤC 1.9.2.1] DIALOG LỌC BILL THEO NGÀY THÁNG CHO DỰ ÁN
// ==========================================
@Composable
fun ProjectDatePickerDialog(
    projectBills: List<Bill>,
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    // Lấy ngày có bill từ dự án này
    val billsDates = projectBills.map { billDateLabel(it.billDate) }.distinct()

    val today = billDateLabel(todayBillDateMillis())
    val yesterday = billDateLabel(todayBillDateMillis() - 86400000L)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lọc hóa đơn theo ngày tháng", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chọn ngày có bill trong dự án này:", fontSize = 12.sp, color = TextMedium)

                // Nút nhanh
                Button(
                    onClick = { onDateSelected(today) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTealContainer)
                ) {
                    Text("Hôm nay ($today) ${if (billsDates.contains(today)) "• Có bill" else ""}",
                         color = PrimaryTealDark, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onDateSelected(yesterday) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = BackgroundSlate)
                ) {
                    Text("Hôm qua ($yesterday) ${if (billsDates.contains(yesterday)) "• Có bill" else ""}",
                         color = TextDark)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text("Ngày khác có bill:", fontSize = 11.sp, color = TextMedium)

                // List các ngày có bill khác
                LazyColumn(
                    modifier = Modifier.height(150.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(billsDates.filter { it != today && it != yesterday }) { date ->
                        Button(
                            onClick = { onDateSelected(date) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = SurfaceWhite),
                            border = ButtonDefaults.outlinedButtonBorder()
                        ) {
                            Text(date, color = TextDark, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}
