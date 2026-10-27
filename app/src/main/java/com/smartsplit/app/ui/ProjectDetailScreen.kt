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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsplit.app.data.AppRepository
import com.smartsplit.app.data.Bill
import com.smartsplit.app.data.BillItem
import com.smartsplit.app.data.BudgetConfig
import com.smartsplit.app.data.InputMode
import com.smartsplit.app.data.MemberBillStatus
import com.smartsplit.app.data.Nb2Permission
import com.smartsplit.app.data.Project
import com.smartsplit.app.data.User
import com.smartsplit.app.ui.theme.*

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
    val projects by AppRepository.projects.collectAsState()
    val allBills by AppRepository.bills.collectAsState()
    val currentUserId by AppRepository.currentUserId.collectAsState()
    val currentUser = AppRepository.getCurrentUser()

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

    // State hiển thị Dialog
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showCreateBillDialog by remember { mutableStateOf(false) }

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
        floatingActionButton = {
            // Nút bấm thêm bill mới nhanh ở góc dưới
            ExtendedFloatingActionButton(
                onClick = { showCreateBillDialog = true },
                icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                text = { Text("Thêm Bill (NB1/NB2)", fontWeight = FontWeight.Bold) },
                containerColor = PrimaryTeal,
                contentColor = SurfaceWhite
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
            // [MỤC 1.9.1] SỐ TIỀN CÒN LẠI DỰ ĐỊNH CHO DỰ ÁN NÀY
            // ==========================================
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBudgetDialog = true },
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
                                text = "Hạn mức dự định cho [${project.name}]",
                                color = SurfaceWhite.copy(alpha = 0.9f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(Icons.Default.Edit, contentDescription = "Sửa hạn mức", tint = PrimaryTealLight, modifier = Modifier.size(16.dp))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = AppRepository.formatCurrency(project.budgetConfig.remainingAmount),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )

                        Text(
                            text = "Hạn mức tối đa: ${AppRepository.formatCurrency(project.budgetConfig.totalLimit)}",
                            fontSize = 12.sp,
                            color = SurfaceWhite.copy(alpha = 0.8f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = PrimaryTealLight, modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Cấp lại: Thủ công (Không tự động)",
                                    fontSize = 11.sp,
                                    color = PrimaryTealLight,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = PrimaryTeal,
                                modifier = Modifier.clickable {
                                    AppRepository.renewBudgetManually(projectId)
                                }
                            ) {
                                Text(
                                    text = "🔄 Cấp lại",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SurfaceWhite,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
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
                                text = "Gộp Bill & Xoay Vòng Tiền (Mục 1.9.5)",
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
                        placeholder = { Text("Tìm tên bill trong dự án này...", fontSize = 13.sp) },
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
                val matchSearch = searchQuery.isEmpty() || bill.title.contains(searchQuery, ignoreCase = true)
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
                    AppRepository.formatDate(bill.createdAt) == dateFilter
                } else {
                    true
                }
                matchSearch && matchApproval && matchPayment && matchDate
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

    // Dialog chỉnh sửa hạn mức riêng cho dự án này (Mục 1.9.1)
    if (showBudgetDialog) {
        BudgetConfigDialog(
            currentConfig = project.budgetConfig,
            onDismiss = { showBudgetDialog = false },
            onSave = { newLimit, newDays ->
                AppRepository.updateProjectBudget(projectId, newLimit, newDays)
                showBudgetDialog = false
            },
            onManualReset = {
                AppRepository.renewBudgetManually(projectId)
                showBudgetDialog = false
            }
        )
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
}

// ==========================================
// [MỤC 1.9.4] THẺ Ô BILL MẶT NGOÀI TRÊN DANH SÁCH DỰ ÁN
// ==========================================
@Composable
fun BillCardItem(
    bill: Bill,
    alpha: Float,
    isMergedOldBill: Boolean,
    onClick: () -> Unit
) {
    val creator = AppRepository.getUserById(bill.creatorId)

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
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Hàng 2: Ngày tạo & Người ứng tiền
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Ngày: ${AppRepository.formatDate(bill.createdAt)}",
                    fontSize = 11.sp,
                    color = TextLight
                )
                Text(
                    text = "Người ứng: ${creator?.name ?: "N/A"}",
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
                    text = "👉 Nhấn vào đây sẽ mở thẳng Bill Gộp chứa hóa đơn này (1.9.5)",
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
@Composable
fun CreateBillDialog(
    project: Project,
    currentUser: User,
    onDismiss: () -> Unit,
    onAddBill: (Bill) -> Unit
) {
    // Bước 1: Chọn Tab chế độ NB1 hoặc NB2
    var selectedMode by remember { mutableStateOf(InputMode.NB1) }
    var billTitle by remember { mutableStateOf("Đi siêu thị mua đồ chung") }
    var inputMethod by remember { mutableStateOf("Nhập tay") } // Chụp ảnh AI, Giọng nói AI, Nhập tay

    // Danh sách món đồ
    var itemName by remember { mutableStateOf("Trứng gà 10 quả") }
    var itemQty by remember { mutableStateOf("2") }
    var itemPrice by remember { mutableStateOf("32000") }

    // 4 Checkbox cấp quyền cho chế độ NB2 (Mục 1.9.3)
    var canEditName by remember { mutableStateOf(true) }
    var canEditQty by remember { mutableStateOf(true) }
    var canEditPrice by remember { mutableStateOf(true) }
    var canSelectItem by remember { mutableStateOf(true) }

    // Tab Xét lịch định kỳ (Mục 1.9.3)
    var isRecurring by remember { mutableStateOf(false) }
    var recurringDay by remember { mutableStateOf(5) } // [SỬA NHANH KHI VẤN ĐÁP] Mặc định ngày 05 hàng tháng

    // Điều chỉnh số tiền từng người (Mục 1.9.3 - Mặc định chia đều nhưng có thể chỉnh)
    var customSplitEnabled by remember { mutableStateOf(false) }
    val totalEstimated = remember(itemQty, itemPrice) {
        val qty = itemQty.toIntOrNull() ?: 1
        val price = itemPrice.toLongOrNull() ?: 0L
        qty * price
    }
    val defaultSplit = remember(totalEstimated, project.memberIds) {
        if (project.memberIds.isNotEmpty()) totalEstimated / project.memberIds.size else totalEstimated
    }
    val memberAmounts = remember { mutableMapOf<String, Long>() }
    project.memberIds.forEach { memberAmounts[it] = defaultSplit }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm Bill vào [${project.name}] (1.9.3)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ==========================================
                // BƯỚC 1: CHỌN TAB CHẾ ĐỘ NB1 HOẶC NB2
                // ==========================================
                item {
                    Text("Bước 1: Chọn Chế độ nhập liệu:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == InputMode.NB1,
                            onClick = { selectedMode = InputMode.NB1 },
                            label = { Text("NB1 (Cá nhân tạo)") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = selectedMode == InputMode.NB2,
                            onClick = { selectedMode = InputMode.NB2 },
                            label = { Text("NB2 (Cùng sửa)") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 3 Phương thức nhập liệu
                item {
                    Text("Phương thức nhập liệu:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("📷 Chụp ảnh OCR", "🎤 Giọng nói AI", "✍️ Nhập tay").forEach { method ->
                            FilterChip(
                                selected = inputMethod == method,
                                onClick = {
                                    inputMethod = method
                                    if (method.contains("OCR")) {
                                        itemName = "Hóa đơn siêu thị WinMart"
                                        itemPrice = "125000"
                                    } else if (method.contains("Giọng nói")) {
                                        itemName = "Lẩu gà lá é"
                                        itemPrice = "280000"
                                    }
                                },
                                label = { Text(method, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Tên bill & thông tin món
                item {
                    OutlinedTextField(
                        value = billTitle,
                        onValueChange = { billTitle = it },
                        label = { Text("Tên hóa đơn") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = itemName,
                            onValueChange = { itemName = it },
                            label = { Text("Tên món") },
                            modifier = Modifier.weight(2f)
                        )
                        OutlinedTextField(
                            value = itemQty,
                            onValueChange = { itemQty = it },
                            label = { Text("SL") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { itemPrice = it },
                        label = { Text("Đơn giá (VNĐ)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Hiển thị tổng tiền tạm tính
                val qty = itemQty.toIntOrNull() ?: 1
                val price = itemPrice.toLongOrNull() ?: 0L
                val total = qty * price
                item {
                    Text(
                        text = "Tổng tiền bill: ${AppRepository.formatCurrency(total)}",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryTealDark,
                        fontSize = 14.sp
                    )
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
                                Text("Mặc định chia đều (${AppRepository.formatCurrency(if (project.memberIds.isNotEmpty()) total / project.memberIds.size else total)}/người). Bạn có thể chỉnh sửa:", fontSize = 11.sp, color = TextDark)
                                Spacer(modifier = Modifier.height(6.dp))

                                project.memberIds.forEach { memberId ->
                                    val user = AppRepository.getUserById(memberId)
                                    val defaultAmount = if (project.memberIds.isNotEmpty()) total / project.memberIds.size else total
                                    val currentAmount = memberAmounts[memberId] ?: defaultAmount

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("${user?.name}:", fontSize = 12.sp, modifier = Modifier.weight(1f))
                                        OutlinedTextField(
                                            value = currentAmount.toString(),
                                            onValueChange = { memberAmounts[memberId] = it.toLongOrNull() ?: defaultAmount },
                                            modifier = Modifier.width(120.dp),
                                            singleLine = true,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // BƯỚC 2: QUY TẮC PHÂN QUYỀN NB1 / NB2
                // ==========================================
                if (selectedMode == InputMode.NB2) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = WarningAmberLight),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("4 Quyền cho thành viên trong NB2 (Mục 1.9.3):", fontWeight = FontWeight.Bold, color = WarningAmber, fontSize = 12.sp)

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = canEditName, onCheckedChange = { canEditName = it })
                                    Text("1. Cho phép sửa Tên món", fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = canEditQty, onCheckedChange = { canEditQty = it })
                                    Text("2. Cho phép sửa Số lượng", fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = canEditPrice, onCheckedChange = { canEditPrice = it })
                                    Text("3. Cho phép sửa Giá tiền", fontSize = 12.sp)
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = canSelectItem, onCheckedChange = { canSelectItem = it })
                                    Text("4. Cho phép tự chọn món mình trả", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            "Quy tắc NB1: Chỉ bạn có quyền chỉnh sửa. Mặc định tổng tiền sẽ chia đều cho cả ${project.memberIds.size} thành viên.",
                            fontSize = 11.sp,
                            color = PrimaryTealDark
                        )
                    }
                }

                // ==========================================
                // BƯỚC 3: TAB XÉT LỊCH ĐỊNH KỲ (MỤC 1.9.3)
                // ==========================================
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
                                Text("⏰ Xét lịch định kỳ hàng tháng", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryTealDark)
                                Switch(checked = isRecurring, onCheckedChange = { isRecurring = it })
                            }

                            if (isRecurring) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Tự động tạo bill này vào ngày $recurringDay hàng tháng (VD: Tiền phòng, tiền mạng):", fontSize = 11.sp, color = TextDark)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(1, 5, 10, 15, 25).forEach { day ->
                                        FilterChip(
                                            selected = recurringDay == day,
                                            onClick = { recurringDay = day },
                                            label = { Text("Ngày $day") }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = itemQty.toIntOrNull() ?: 1
                    val price = itemPrice.toLongOrNull() ?: 0L
                    val total = qty * price
                    val defaultSplit = if (project.memberIds.isNotEmpty()) total / project.memberIds.size else total

                    val newItem = BillItem(
                        name = itemName,
                        quantity = qty,
                        unitPrice = price,
                        payerUserIds = project.memberIds
                    )

                    val newBill = Bill(
                        projectId = project.id,
                        title = billTitle,
                        creatorId = currentUser.id,
                        mode = selectedMode,
                        nb2Permission = Nb2Permission(
                            canEditName = canEditName,
                            canEditQty = canEditQty,
                            canEditPrice = canEditPrice,
                            canSelectItem = canSelectItem
                        ),
                        receiptImageUri = if (inputMethod.contains("OCR")) "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=400" else null,
                        recurringDay = if (isRecurring) recurringDay else null,
                        items = listOf(newItem),
                        memberStatuses = project.memberIds.associateWith { uId ->
                            // Sử dụng số tiền tùy chỉnh nếu đã bật, ngược lại dùng chia đều
                            val amount = if (customSplitEnabled) {
                                memberAmounts[uId] ?: defaultSplit
                            } else {
                                defaultSplit
                            }
                            MemberBillStatus(
                                userId = uId,
                                itemCount = 1,
                                amountOwed = amount,
                                isApproved = (uId == currentUser.id), // Người tạo mặc định tự duyệt
                                isPaid = (uId == currentUser.id),
                                isCreatorConfirmed = (uId == currentUser.id)
                            )
                        }
                    )
                    onAddBill(newBill)
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
    val billsDates = projectBills.map {
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("vi", "VN")).format(java.util.Date(it.createdAt))
    }.distinct()

    val today = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("vi", "VN")).format(java.util.Date())
    val yesterday = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale("vi", "VN")).format(java.util.Date(System.currentTimeMillis() - 86400000L))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lọc hóa đơn theo Ngày tháng (1.9.2.1)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
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
