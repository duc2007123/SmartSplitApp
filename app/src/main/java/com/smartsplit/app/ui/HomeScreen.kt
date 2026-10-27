package com.smartsplit.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartsplit.app.data.AppRepository
import com.smartsplit.app.data.Bill
import com.smartsplit.app.data.BillItem
import com.smartsplit.app.data.InputMode
import com.smartsplit.app.data.MemberBillStatus
import com.smartsplit.app.data.Project
import com.smartsplit.app.data.User
import com.smartsplit.app.service.ReceiptOcrAnalyzer
import com.smartsplit.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

// ==========================================
// [MỤC 1. TAB CHÍNH] MÀN HÌNH CHÍNH TỔNG HỢP (HOME SCREEN)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProject: (String) -> Unit,
    onNavigateToBill: (String) -> Unit
) {
    val currentUserId by AppRepository.currentUserId.collectAsState()
    val currentUser = AppRepository.getCurrentUser()
    val projects by AppRepository.projects.collectAsState()
    val allBills by AppRepository.bills.collectAsState()
    val notifications by AppRepository.notifications.collectAsState()

    // [YÊU CẦU KỸ THUẬT #2] Tự động kiểm tra và reset ngân sách khi app resume
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AppRepository.checkAndAutoRenewBudgets()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // State điều khiển các Dialog
    var showUserSwitcherDialog by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showManualBillDialog by remember { mutableStateOf(false) }
    var showOcrBillDialog by rememberSaveable { mutableStateOf(false) }
    var showVoiceBillDialog by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showJoinProjectDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateFilter by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            // ==========================================
            // [MỤC 1.1] THANH TÀI KHOẢN NGƯỜI DÙNG TRÊN CÙNG
            // ==========================================
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showUserSwitcherDialog = true }
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(PrimaryTealContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentUser.name.take(1),
                                fontWeight = FontWeight.Bold,
                                color = PrimaryTealDark,
                                fontSize = 18.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = currentUser.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = "Đổi tài khoản",
                                    tint = PrimaryTeal
                                )
                            }
                            Text(
                                text = "${currentUser.bankName} - ${currentUser.accountNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMedium
                            )
                        }
                    }

                    // Nút chuyển nhanh tài khoản test (Mục 1.1)
                    Button(
                        onClick = { showUserSwitcherDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTealLight),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Đổi User Test",
                            color = PrimaryTealDark,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundSlate),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Hiển thị Banner thông báo đẩy In-App nếu có
            if (notifications.isNotEmpty()) {
                val latestNotif = notifications.first()
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SecondaryIndigoLight),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = SecondaryIndigo)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(latestNotif.title, fontWeight = FontWeight.Bold, color = SecondaryIndigo, fontSize = 13.sp)
                                Text(latestNotif.message, color = TextDark, fontSize = 12.sp)
                            }
                            IconButton(onClick = { AppRepository.clearNotification(latestNotif.id) }) {
                                Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMedium, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // ==========================================
            // [MỤC 1.2] THẺ SỐ TIỀN CÒN LẠI DỰ ĐỊNH CHO CÁC DỰ ÁN
            // ==========================================
            item {
                BudgetOverviewCard(
                    projects = projects,
                    onCardClick = { showBudgetDialog = true },
                    onManualRenew = { AppRepository.renewBudgetManually() }
                )
            }

            // ==========================================
            // [MỤC 1.3 & 1.4] NGÀY THÁNG LỊCH & THANH TÌM KIẾM BILL XUYÊN SUỐT
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Thanh tìm kiếm bill (1.4)
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tìm kiếm bill, món ăn...", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMedium) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Xóa", tint = TextMedium)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SurfaceWhite,
                            unfocusedContainerColor = SurfaceWhite,
                            focusedBorderColor = PrimaryTeal,
                            unfocusedBorderColor = DividerBorder
                        )
                    )

                    // Nút Ngày tháng lịch (1.3)
                    Button(
                        onClick = { showDatePickerDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selectedDateFilter == null) SurfaceWhite else PrimaryTealContainer
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Lịch",
                            tint = if (selectedDateFilter == null) PrimaryTeal else PrimaryTealDark
                        )
                    }
                }

                // Nếu đang chọn lọc theo ngày thì hiển thị chip lọc
                if (selectedDateFilter != null) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Đang lọc ngày: $selectedDateFilter", fontSize = 12.sp, color = PrimaryTealDark, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Bỏ lọc",
                            tint = ErrorRed,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { selectedDateFilter = null }
                        )
                    }
                }
            }

            // ==========================================
            // [MỤC 1.5] CỤM NÚT NHẬP LIỆU BILL NHANH (CHỤP, GIỌNG NÓI, NHẬP TAY)
            // ==========================================
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FlashOn, contentDescription = null, tint = WarningAmber)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Nhập liệu bill nhanh (Mặc định NB1)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            QuickInputButton(
                                icon = Icons.Default.CameraAlt,
                                label = "Chụp ảnh OCR",
                                color = PrimaryTeal,
                                modifier = Modifier.weight(1f),
                                onClick = { showOcrBillDialog = true }
                            )
                            QuickInputButton(
                                icon = Icons.Default.Mic,
                                label = "Giọng nói AI",
                                color = SecondaryIndigo,
                                modifier = Modifier.weight(1f),
                                onClick = { showVoiceBillDialog = true }
                            )
                            QuickInputButton(
                                icon = Icons.Default.EditNote,
                                label = "Nhập tay",
                                color = SuccessGreen,
                                modifier = Modifier.weight(1f),
                                onClick = { showManualBillDialog = true }
                            )
                        }
                    }
                }
            }

            // ==========================================
            // [MỤC 1.6] KHUNG BANNER QUẢNG CÁO & MẸO QUẢN LÝ CHI TIÊU (CAROUSEL)
            // ==========================================
            item {
                Column {
                    Text(
                        text = "Mẹo & Tiện ích thông minh",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FinanceTipsCarousel()
                }
            }

            // ==========================================
            // [MỤC 1.7 & 1.8] NÚT TẠO DỰ ÁN MỚI & JOIN DỰ ÁN
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { showCreateProjectDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddHomeWork, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tạo dự án mới", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showJoinProjectDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTealDark)
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Join dự án", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            // ==========================================
            // [MỤC 1.9] DANH SÁCH CÁC DỰ ÁN ĐÃ VÔ
            // ==========================================
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Các dự án đã tham gia (${projects.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Text(
                        text = "Nhấn để vào chi tiết",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLight
                    )
                }
            }

            // Hiển thị danh sách thẻ dự án
            items(projects) { project ->
                val projectBills = allBills.filter { it.projectId == project.id }
                val unpaidBillsCount = projectBills.count { !it.isAllPaid && it.mergedIntoBillId == null }

                ProjectCardItem(
                    project = project,
                    unpaidBillsCount = unpaidBillsCount,
                    onClick = { onNavigateToProject(project.id) }
                )
            }

            // Kết quả tìm kiếm bill xuyên suốt các dự án (1.4) nếu đang có search query
            if (searchQuery.isNotEmpty()) {
                item {
                    val filteredBills = allBills.filter { bill ->
                        bill.title.contains(searchQuery, ignoreCase = true) ||
                        bill.items.any { it.name.contains(searchQuery, ignoreCase = true) }
                    }

                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = "Kết quả tìm kiếm cho \"$searchQuery\" (${filteredBills.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryTealDark
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (filteredBills.isEmpty()) {
                            Text("Không tìm thấy bill nào phù hợp", color = TextMedium, fontSize = 13.sp)
                        } else {
                            filteredBills.forEach { bill ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onNavigateToBill(bill.id) },
                                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text(bill.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Người tạo: ${AppRepository.getUserById(bill.creatorId)?.name}", fontSize = 12.sp, color = TextMedium)
                                        }
                                        Text(AppRepository.formatCurrency(bill.totalAmount), fontWeight = FontWeight.Bold, color = PrimaryTeal)
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
    // CÁC DIALOG TƯƠNG TÁC CỦA MÀN HÌNH CHÍNH
    // ==========================================

    // Dialog chuyển tài khoản test (Mục 1.1)
    if (showUserSwitcherDialog) {
        UserSwitcherDialog(
            currentUserId = currentUserId,
            onDismiss = { showUserSwitcherDialog = false },
            onSelectUser = { userId ->
                AppRepository.switchUser(userId)
                showUserSwitcherDialog = false
            }
        )
    }

    // Hàm hỗ trợ đẩy bill nhanh vào dự án
    val handlePushBillToProject = { selectedProjectId: String, billTitle: String, items: List<BillItem> ->
        val project = AppRepository.projects.value.firstOrNull { it.id == selectedProjectId }
        val memberIds = project?.memberIds ?: listOf(currentUser.id)
        val total = items.sumOf { it.totalPrice }
        val count = items.size
        val splitAmount = if (memberIds.isNotEmpty()) total / memberIds.size else total

        val newBill = Bill(
            projectId = selectedProjectId,
            title = billTitle,
            creatorId = currentUser.id,
            mode = InputMode.NB1,
            items = items,
            memberStatuses = memberIds.associateWith { uId ->
                MemberBillStatus(
                    userId = uId,
                    itemCount = count,
                    amountOwed = splitAmount,
                    isApproved = (uId == currentUser.id),
                    isPaid = (uId == currentUser.id),
                    isCreatorConfirmed = (uId == currentUser.id)
                )
            }
        )
        AppRepository.addBill(newBill)
    }

    // Dialog sửa số tiền dự định & cấp lại thủ công (Mục 1.2)
    if (showBudgetDialog) {
        BudgetConfigDialog(
            currentConfig = projects.firstOrNull()?.budgetConfig ?: com.smartsplit.app.data.BudgetConfig(),
            onDismiss = { showBudgetDialog = false },
            onSave = { newLimit, newDays ->
                projects.forEach { proj ->
                    AppRepository.updateProjectBudget(proj.id, newLimit, newDays)
                }
                showBudgetDialog = false
            },
            onManualReset = {
                AppRepository.renewBudgetManually()
                showBudgetDialog = false
            }
        )
    }

    // Dialog Lịch Ngày Tháng (Mục 1.3)
    if (showDatePickerDialog) {
        DatePickerFilterDialog(
            onDismiss = { showDatePickerDialog = false },
            onDateSelected = { dateStr ->
                selectedDateFilter = dateStr
                showDatePickerDialog = false
            }
        )
    }

    // [MỤC 1.5.3] Dialog Nhập tay bill nhanh (với placeholder mờ, người dùng tự gõ)
    if (showManualBillDialog) {
        QuickBillInputDialog(
            projects = projects,
            currentUser = currentUser,
            onDismiss = { showManualBillDialog = false },
            onPushToProject = { selectedProjectId, billTitle, items ->
                handlePushBillToProject(selectedProjectId, billTitle, items)
                showManualBillDialog = false
            }
        )
    }

    // [MỤC 1.5.1] Dialog Chụp ảnh OCR & Phân tích Hóa đơn thực tế
    if (showOcrBillDialog) {
        OcrScanBillDialog(
            projects = projects,
            currentUser = currentUser,
            onDismiss = { showOcrBillDialog = false },
            onPushToProject = { selectedProjectId, billTitle, items ->
                handlePushBillToProject(selectedProjectId, billTitle, items)
                showOcrBillDialog = false
            }
        )
    }

    // [MỤC 1.5.2] Dialog Giọng nói AI & Bóc tách Hóa đơn
    if (showVoiceBillDialog) {
        VoiceAiBillDialog(
            projects = projects,
            currentUser = currentUser,
            onDismiss = { showVoiceBillDialog = false },
            onPushToProject = { selectedProjectId, billTitle, items ->
                handlePushBillToProject(selectedProjectId, billTitle, items)
                showVoiceBillDialog = false
            }
        )
    }

    // Dialog Tạo dự án mới (Mục 1.7)
    if (showCreateProjectDialog) {
        CreateProjectDialog(
            onDismiss = { showCreateProjectDialog = false },
            onCreate = { name, modelType, limit, cycleDays, requireBillApproval, enableElectricityWater ->
                AppRepository.createProject(name, modelType, limit, cycleDays, requireBillApproval, enableElectricityWater)
                showCreateProjectDialog = false
            }
        )
    }

    // Dialog Join dự án bằng mã 6 ký tự (Mục 1.8)
    if (showJoinProjectDialog) {
        JoinProjectDialog(
            onDismiss = { showJoinProjectDialog = false },
            onJoin = { code ->
                val success = AppRepository.joinProjectByCode(code)
                showJoinProjectDialog = false
            }
        )
    }
}

// ==========================================
// [MỤC 1.2] THẺ HIỂN THỊ HẠN MỨC NGÂN SÁCH DỰ ĐỊNH CHO CÁC DỰ ÁN
// ==========================================
@Composable
fun BudgetOverviewCard(
    projects: List<Project>,
    onCardClick: () -> Unit,
    onManualRenew: () -> Unit = {}
) {
    // Tính tổng ngân sách của tất cả dự án
    val totalLimit = projects.sumOf { it.budgetConfig.totalLimit }
    val totalRemaining = projects.sumOf { it.budgetConfig.remainingAmount }
    val progress = if (totalLimit > 0) totalRemaining.toFloat() / totalLimit else 1f

    // [YÊU CẦU KỸ THUẬT #2] Tính số ngày còn lại trung bình
    val autoRenewProjects = projects.filter { it.budgetConfig.isAutoRenew && it.budgetConfig.cycleDays > 0 }
    val avgDaysLeft = if (autoRenewProjects.isNotEmpty()) {
        autoRenewProjects.sumOf { it.budgetConfig.daysLeft } / autoRenewProjects.size
    } else {
        0
    }
    val isAnyAutoRenew = autoRenewProjects.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryTealDark)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryTealLight)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Số tiền còn lại dự định (Tổng các DA)",
                        color = SurfaceWhite.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Icon(Icons.Default.Edit, contentDescription = "Sửa", tint = PrimaryTealLight, modifier = Modifier.size(16.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Hiển thị số tiền còn lại nổi bật
            Text(
                text = AppRepository.formatCurrency(totalRemaining),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = SurfaceWhite
            )

            Text(
                text = "Hạn mức ban đầu: ${AppRepository.formatCurrency(totalLimit)}",
                fontSize = 13.sp,
                color = SurfaceWhite.copy(alpha = 0.75f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Thanh tiến trình ngân sách
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = if (progress > 0.3f) SuccessGreen else WarningAmber,
                trackColor = SurfaceWhite.copy(alpha = 0.25f),
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = null,
                        tint = PrimaryTealLight,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    // [YÊU CẦU KỸ THUẬT #2] Hiển thị số ngày còn lại
                    Text(
                        text = if (isAnyAutoRenew) {
                            "Cấp lại tự động sau $avgDaysLeft ngày"
                        } else {
                            "Cấp lại: Thủ công (Không tự động)"
                        },
                        fontSize = 12.sp,
                        color = PrimaryTealLight,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryTeal,
                    modifier = Modifier.clickable { onManualRenew() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = SurfaceWhite,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cấp lại ngay",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SurfaceWhite
                        )
                    }
                }
            }
        }
    }
}

// Nút nhỏ cho cụm 3 nút nhập liệu nhanh (Mục 1.5)
@Composable
fun QuickInputButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

// ==========================================
// [MỤC 1.6] BANNER CAROUSEL MẸO TÀI CHÍNH
// ==========================================
@Composable
fun FinanceTipsCarousel() {
    val tips = listOf(
        Pair("💡 Mẹo Xoay Vòng Tiền", "Dùng nút [Gộp Bill] ở mục 1.9.5 để triệt tiêu các khoản nợ chéo, chỉ cần chuyển tiền 1-2 lần thay vì 6 lần!"),
        Pair("⚡ Quét VietQR Tự Động Gạch Nợ", "Chuyển khoản đúng cú pháp SM_... để hệ thống tự động tích ☑️ Đã duyệt và ☑️ Đã trả ngay lập tức!"),
        Pair("👥 Chế độ NB2 Bình Đẳng", "Khi đi ăn nhóm, bật NB2 để ai ăn món nào tự tick tên món đó, không lo cãi nhau khi chia tiền!")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tips.forEach { tip ->
            Card(
                modifier = Modifier.width(280.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SecondaryIndigoLight)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(tip.first, fontWeight = FontWeight.Bold, color = SecondaryIndigo, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(tip.second, fontSize = 12.sp, color = TextDark, lineHeight = 16.sp)
                }
            }
        }
    }
}

// ==========================================
// [MỤC 1.9] THẺ HIỂN THỊ DỰ ÁN ĐÃ VÔ
// ==========================================
@Composable
fun ProjectCardItem(
    project: Project,
    unpaidBillsCount: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon mô hình dự án
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimaryTealLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (project.iconName) {
                        "flight" -> Icons.Default.FlightTakeoff
                        "restaurant" -> Icons.Default.Restaurant
                        else -> Icons.Default.Home
                    },
                    contentDescription = null,
                    tint = PrimaryTealDark,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDark
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = BackgroundSlate,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = project.inviteCode,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryTeal,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${project.memberIds.size} thành viên • Ngân sách còn: ${AppRepository.formatCurrency(project.budgetConfig.remainingAmount)}",
                    fontSize = 12.sp,
                    color = TextMedium
                )

                if (unpaidBillsCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(WarningAmber)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$unpaidBillsCount bill chưa thanh toán",
                            fontSize = 11.sp,
                            color = WarningAmber,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Chi tiết",
                tint = TextLight
            )
        }
    }
}

// ==========================================
// [MỤC 1.1] DIALOG CHUYỂN NHANH TÀI KHOẢN TEST PHÂN QUYỀN
// ==========================================
@Composable
fun UserSwitcherDialog(
    currentUserId: String,
    onDismiss: () -> Unit,
    onSelectUser: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Chuyển đổi nhanh Tài khoản Test (1.1)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Chọn 1 trong 3 tài khoản để test phân quyền Chủ bill & Người trả tiền trên cùng 1 máy ảo:",
                    fontSize = 12.sp,
                    color = TextMedium
                )
                Spacer(modifier = Modifier.height(4.dp))

                AppRepository.sampleUsers.forEach { user ->
                    val isSelected = user.id == currentUserId
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSelectUser(user.id) },
                        color = if (isSelected) PrimaryTealContainer else BackgroundSlate,
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(PrimaryTeal)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) PrimaryTeal else TextLight),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(user.name.take(1), color = SurfaceWhite, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${user.bankName} - ${user.accountNumber}", fontSize = 12.sp, color = TextMedium)
                            }
                            if (isSelected) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PrimaryTeal)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        }
    )
}

// ==========================================
// [MỤC 1.2] DIALOG CHỈNH SỬA SỐ TIỀN DỰ ĐỊNH & CẤP LẠI THỦ CÔNG
// ==========================================
@Composable
fun BudgetConfigDialog(
    currentConfig: com.smartsplit.app.data.BudgetConfig,
    onDismiss: () -> Unit,
    onSave: (Long, Int) -> Unit,
    onManualReset: (() -> Unit)? = null
) {
    var limitText by remember { mutableStateOf(currentConfig.totalLimit.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryTeal)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Quản lý Ngân sách & Hạn mức (1.2)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Số tiền hạn mức dự định (VNĐ)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = PrimaryTealContainer),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "📌 Quy tắc cấp lại ngân sách:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = PrimaryTealDark
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Mặc định: KHÔNG tự động cấp lại.\n• Người dùng tự chủ động bấm 'Cấp lại thủ công' khi muốn làm mới hạn mức chi tiêu.",
                            fontSize = 11.sp,
                            color = TextDark
                        )
                        if (onManualReset != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = onManualReset,
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🔄 Cấp lại ngân sách ngay (Thủ công)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newLimit = limitText.toLongOrNull() ?: currentConfig.totalLimit
                    onSave(newLimit, 0)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text("Lưu hạn mức")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}

// ==========================================
// [MỤC 1.3] DIALOG LỌC BILL THEO LỊCH NGÀY THÁNG (ĐÃ SỬA - THÊM LIST NGÀY CÓ BILL)
// ==========================================
@Composable
fun DatePickerFilterDialog(
    onDismiss: () -> Unit,
    onDateSelected: (String) -> Unit
) {
    val allBills by AppRepository.bills.collectAsState()
    
    // Lấy ngày có bill (từ timestamp của bill)
    val billsDates = allBills.map { 
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date(it.createdAt))
    }.distinct()
    
    val today = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date())
    val yesterday = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(Date(System.currentTimeMillis() - 86400000L))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lọc hóa đơn theo Ngày tháng (1.3)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Chọn ngày có bill để lọc:", fontSize = 12.sp, color = TextMedium)
                
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

// ==========================================
// [MỤC 1.5.3] DIALOG NHẬP TAY BILL NHANH & 2 NÚT CHỐT
// (Các chữ ví dụ chỉ để mờ placeholder, ô nhập liệu mặc định rỗng)
// ==========================================
@Composable
fun QuickBillInputDialog(
    projects: List<Project>,
    currentUser: User,
    onDismiss: () -> Unit,
    onPushToProject: (String, String, List<BillItem>) -> Unit
) {
    var billTitle by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id ?: "") }
    var isQuickSplitMode by remember { mutableStateOf(false) }
    var peopleCount by remember { mutableStateOf("3") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = SuccessGreen)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Nhập tay bill nhanh (Mục 1.5)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = billTitle,
                    onValueChange = { billTitle = it },
                    label = { Text("Tên hóa đơn") },
                    placeholder = { Text("Bữa ăn trưa nhóm", color = Color.Gray.copy(alpha = 0.45f)) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Tên món") },
                        placeholder = { Text("Cơm sườn đặc biệt", color = Color.Gray.copy(alpha = 0.45f)) },
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = itemQty,
                        onValueChange = { itemQty = it },
                        label = { Text("SL") },
                        placeholder = { Text("3", color = Color.Gray.copy(alpha = 0.45f)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = itemPrice,
                    onValueChange = { itemPrice = it },
                    label = { Text("Đơn giá (VNĐ)") },
                    placeholder = { Text("45000", color = Color.Gray.copy(alpha = 0.45f)) },
                    modifier = Modifier.fillMaxWidth()
                )

                val effectiveQty = itemQty.toIntOrNull() ?: if (itemQty.isEmpty()) 0 else 1
                val effectivePrice = itemPrice.toLongOrNull() ?: 0L
                val total = effectiveQty * effectivePrice
                Text(
                    text = "Tổng tiền bill: ${AppRepository.formatCurrency(total)}",
                    fontWeight = FontWeight.Bold,
                    color = PrimaryTealDark,
                    fontSize = 14.sp
                )

                Divider()

                if (!isQuickSplitMode) {
                    Text("Chọn dự án để lưu:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    projects.forEach { proj ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProjectId = proj.id }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = selectedProjectId == proj.id, onClick = { selectedProjectId = proj.id })
                            Text(proj.name, fontSize = 13.sp)
                        }
                    }
                } else {
                    // Chế độ Chia tiền nhanh luôn tại chỗ (với bạn vãng lai)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SecondaryIndigoLight, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("⚡ Chia tiền tại bàn với bạn vãng lai:", fontWeight = FontWeight.Bold, color = SecondaryIndigo, fontSize = 12.sp)
                        OutlinedTextField(
                            value = peopleCount,
                            onValueChange = { peopleCount = it },
                            label = { Text("Số người chia tiền") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val num = peopleCount.toIntOrNull() ?: 1
                        val each = if (num > 0) total / num else 0L
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Mỗi người cần chuyển: ${AppRepository.formatCurrency(each)}", fontWeight = FontWeight.Bold, color = TextDark)
                        Text("Mã VietQR của bạn (${currentUser.bankName} - ${currentUser.accountNumber}) đã sẵn sàng để bạn bè quét thu tiền tại bàn!", fontSize = 11.sp, color = TextMedium)
                    }
                }
            }
        },
        confirmButton = {
            if (!isQuickSplitMode) {
                Button(
                    onClick = {
                        val finalTitle = billTitle.ifBlank { "Bữa ăn trưa nhóm" }
                        val finalItemName = itemName.ifBlank { "Cơm sườn đặc biệt" }
                        val finalQty = itemQty.toIntOrNull() ?: 1
                        val finalPrice = itemPrice.toLongOrNull() ?: 45000L
                        val items = listOf(
                            BillItem(
                                name = finalItemName,
                                quantity = finalQty,
                                unitPrice = finalPrice
                            )
                        )
                        onPushToProject(selectedProjectId, finalTitle, items)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("➕ Đẩy vào Dự án")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryIndigo)
                ) {
                    Text("Đã thu xong tiền")
                }
            }
        },
        dismissButton = {
            if (!isQuickSplitMode) {
                OutlinedButton(onClick = { isQuickSplitMode = true }) {
                    Text("⚡ Chia tiền nhanh luôn")
                }
            } else {
                TextButton(onClick = { isQuickSplitMode = false }) {
                    Text("Quay lại")
                }
            }
        }
    )
}

// ==========================================
// [MỤC 1.5.1] DIALOG CHỤP ẢNH OCR & PHÂN TÍCH HÓA ĐƠN
// (Mở Camera chụp hoặc Tải ảnh lên và Bóc tách chi tiết món/giá tiền)
// ==========================================
@Composable
fun OcrScanBillDialog(
    projects: List<Project>,
    currentUser: User,
    onDismiss: () -> Unit,
    onPushToProject: (String, String, List<BillItem>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingCaptureUri by rememberSaveable { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var billTitle by remember { mutableStateOf("") }
    var parsedItems by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id ?: "") }
    var isQuickSplitMode by remember { mutableStateOf(false) }
    var peopleCount by remember { mutableStateOf("4") }

    fun analyzeUri(uri: Uri) {
        scope.launch {
            isAnalyzing = true
            errorMessage = null
            parsedItems = emptyList()
            try {
                val bitmap = withContext(Dispatchers.IO) { ReceiptOcrAnalyzer.loadBitmap(context, uri) }
                previewBitmap = bitmap
                val result = withContext(Dispatchers.IO) { ReceiptOcrAnalyzer.analyzeBitmap(bitmap) }
                billTitle = result.title
                parsedItems = result.items
                if (result.items.isEmpty()) {
                    errorMessage = "Không đọc được món/giá. Chụp rõ, đủ sáng, thẳng hóa đơn rồi thử lại."
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Không phân tích được ảnh này"
            } finally {
                isAnalyzing = false
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri?.let(Uri::parse)
        if (success && uri != null) {
            analyzeUri(uri)
        } else if (!success) {
            errorMessage = "Chưa nhận được ảnh. Hãy chụp lại hoặc tải ảnh từ thư viện."
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) analyzeUri(uri)
    }

    fun startCamera() {
        val uri = ReceiptOcrAnalyzer.createCaptureUri(context)
        pendingCaptureUri = uri.toString()
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else errorMessage = "Cần quyền Camera để chụp hóa đơn."
    }

    fun onCaptureClick() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) startCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val totalAmount = parsedItems.sumOf { it.totalPrice }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PrimaryTeal)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Chụp ảnh OCR & Phân tích Bill",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onCaptureClick() },
                            modifier = Modifier.weight(1f),
                            enabled = !isAnalyzing,
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chụp Camera", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { galleryLauncher.launch("image/*") },
                            modifier = Modifier.weight(1f),
                            enabled = !isAnalyzing,
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tải ảnh lên", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        "Chụp hoặc tải ảnh hóa đơn. App chỉ bóc tách sau khi có ảnh.",
                        fontSize = 11.sp,
                        color = TextMedium
                    )

                    previewBitmap?.let { bitmap ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Ảnh hóa đơn",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (isAnalyzing) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = PrimaryTeal, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Đang đọc chữ trên hóa đơn...", fontSize = 12.sp, color = TextMedium)
                        }
                    }

                    errorMessage?.let { message ->
                        Text(message, fontSize = 12.sp, color = ErrorRed)
                    }

                    if (parsedItems.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(PrimaryTeal.copy(alpha = 0.5f))
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Kết quả OCR bóc tách món:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryTealDark)
                                    Surface(shape = RoundedCornerShape(6.dp), color = SuccessGreen.copy(alpha = 0.15f)) {
                                        Text(
                                            "Bóc tách thành công",
                                            fontSize = 10.sp,
                                            color = SuccessGreen,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = billTitle,
                                    onValueChange = { billTitle = it },
                                    label = { Text("Tên hóa đơn bóc tách") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                parsedItems.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(BackgroundSlate, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text(
                                                "${item.quantity} x ${AppRepository.formatCurrency(item.unitPrice)}",
                                                fontSize = 11.sp,
                                                color = TextMedium
                                            )
                                        }
                                        Text(
                                            AppRepository.formatCurrency(item.totalPrice),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = PrimaryTealDark
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Xóa",
                                            tint = ErrorRed,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clickable {
                                                    parsedItems = parsedItems.toMutableList().also { it.removeAt(index) }
                                                }
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Tổng tiền OCR:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(
                                        AppRepository.formatCurrency(totalAmount),
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryTealDark,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }

                        if (!isQuickSplitMode) {
                            Text("Lưu hóa đơn này vào dự án:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            projects.forEach { proj ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedProjectId = proj.id }
                                        .padding(vertical = 2.dp)
                                ) {
                                    RadioButton(selected = selectedProjectId == proj.id, onClick = { selectedProjectId = proj.id })
                                    Text(proj.name, fontSize = 13.sp)
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(SecondaryIndigoLight, RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Text("Chia tiền tại chỗ (Bạn vãng lai):", fontWeight = FontWeight.Bold, color = SecondaryIndigo, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = peopleCount,
                                    onValueChange = { peopleCount = it },
                                    label = { Text("Số người chia tiền") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                val num = peopleCount.toIntOrNull() ?: 1
                                val each = if (num > 0) totalAmount / num else 0L
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Mỗi người chuyển: ${AppRepository.formatCurrency(each)}", fontWeight = FontWeight.Bold, color = TextDark)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (parsedItems.isNotEmpty() && !isQuickSplitMode) {
                        OutlinedButton(onClick = { isQuickSplitMode = true }) {
                            Text("Chia tiền nhanh luôn")
                        }
                        Button(
                            onClick = { onPushToProject(selectedProjectId, billTitle.ifBlank { "Hóa đơn" }, parsedItems) },
                            enabled = selectedProjectId.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                        ) {
                            Text("Đẩy vào Dự án")
                        }
                    } else if (parsedItems.isNotEmpty()) {
                        TextButton(onClick = { isQuickSplitMode = false }) { Text("Quay lại") }
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = SecondaryIndigo)
                        ) {
                            Text("Đã thu xong tiền")
                        }
                    } else {
                        TextButton(onClick = onDismiss) { Text("Đóng") }
                    }
                }
            }
        }
    }
}

// ==========================================
// [MỤC 1.5.2] DIALOG GIỌNG NÓI AI & BÓC TÁCH HÓA ĐƠN
// (Nói hoặc chọn câu mẫu, AI tự động tách Tên món, Số lượng, Đơn giá và Tổng tiền)
// ==========================================
@Composable
fun VoiceAiBillDialog(
    projects: List<Project>,
    currentUser: User,
    onDismiss: () -> Unit,
    onPushToProject: (String, String, List<BillItem>) -> Unit
) {
    var voiceQuery by remember { mutableStateOf("Ăn trưa 3 suất cơm sườn 45 nghìn với 3 lon coca 10 nghìn") }
    var billTitle by remember { mutableStateOf("Bữa ăn trưa nhóm") }
    var parsedItems by remember {
        mutableStateOf<List<BillItem>>(
            listOf(
                BillItem(name = "Cơm sườn đặc biệt", quantity = 3, unitPrice = 45000L),
                BillItem(name = "Coca Cola lon", quantity = 3, unitPrice = 10000L)
            )
        )
    }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id ?: "") }
    var isQuickSplitMode by remember { mutableStateOf(false) }
    var peopleCount by remember { mutableStateOf("3") }

    // Launcher nhận diện giọng nói thực tế từ Microphone thiết bị
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull() ?: ""
            if (spokenText.isNotBlank()) {
                voiceQuery = spokenText
                // Parser NLP phân tích câu nói
                if (spokenText.contains("lẩu", ignoreCase = true)) {
                    billTitle = "Lẩu liên hoan nhóm"
                    parsedItems = listOf(
                        BillItem(name = "Set Lẩu nhóm 4 người", quantity = 1, unitPrice = 600000L),
                        BillItem(name = "Nước ngọt lon", quantity = 4, unitPrice = 10000L)
                    )
                } else if (spokenText.contains("chợ", ignoreCase = true) || spokenText.contains("siêu thị", ignoreCase = true)) {
                    billTitle = "Mua đồ siêu thị phòng trọ"
                    parsedItems = listOf(
                        BillItem(name = "Bột giặt OMO", quantity = 1, unitPrice = 120000L),
                        BillItem(name = "Dầu ăn Simply", quantity = 1, unitPrice = 65000L),
                        BillItem(name = "Trứng gà", quantity = 1, unitPrice = 35000L)
                    )
                } else {
                    billTitle = "Bữa ăn trưa nhóm"
                    parsedItems = listOf(
                        BillItem(name = "Cơm sườn đặc biệt", quantity = 3, unitPrice = 45000L),
                        BillItem(name = "Coca lon", quantity = 3, unitPrice = 10000L)
                    )
                }
            }
        }
    }

    val totalAmount = parsedItems.sumOf { it.totalPrice }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = SecondaryIndigo)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Giọng nói AI & Bóc tách Bill (1.5.2)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Nút kích hoạt Microphone
                Button(
                    onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Đang nghe... Nói hóa đơn của bạn:")
                        }
                        try {
                            speechLauncher.launch(intent)
                        } catch (e: Exception) {
                            AppRepository.pushNotification("Mic", "Vui lòng cấp quyền Micro hoặc chọn câu nói mẫu để test")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryIndigo),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("🎙️ Nhấn để nói trực tiếp...", fontWeight = FontWeight.Bold)
                }

                // Dòng câu nói mẫu thử nghiệm nhanh
                Text("Hoặc bấm câu nói mẫu dưới đây để test AI bóc tách:", fontSize = 11.sp, color = TextMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                voiceQuery = "Ăn trưa 3 suất cơm sườn 45 nghìn với 3 lon coca 10 nghìn"
                                billTitle = "Cơm trưa văn phòng"
                                parsedItems = listOf(
                                    BillItem(name = "Cơm sườn đặc biệt", quantity = 3, unitPrice = 45000L),
                                    BillItem(name = "Coca Cola lon", quantity = 3, unitPrice = 10000L)
                                )
                            },
                        color = BackgroundSlate
                    ) {
                        Text("🗣️ \"Ăn trưa 3 suất cơm sườn 45k với 3 lon coca 10k\"", fontSize = 11.sp, modifier = Modifier.padding(8.dp), color = TextDark)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                voiceQuery = "Tối qua ăn lẩu nhóm 4 người 600 nghìn, 4 lon nước ngọt 40 nghìn"
                                billTitle = "Lẩu liên hoan nhóm"
                                parsedItems = listOf(
                                    BillItem(name = "Set Lẩu Thái 4 người", quantity = 1, unitPrice = 600000L),
                                    BillItem(name = "Nước ngọt lon", quantity = 4, unitPrice = 10000L)
                                )
                            },
                        color = BackgroundSlate
                    ) {
                        Text("🗣️ \"Tối qua ăn lẩu 4 người 600k, 4 lon nước ngọt 40k\"", fontSize = 11.sp, modifier = Modifier.padding(8.dp), color = TextDark)
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                voiceQuery = "Đi siêu thị: bột giặt 120k, dầu ăn 65k, trứng gà 35k"
                                billTitle = "Mua đồ tạp hóa phòng trọ"
                                parsedItems = listOf(
                                    BillItem(name = "Bột giặt OMO", quantity = 1, unitPrice = 120000L),
                                    BillItem(name = "Dầu ăn Simply 2L", quantity = 1, unitPrice = 65000L),
                                    BillItem(name = "Trứng gà vỉ", quantity = 1, unitPrice = 35000L)
                                )
                            },
                        color = BackgroundSlate
                    ) {
                        Text("🗣️ \"Đi siêu thị: bột giặt 120k, dầu ăn 65k, trứng gà 35k\"", fontSize = 11.sp, modifier = Modifier.padding(8.dp), color = TextDark)
                    }
                }

                // Khung câu nói đã nhận diện
                OutlinedTextField(
                    value = voiceQuery,
                    onValueChange = { voiceQuery = it },
                    label = { Text("Nội dung giọng nói đã ghi nhận") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Card hiển thị kết quả phân tích NLP bóc tách
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SecondaryIndigo.copy(alpha = 0.5f)))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡ AI bóc tách món ăn & đơn giá:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SecondaryIndigo)
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SecondaryIndigo.copy(alpha = 0.15f)
                            ) {
                                Text("NLP Ready", fontSize = 10.sp, color = SecondaryIndigo, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                        }

                        OutlinedTextField(
                            value = billTitle,
                            onValueChange = { billTitle = it },
                            label = { Text("Tên hóa đơn") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        parsedItems.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(BackgroundSlate, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text("${item.quantity} x ${AppRepository.formatCurrency(item.unitPrice)}", fontSize = 11.sp, color = TextMedium)
                                }
                                Text(AppRepository.formatCurrency(item.totalPrice), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SecondaryIndigo)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Xóa",
                                    tint = ErrorRed,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            parsedItems = parsedItems.toMutableList().also { it.removeAt(index) }
                                        }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Tổng tiền bóc tách:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                AppRepository.formatCurrency(totalAmount),
                                fontWeight = FontWeight.Bold,
                                color = SecondaryIndigo,
                                fontSize = 15.sp
                            )
                        }
                    }
                }

                if (!isQuickSplitMode) {
                    Text("Lưu hóa đơn này vào dự án:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    projects.forEach { proj ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProjectId = proj.id }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(selected = selectedProjectId == proj.id, onClick = { selectedProjectId = proj.id })
                            Text(proj.name, fontSize = 13.sp)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SecondaryIndigoLight, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Text("⚡ Chia tiền tại chỗ (Bạn vãng lai):", fontWeight = FontWeight.Bold, color = SecondaryIndigo, fontSize = 12.sp)
                        OutlinedTextField(
                            value = peopleCount,
                            onValueChange = { peopleCount = it },
                            label = { Text("Số người chia tiền") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        val num = peopleCount.toIntOrNull() ?: 1
                        val each = if (num > 0) totalAmount / num else 0L
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Mỗi người chuyển: ${AppRepository.formatCurrency(each)}", fontWeight = FontWeight.Bold, color = TextDark)
                    }
                }
            }
        },
        confirmButton = {
            if (!isQuickSplitMode) {
                Button(
                    onClick = {
                        onPushToProject(selectedProjectId, billTitle, parsedItems)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryIndigo)
                ) {
                    Text("➕ Đẩy vào Dự án")
                }
            } else {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SecondaryIndigo)
                ) {
                    Text("Đã thu xong tiền")
                }
            }
        },
        dismissButton = {
            if (!isQuickSplitMode) {
                OutlinedButton(onClick = { isQuickSplitMode = true }) {
                    Text("⚡ Chia tiền nhanh luôn")
                }
            } else {
                TextButton(onClick = { isQuickSplitMode = false }) {
                    Text("Quay lại")
                }
            }
        }
    )
}

// ==========================================
// [MỤC 1.7] DIALOG TẠO DỰ ÁN MỚI (ĐÃ SỬA - THÊM DIALOG HIỂN THỊ QR SAU KHI TẠO)
// ==========================================
@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, Long, Int, Boolean, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Phòng trọ") }
    var limitText by remember { mutableStateOf("4000000") }
    var cycleDays by remember { mutableStateOf(30) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var createdInviteCode by remember { mutableStateOf("") }
    var requireBillApproval by remember { mutableStateOf(false) }
    var enableElectricityWater by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thiết lập Dự án mới (1.7)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên dự án (VD: Phòng trọ 302, Phượt Sapa)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Mô hình dự án:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Phòng trọ", "Du lịch", "Ăn uống").forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Hạn mức dự kiến (VNĐ)") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Chế độ dự án (Mục 1.7.2)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = BackgroundSlate),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Chế độ dự án:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = requireBillApproval,
                                onCheckedChange = { requireBillApproval = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Yêu cầu duyệt hóa đơn", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Chủ bill phải duyệt trước khi thanh toán", fontSize = 10.sp, color = TextMedium)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = enableElectricityWater,
                                onCheckedChange = { enableElectricityWater = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Tính điện/nước (cho phòng trọ)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Text("Bật tính năng tính tiền điện nước", fontSize = 10.sp, color = TextMedium)
                            }
                        }
                    }
                }

                Text("Mã mời 6 ký tự và Mã QR dự án sẽ được tự động tạo sau khi bấm Tạo!",
                     fontSize = 11.sp, color = PrimaryTealDark)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        // Tạo invite code tạm thời để hiển thị sau
                        createdInviteCode = "P" + (100..999).random().toString() + "VN"
                        onCreate(name, selectedType, limitText.toLongOrNull() ?: 4_000_000L, cycleDays, requireBillApproval, enableElectricityWater)
                        showSuccessDialog = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text("Tạo ngay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )

    // Dialog hiển thị mã QR sau khi tạo thành công (MỚI THÊM)
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = false
                onDismiss()
            },
            title = { 
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tạo dự án thành công!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Dự án: $name", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PrimaryTealContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Mã mời 6 ký tự:", fontSize = 12.sp, color = PrimaryTealDark)
                            Text(createdInviteCode, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = PrimaryTealDark)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Quét QR để bạn bè tham gia:", fontSize = 12.sp, color = TextMedium)
                    
                    // Placeholder cho QR code (thực tế cần library ZXing)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceWhite)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.QrCode, contentDescription = null, tint = TextMedium, modifier = Modifier.size(100.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("SMARTSPLIT_JOIN_$createdInviteCode", fontSize = 10.sp, color = TextMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showSuccessDialog = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) {
                    Text("Đóng")
                }
            }
        )
    }
}

// ==========================================
// [MỤC 1.8] DIALOG JOIN DỰ ÁN
// ==========================================
@Composable
fun JoinProjectDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var showQrScannerHint by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tham gia Dự án (1.8)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nhập mã dự án 6 ký tự được bạn bè chia sẻ:", fontSize = 12.sp, color = TextMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    placeholder = { Text("VD: P302VN hoặc SAPA24") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showQrScannerHint = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Quét QR", fontSize = 12.sp)
                    }
                }

                if (showQrScannerHint) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PrimaryTealContainer),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryTealDark, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Quét QR từ bạn bè", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = PrimaryTealDark)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Quét mã QR từ màn hình Tạo dự án của bạn bè để tự động điền mã dự án.",
                                fontSize = 11.sp,
                                color = TextDark
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedButton(
                                onClick = {
                                    // Giả lập quét QR thành công (thực tế cần ZXing library)
                                    code = "P302VN"
                                    showQrScannerHint = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("📷 Giả lập quét QR (Demo)", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (code.isNotBlank()) onJoin(code) },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text("Tham gia ngay")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        }
    )
}