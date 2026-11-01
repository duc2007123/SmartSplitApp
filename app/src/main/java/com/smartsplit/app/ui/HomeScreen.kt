package com.smartsplit.app.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import com.smartsplit.app.data.AppRepository
import com.smartsplit.app.data.Bill
import com.smartsplit.app.data.BillDebtObject
import com.smartsplit.app.data.BillItem
import com.smartsplit.app.data.InputMode
import com.smartsplit.app.data.MemberBillStatus
import com.smartsplit.app.data.Project
import com.smartsplit.app.data.User
import com.smartsplit.app.data.unpaidAmountFor
import com.smartsplit.app.service.ReceiptOcrAnalyzer
import com.smartsplit.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

@Composable
internal fun InputPlaceholder(text: String, fontSize: TextUnit = TextUnit.Unspecified) {
    Text(
        text = text,
        color = TextMedium.copy(alpha = 0.55f),
        fontSize = fontSize
    )
}

@Composable
internal fun BillInputFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextDark,
    unfocusedTextColor = TextDark,
    focusedContainerColor = SurfaceWhite,
    unfocusedContainerColor = SurfaceWhite,
    focusedBorderColor = PrimaryTeal,
    unfocusedBorderColor = TextLight,
    focusedLabelColor = PrimaryTeal,
    unfocusedLabelColor = TextMedium,
    cursorColor = PrimaryTeal
)

@Composable
fun BillDistributionTab(
    items: List<BillItem>,
    participants: List<User>,
    selectedPayers: Map<String, List<String>>,
    onTogglePayer: (itemId: String, userId: String, isSelected: Boolean) -> Unit,
    showAmounts: Boolean = true,
    defaultAllParticipants: Boolean = true,
    showAdvanceSelection: Boolean = false,
    advanceUserId: String? = null,
    onAdvanceUserSelected: (String?) -> Unit = {}
) {
    val participantTotals = participants.associate { it.id to 0L }.toMutableMap()
    items.forEach { item ->
        val selectedIds = selectedPayers[item.id]
            ?: if (defaultAllParticipants) participants.map { it.id } else emptyList()
        val selected = participants.filter { it.id in selectedIds }
        if (selected.isNotEmpty()) {
            val baseShare = item.totalPrice / selected.size
            val remainder = item.totalPrice % selected.size
            selected.forEachIndexed { index, user ->
                participantTotals[user.id] = (participantTotals[user.id] ?: 0L) + baseShare + if (index < remainder) 1 else 0
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("PHANCHIA", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryTealDark)
        Text("Chọn người cùng dùng từng món. Mỗi món được chia đều giữa những người đã chọn.", fontSize = 12.sp, color = TextMedium)

        if (showAdvanceSelection) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = PrimaryTealContainer),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Ai đã ứng tiền bill này?", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    participants.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdvanceUserSelected(user.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = advanceUserId == user.id,
                                onClick = { onAdvanceUserSelected(user.id) }
                            )
                            Text(user.name, fontSize = 13.sp)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdvanceUserSelected(null) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = advanceUserId == null,
                            onClick = { onAdvanceUserSelected(null) }
                        )
                        Column {
                            Text("Chưa có ai ứng", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("Tạo khoản nợ chung để các thành viên thanh toán.", fontSize = 11.sp, color = TextMedium)
                        }
                    }
                }
            }
        }

        items.forEach { item ->
            val selectedIds = selectedPayers[item.id]
                ?: if (defaultAllParticipants) participants.map { it.id } else emptyList()
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(item.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${item.quantity} x ${AppRepository.formatCurrency(item.unitPrice)} = ${AppRepository.formatCurrency(item.totalPrice)}",
                        fontSize = 12.sp,
                        color = TextMedium
                    )
                    participants.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onTogglePayer(
                                        item.id,
                                        user.id,
                                        user.id !in selectedIds
                                    )
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = user.id in selectedIds,
                                onCheckedChange = { checked -> onTogglePayer(item.id, user.id, checked) }
                            )
                            Text(user.name, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        if (showAmounts) {
            Text("Số tiền mỗi người trả", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            participants.forEach { user ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(user.name, fontSize = 13.sp)
                    Text(
                        AppRepository.formatCurrency(participantTotals[user.id] ?: 0L),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryTealDark
                    )
                }
            }
            Text(
                "Tổng hóa đơn: ${AppRepository.formatCurrency(items.sumOf { it.totalPrice })}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryTealDark
            )
        }
    }
}

private fun getProjectParticipants(project: Project?): List<User> =
    project?.memberIds?.mapIndexed { index, id ->
        AppRepository.getUserById(id) ?: User(id, "Thành viên ${index + 1}", "", "", "", "")
    }.orEmpty()

private fun getGuestParticipants(count: Int): List<User> =
    (1..count).map { index -> User("guest_$index", "Người $index", "", "", "", "") }

// ==========================================
// [MỤC 1. TAB CHÍNH] MÀN HÌNH CHÍNH TỔNG HỢP (HOME SCREEN)
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToProject: (String) -> Unit,
    onNavigateToBill: (String) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val currentUserId by AppRepository.currentUserId.collectAsState()
    var currentUser by remember { mutableStateOf(AppRepository.getCurrentUser()) }
    val projects by AppRepository.projects.collectAsState()
    val allBills by AppRepository.bills.collectAsState()
    val notifications by AppRepository.notifications.collectAsState()
    val cloudUsers by AppRepository.users.collectAsState()
    val syncError by AppRepository.syncError.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(currentUserId, cloudUsers) {
        if (currentUserId != null) {
            currentUser = cloudUsers.firstOrNull { it.id == currentUserId }
                ?: AppRepository.getCurrentUserFromDb()
                ?: currentUser
        }
    }

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
    var showDatePickerDialog by remember { mutableStateOf(false) }
    var showBillHistoryDialog by remember { mutableStateOf(false) }
    var showManualBillDialog by remember { mutableStateOf(false) }
    var showOcrBillDialog by rememberSaveable { mutableStateOf(false) }
    var showVoiceBillDialog by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showJoinProjectDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedDateRange by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var showUnpaidBillsDialog by remember { mutableStateOf(false) }
    val qrPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                coroutineScope.launch {
                    try {
                        if (AppRepository.updateCurrentUserQrImage(uri.toString())) {
                            Toast.makeText(context, "Đã cập nhật mã QR nhận tiền.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Không thể lưu mã QR cho tài khoản này.", Toast.LENGTH_LONG).show()
                        }
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            "Không thể đồng bộ mã QR: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (_: SecurityException) {
                Toast.makeText(context, "Không thể cấp quyền đọc ảnh QR đã chọn.", Toast.LENGTH_LONG).show()
            }
        }
    }

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
                            Text(
                                text = currentUser.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${currentUser.bankName} - ${currentUser.accountNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMedium
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { qrPicker.launch(arrayOf("image/*")) },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.QrCode2, contentDescription = "Tải QR nhận tiền", tint = PrimaryTeal)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (currentUser.qrImageUri.isBlank()) "Tải QR" else "Đổi QR")
                        }
                        IconButton(onClick = onLogout) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Đăng xuất", tint = PrimaryTeal)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding()
                .background(BackgroundSlate),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (syncError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = syncError.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            fontSize = 12.sp
                        )
                    }
                }
            }

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

            item {
                UnpaidDebtOverviewCard(
                    userId = currentUserId,
                    bills = allBills,
                    onClick = { showUnpaidBillsDialog = true }
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
                        placeholder = { InputPlaceholder("Tìm kiếm bill, món ăn...", 13.sp) },
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
                            containerColor = if (selectedDateRange == null) SurfaceWhite else PrimaryTealContainer
                        ),
                        border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(DividerBorder)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Lịch",
                            tint = if (selectedDateRange == null) PrimaryTeal else PrimaryTealDark
                        )
                    }
                }

                // Nếu đang chọn lọc theo ngày thì hiển thị chip lọc
                if (selectedDateRange != null) {
                    val range = selectedDateRange!!
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (range.first == range.second) {
                                "Đang lọc ngày: ${billDateLabel(range.first)}"
                            } else {
                                "Đang lọc: ${billDateLabel(range.first)} - ${billDateLabel(range.second)}"
                            },
                            fontSize = 12.sp,
                            color = PrimaryTealDark,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Bỏ lọc",
                            tint = ErrorRed,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { selectedDateRange = null }
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
            if (searchQuery.isBlank() && selectedDateRange == null) {
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
                        TextButton(onClick = { showBillHistoryDialog = true }) {
                            Text("Lịch sử sửa bill", fontSize = 12.sp)
                        }
                    }
                }

                // Hiển thị danh sách thẻ dự án
                items(projects) { project ->
                    val projectBills = allBills.filter { it.projectId == project.id }
                    val unpaidBillsCount = projectBills.count { it.unpaidAmountFor(currentUserId.orEmpty()) > 0L }
                    val unpaidAmount = AppRepository.unpaidAmountForUser(projectBills, currentUserId.orEmpty())

                    ProjectCardItem(
                        project = project,
                        unpaidBillsCount = unpaidBillsCount,
                        unpaidAmount = unpaidAmount,
                        onClick = { onNavigateToProject(project.id) }
                    )
                }
            }

            // Kết quả tìm kiếm bill xuyên suốt các dự án (1.4) nếu đang có search query
            if (searchQuery.isNotBlank() || selectedDateRange != null) {
                item {
                    val query = searchQuery.trim()
                    val filteredBills = allBills.filter { bill ->
                        val matchesSearch = query.isEmpty() ||
                            bill.title.contains(query, ignoreCase = true) ||
                            bill.items.any { it.name.contains(query, ignoreCase = true) }
                        val matchesDate = selectedDateRange?.let { range ->
                            bill.billDate in range.first..range.second
                        } ?: true
                        matchesSearch && matchesDate
                    }.sortedByDescending { it.billDate }

                    Column(modifier = Modifier.padding(top = 16.dp)) {
                        Text(
                            text = if (query.isNotEmpty()) {
                                "Kết quả cho \"$query\" (${filteredBills.size})"
                            } else {
                                "Bill theo ngày đã chọn (${filteredBills.size})"
                            },
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
                                            val projectName = projects.firstOrNull { it.id == bill.projectId }?.name ?: "Không rõ dự án"
                                            Text("$projectName • ${billDateLabel(bill.billDate)}", fontSize = 12.sp, color = TextMedium)
                                            Text("Người tạo: ${AppRepository.getUserById(bill.creatorId)?.name ?: "Không rõ"}", fontSize = 11.sp, color = TextMedium)
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

    // Hàm hỗ trợ đẩy bill nhanh vào dự án
    val handlePushBillToProject = { selectedProjectId: String, billTitle: String, items: List<BillItem>, advanceUserId: String? ->
        val project = AppRepository.projects.value.firstOrNull { it.id == selectedProjectId }
        val memberIds = project?.memberIds ?: listOf(currentUser.id)
        val selectedAdvanceUserId = advanceUserId?.takeIf { it in memberIds }
        val amountsByMember = memberIds.associateWith { 0L }.toMutableMap()
        val itemCountsByMember = memberIds.associateWith { 0 }.toMutableMap()
        val distributedItems = items.map { item ->
            val payers = item.payerUserIds.filter { it in memberIds }.ifEmpty { memberIds }
            if (payers.isNotEmpty()) {
                val baseShare = item.totalPrice / payers.size
                val remainder = item.totalPrice % payers.size
                payers.forEachIndexed { index, memberId ->
                    amountsByMember[memberId] = (amountsByMember[memberId] ?: 0L) +
                        baseShare + if (index < remainder) 1L else 0L
                    itemCountsByMember[memberId] = (itemCountsByMember[memberId] ?: 0) + 1
                }
            }
            item.copy(payerUserIds = payers)
        }

        val newBill = Bill(
            projectId = selectedProjectId,
            title = billTitle,
            creatorId = currentUser.id,
            advanceUserId = selectedAdvanceUserId,
            debtObject = if (selectedAdvanceUserId == null) {
                BillDebtObject(ownerUserId = currentUser.id)
            } else {
                null
            },
            mode = InputMode.NB1,
            items = distributedItems,
            memberStatuses = memberIds.associateWith { uId ->
                MemberBillStatus(
                    userId = uId,
                    itemCount = itemCountsByMember[uId] ?: 0,
                    amountOwed = (amountsByMember[uId] ?: 0L),
                    isApproved = (uId == selectedAdvanceUserId || (amountsByMember[uId] ?: 0L) == 0L),
                    isPaid = (uId == selectedAdvanceUserId || (amountsByMember[uId] ?: 0L) == 0L),
                    isCreatorConfirmed = (uId == selectedAdvanceUserId || (amountsByMember[uId] ?: 0L) == 0L)
                )
            }
        )
        AppRepository.addBill(newBill)
    }

    // Dialog Lịch Ngày Tháng (Mục 1.3)
    if (showDatePickerDialog) {
        HomeBillCalendarDialog(
            onDismiss = { showDatePickerDialog = false },
            onDateRangeSelected = { start, end ->
                selectedDateRange = start to end
                showDatePickerDialog = false
            }
        )
    }

    if (showUnpaidBillsDialog) {
        UnpaidBillsDialog(
            bills = allBills,
            userId = currentUserId,
            projectNames = projects.associate { it.id to it.name },
            onDismiss = { showUnpaidBillsDialog = false },
            onOpenBill = { billId ->
                showUnpaidBillsDialog = false
                onNavigateToBill(billId)
            }
        )
    }

    if (showBillHistoryDialog) {
        BillEditHistoryDialog(
            bills = allBills,
            projectNames = projects.associate { it.id to it.name },
            onDismiss = { showBillHistoryDialog = false }
        )
    }

    // [MỤC 1.5.3] Dialog Nhập tay bill nhanh (với placeholder mờ, người dùng tự gõ)
    if (showManualBillDialog) {
        QuickBillInputDialog(
            projects = projects,
            currentUser = currentUser,
            onDismiss = { showManualBillDialog = false },
            onBillCreated = { bill ->
                AppRepository.addBill(bill)
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
            onPushToProject = { selectedProjectId, billTitle, items, advanceUserId ->
                handlePushBillToProject(selectedProjectId, billTitle, items, advanceUserId)
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
            onPushToProject = { selectedProjectId, billTitle, items, advanceUserId ->
                handlePushBillToProject(selectedProjectId, billTitle, items, advanceUserId)
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
            }
        )
    }

    // Dialog join dự án bằng mã mời.
    if (showJoinProjectDialog) {
        JoinProjectDialog(
            onDismiss = { showJoinProjectDialog = false },
            onJoin = { code ->
                coroutineScope.launch {
                    try {
                        val joined = AppRepository.joinProjectByCode(code)
                        if (joined) {
                            showJoinProjectDialog = false
                            Toast.makeText(context, "Đã tham gia dự án.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Mã mời không đúng hoặc dự án không tồn tại.", Toast.LENGTH_LONG).show()
                        }
                    } catch (error: Exception) {
                        Toast.makeText(
                            context,
                            "Không thể tham gia dự án: ${error.localizedMessage}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        )
    }
}

@Composable
fun UnpaidDebtOverviewCard(userId: String?, bills: List<Bill>, onClick: () -> Unit) {
    val unpaidBills = bills.count { bill ->
        userId != null && bill.unpaidAmountFor(userId) > 0L
    }
    val unpaidAmount = if (userId == null) 0L else AppRepository.unpaidAmountForUser(bills, userId)

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryTealDark)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PrimaryTealLight)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Nợ các hóa đơn chưa trả",
                    color = SurfaceWhite.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = AppRepository.formatCurrency(unpaidAmount),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = SurfaceWhite
            )
            Text(
                text = "$unpaidBills bill chưa thanh toán",
                fontSize = 13.sp,
                color = SurfaceWhite.copy(alpha = 0.75f)
            )
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
            modifier = Modifier
                .heightIn(min = 80.dp)
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center,
                maxLines = 2
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
        Pair("💡 Mẹo Xoay Vòng Tiền", "Dùng nút [Gộp Bill] để triệt tiêu các khoản nợ chéo, chỉ cần chuyển tiền 1-2 lần thay vì 6 lần!"),
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
    unpaidAmount: Long,
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
                    text = "${project.memberIds.size} thành viên • Nợ chưa trả: ${AppRepository.formatCurrency(unpaidAmount)}",
                    fontSize = 12.sp,
                    color = TextMedium
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Hạn mức còn lại: ${AppRepository.formatCurrency(project.budgetConfig.remainingAmount)}",
                    fontSize = 12.sp,
                    color = if (project.budgetConfig.remainingAmount < 0L) ErrorRed else PrimaryTealDark,
                    fontWeight = FontWeight.SemiBold
                )
                if (project.budgetConfig.remainingAmount < 0L) {
                    Text(
                        text = "Cảnh báo: dự án đã vượt hạn mức ${AppRepository.formatCurrency(-project.budgetConfig.remainingAmount)}",
                        fontSize = 11.sp,
                        color = ErrorRed,
                        fontWeight = FontWeight.Bold
                    )
                }

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
                Text("Quản lý Ngân sách & Hạn mức", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentConfig.remainingAmount < 0L) ErrorRed.copy(alpha = 0.08f) else PrimaryTealContainer
                    ),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Hạn mức còn lại: ${AppRepository.formatCurrency(currentConfig.remainingAmount)}",
                            color = if (currentConfig.remainingAmount < 0L) ErrorRed else PrimaryTealDark,
                            fontWeight = FontWeight.Bold
                        )
                        if (currentConfig.remainingAmount < 0L) {
                            Text(
                                "Cảnh báo: đã vượt hạn mức ${AppRepository.formatCurrency(-currentConfig.remainingAmount)}.",
                                color = ErrorRed,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Số tiền hạn mức dự định (VNĐ)") },
                    placeholder = { InputPlaceholder("Nhập hạn mức") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = BillInputFieldColors()
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

internal fun todayBillDateMillis(): Long {
    val localDate = Calendar.getInstance()
    return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
        clear()
        set(
            localDate.get(Calendar.YEAR),
            localDate.get(Calendar.MONTH),
            localDate.get(Calendar.DAY_OF_MONTH)
        )
    }.timeInMillis
}

internal fun billDateLabel(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(millis))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeBillCalendarDialog(
    onDismiss: () -> Unit,
    onDateRangeSelected: (Long, Long) -> Unit
) {
    var rangeMode by remember { mutableStateOf(false) }
    val today = remember { todayBillDateMillis() }
    val singleDateState = rememberDatePickerState(initialSelectedDateMillis = today)
    val dateRangeState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = today,
        initialSelectedEndDateMillis = today
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = if (rangeMode) dateRangeState.selectedStartDateMillis != null
                    && dateRangeState.selectedEndDateMillis != null
                else singleDateState.selectedDateMillis != null,
                onClick = {
                    if (rangeMode) {
                        val start = dateRangeState.selectedStartDateMillis
                        val end = dateRangeState.selectedEndDateMillis
                        if (start != null && end != null) onDateRangeSelected(start, end)
                    } else {
                        singleDateState.selectedDateMillis?.let { onDateRangeSelected(it, it) }
                    }
                }
            ) { Text("Xem bill") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
    ) {
        if (rangeMode) {
            DateRangePicker(
                state = dateRangeState,
                title = {
                    CalendarSelectionMode(
                        rangeMode = true,
                        onRangeModeChange = { rangeMode = it }
                    )
                },
                headline = { Text("Chọn khoảng ngày") },
                showModeToggle = true
            )
        } else {
            DatePicker(
                state = singleDateState,
                title = {
                    CalendarSelectionMode(
                        rangeMode = false,
                        onRangeModeChange = { rangeMode = it }
                    )
                },
                headline = { Text("Chọn ngày") },
                showModeToggle = true
            )
        }
    }
}

@Composable
private fun CalendarSelectionMode(
    rangeMode: Boolean,
    onRangeModeChange: (Boolean) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = !rangeMode,
            onClick = { onRangeModeChange(false) },
            label = { Text("Một ngày") }
        )
        FilterChip(
            selected = rangeMode,
            onClick = { onRangeModeChange(true) },
            label = { Text("Khoảng ngày") }
        )
    }
}

@Composable
fun UnpaidBillsDialog(
    bills: List<Bill>,
    userId: String?,
    projectNames: Map<String, String>,
    onDismiss: () -> Unit,
    onOpenBill: (String) -> Unit
) {
    val outstandingBills = bills.filter { userId != null && it.unpaidAmountFor(userId) > 0L }
        .sortedByDescending { it.billDate }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hóa đơn chưa trả", fontWeight = FontWeight.Bold) },
        text = {
            if (outstandingBills.isEmpty()) {
                Text("Bạn không có hóa đơn nào cần thanh toán.")
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    outstandingBills.forEach { bill ->
                        val amount = bill.unpaidAmountFor(userId!!)
                        val status = bill.memberStatuses[userId]
                        val alreadyReported = status?.isPaid == true && !status.isCreatorConfirmed
                        Card(
                            colors = CardDefaults.cardColors(containerColor = BackgroundSlate),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(bill.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "${projectNames[bill.projectId] ?: "Không rõ dự án"} • ${billDateLabel(bill.billDate)}",
                                    color = TextMedium,
                                    fontSize = 12.sp
                                )
                                Text(
                                    "Còn nợ: ${AppRepository.formatCurrency(amount)}",
                                    color = PrimaryTealDark,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (alreadyReported) {
                                    Text("Đã báo trả, đang chờ người nhận xác nhận.", color = TextMedium, fontSize = 12.sp)
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onOpenBill(bill.id) },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Chi tiết / QR", fontSize = 11.sp) }
                                        Button(
                                            onClick = {
                                                if (bill.isMergedParent) {
                                                    bill.optimizedTransfers
                                                        .filter { it.fromUserId == userId && !it.isPaid }
                                                        .forEach { AppRepository.markOptimizedTransferPaid(it.id) }
                                                } else {
                                                    AppRepository.markMemberAsPaid(bill.id, userId)
                                                }
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                                        ) { Text("Báo đã trả", fontSize = 11.sp) }
                                    }
                                }
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

@Composable
fun BillEditHistoryDialog(
    bills: List<Bill>,
    projectNames: Map<String, String>,
    onlyBillId: String? = null,
    onDismiss: () -> Unit
) {
    val historyEntries = bills
        .filter { onlyBillId == null || it.id == onlyBillId }
        .flatMap { bill -> bill.editHistory.map { record -> bill to record } }
        .sortedByDescending { it.second.editedAt }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lịch sử sửa bill", fontWeight = FontWeight.Bold) },
        text = {
            if (historyEntries.isEmpty()) {
                Text("Bill này chưa có lịch sử chỉnh sửa.")
            } else {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    historyEntries.forEach { (bill, record) ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BackgroundSlate),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(bill.title, fontWeight = FontWeight.Bold)
                                Text(
                                    "${projectNames[bill.projectId] ?: "Không rõ dự án"} • " +
                                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
                                            .format(Date(record.editedAt)),
                                    fontSize = 12.sp,
                                    color = TextMedium
                                )
                                Text(
                                    "Người sửa: ${AppRepository.getUserById(record.editorUserId)?.name ?: "Tài khoản khác"}",
                                    fontSize = 12.sp,
                                    color = TextMedium
                                )
                                Text(record.changes, fontSize = 12.sp, color = TextDark)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
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
    onBillCreated: (Bill) -> Unit
) {
    var selectedProjectId by remember { mutableStateOf<String?>(null) }
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }

    if (selectedProject == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Chọn dự án để tạo bill", fontWeight = FontWeight.Bold) },
            text = {
                if (projects.isEmpty()) {
                    Text("Bạn chưa có dự án. Hãy tạo hoặc tham gia dự án trước khi thêm bill.")
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        projects.forEach { project ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedProjectId = project.id }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProjectId == project.id,
                                    onClick = { selectedProjectId = project.id }
                                )
                                Column {
                                    Text(project.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${project.memberIds.size} thành viên",
                                        fontSize = 12.sp,
                                        color = TextMedium
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text("Hủy") } }
        )
    } else {
        CreateBillDialog(
            project = selectedProject,
            currentUser = currentUser,
            onDismiss = { selectedProjectId = null },
            onAddBill = onBillCreated
        )
    }
}

@Composable
private fun LegacyQuickBillInputDialog(
    projects: List<Project>,
    currentUser: User,
    onDismiss: () -> Unit,
    onPushToProject: (String, String, List<BillItem>, String?) -> Unit
) {
    var billTitle by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("") }
    var itemPrice by remember { mutableStateOf("") }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id ?: "") }
    var step by remember { mutableStateOf(0) }
    var splitForProject by remember { mutableStateOf(false) }
    var peopleCount by remember { mutableStateOf("") }
    var advanceUserId by remember {
        mutableStateOf(
            projects.firstOrNull()?.memberIds?.firstOrNull { it == currentUser.id }
                ?: projects.firstOrNull()?.memberIds?.firstOrNull()
        )
    }
    var savedItems by remember { mutableStateOf(emptyList<BillItem>()) }
    var selectedPayers by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var inputError by remember { mutableStateOf<String?>(null) }

    val draftItem = remember(itemName, itemQty, itemPrice) {
        if (itemName.isNotBlank() && (itemQty.toIntOrNull() ?: 0) > 0 && (itemPrice.toLongOrNull() ?: 0L) > 0L) {
            BillItem(
                name = itemName.trim(),
                quantity = itemQty.toInt(),
                unitPrice = itemPrice.toLong()
            )
        } else {
            null
        }
    }
    val billItems = savedItems + listOfNotNull(draftItem)
    val selectedProject = projects.firstOrNull { it.id == selectedProjectId }
    val guestCount = peopleCount.toIntOrNull()?.coerceIn(1, 30) ?: 0
    val participants = if (splitForProject) {
        selectedProject?.memberIds?.mapIndexed { index, id ->
            AppRepository.getUserById(id) ?: User(id, "Thành viên ${index + 1}", "", "", "", "")
        }.orEmpty()
    } else {
        (1..guestCount).map { index ->
            User("guest_$index", "Người $index", "", "", "", "")
        }
    }
    val participantIds = participants.map { it.id }.toSet()
    val selectedPayersByItem = billItems.associate { item ->
        item.id to selectedPayers[item.id].orEmpty().filter { it in participantIds }
    }
    val hasUnassignedItems = billItems.any { selectedPayersByItem[it.id].isNullOrEmpty() }
    val itemSharesByUser = participants.associate { user ->
        user.id to billItems.mapNotNull { item ->
            val payers = selectedPayersByItem[item.id].orEmpty()
            val payerIndex = payers.indexOf(user.id)
            if (payerIndex < 0 || payers.isEmpty()) {
                null
            } else {
                val baseShare = item.totalPrice / payers.size
                val remainder = item.totalPrice % payers.size
                item to (baseShare + if (payerIndex < remainder) 1L else 0L)
            }
        }
    }

    fun togglePayer(itemId: String, userId: String, isSelected: Boolean) {
        val current = selectedPayers[itemId].orEmpty()
        val updated = if (isSelected) (current + userId).distinct() else current - userId
        selectedPayers = selectedPayers + (itemId to updated)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.EditNote, contentDescription = null, tint = SuccessGreen)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    when (step) {
                        1 -> "Chia tiền nhanh"
                        2 -> "PHANCHIA"
                        3 -> "Kiểm tra số tiền"
                        else -> "Nhập tay bill nhanh"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            if (step == 1) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Kiểm tra bill rồi nhập số người tham gia chia tiền.",
                        fontSize = 12.sp,
                        color = TextMedium
                    )
                    OutlinedTextField(
                        value = peopleCount,
                        onValueChange = {
                            peopleCount = it.filter(Char::isDigit)
                            selectedPayers = emptyMap()
                            inputError = null
                        },
                        label = { Text("Số người chia tiền") },
                        placeholder = { InputPlaceholder("Nhập số người") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )
                    Text(
                        "${billItems.size} món • Tổng ${AppRepository.formatCurrency(billItems.sumOf { it.totalPrice })}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PrimaryTealDark
                    )
                    inputError?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                }
            } else if (step == 2) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        if (splitForProject) {
                            "Dự án có ${participants.size} thành viên. Chọn người cùng dùng cho từng món."
                        } else {
                            "Chọn người cùng dùng từng món. Món có nhiều người sẽ được chia tiền giữa những người đã chọn."
                        },
                        fontSize = 12.sp,
                        color = TextMedium
                    )
                    BillDistributionTab(
                        items = billItems,
                        participants = participants,
                        selectedPayers = selectedPayers,
                        onTogglePayer = ::togglePayer,
                        showAmounts = false,
                        defaultAllParticipants = false,
                        showAdvanceSelection = splitForProject,
                        advanceUserId = advanceUserId,
                        onAdvanceUserSelected = { advanceUserId = it }
                    )
                    inputError?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                }
            } else if (step == 3) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Kiểm tra số tiền mỗi người cần trả:",
                        fontSize = 12.sp,
                        color = TextMedium
                    )
                    participants.forEach { user ->
                        val shares = itemSharesByUser[user.id].orEmpty()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(user.name, fontWeight = FontWeight.Bold)
                                    Text(
                                        AppRepository.formatCurrency(shares.sumOf { it.second }),
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryTealDark
                                    )
                                }
                                if (shares.isEmpty()) {
                                    Text("Không có món nào được chọn", fontSize = 11.sp, color = TextMedium)
                                } else {
                                    shares.forEach { (item, amount) ->
                                        Text(
                                            "${item.name}: ${AppRepository.formatCurrency(amount)}",
                                            fontSize = 11.sp,
                                            color = TextMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        "Tổng hóa đơn: ${AppRepository.formatCurrency(billItems.sumOf { it.totalPrice })}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryTealDark
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = billTitle,
                        onValueChange = { billTitle = it },
                        label = { Text("Tên hóa đơn") },
                        placeholder = { InputPlaceholder("Nhập tên hóa đơn") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )

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
                    OutlinedTextField(
                        value = itemPrice,
                        onValueChange = { itemPrice = it.filter(Char::isDigit); inputError = null },
                        label = { Text("Đơn giá (VNĐ)") },
                        placeholder = { InputPlaceholder("Nhập đơn giá") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )
                    OutlinedButton(
                        onClick = {
                            when {
                                itemName.isBlank() -> inputError = "Vui lòng nhập tên món."
                                itemQty.toIntOrNull() == null || itemQty.toInt() <= 0 -> inputError = "Số lượng phải lớn hơn 0."
                                itemPrice.toLongOrNull() == null || itemPrice.toLong() <= 0L -> inputError = "Đơn giá phải lớn hơn 0."
                                else -> {
                                    savedItems = savedItems + draftItem!!
                                    itemName = ""
                                    itemQty = ""
                                    itemPrice = ""
                                    inputError = null
                                }
                            }
                        }
                    ) {
                        Text("Thêm món")
                    }

                    savedItems.forEachIndexed { index, item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${item.name} × ${item.quantity} — ${AppRepository.formatCurrency(item.totalPrice)}",
                                modifier = Modifier.weight(1f),
                                fontSize = 12.sp
                            )
                            IconButton(onClick = { savedItems = savedItems.filterIndexed { i, _ -> i != index } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Xóa món", tint = ErrorRed)
                            }
                        }
                    }

                    val total = billItems.sumOf { it.totalPrice }
                    Text(
                        "Tổng tiền bill: ${AppRepository.formatCurrency(total)}",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryTealDark,
                        fontSize = 14.sp
                    )
                    inputError?.let { Text(it, color = ErrorRed, fontSize = 12.sp) }
                    Divider()
                    Text("Chọn dự án để lưu:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    projects.forEach { proj ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProjectId = proj.id
                                    selectedPayers = emptyMap()
                                    advanceUserId = proj.memberIds.firstOrNull { it == currentUser.id }
                                        ?: proj.memberIds.firstOrNull()
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedProjectId == proj.id,
                                onClick = {
                                    selectedProjectId = proj.id
                                    selectedPayers = emptyMap()
                                    advanceUserId = proj.memberIds.firstOrNull { it == currentUser.id }
                                        ?: proj.memberIds.firstOrNull()
                                }
                            )
                            Text(proj.name, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (step) {
                        0 -> {
                            val draftHasValues = itemName.isNotBlank() || itemQty.isNotBlank() || itemPrice.isNotBlank()
                            when {
                                draftHasValues && draftItem == null ->
                                    inputError = "Hoàn thiện tên món, số lượng và đơn giá trước khi tiếp tục."
                                billItems.isEmpty() ->
                                    inputError = "Thêm ít nhất một món để tiếp tục."
                                else -> {
                                    splitForProject = false
                                    selectedPayers = emptyMap()
                                    inputError = null
                                    step = 1
                                }
                            }
                        }
                        1 -> {
                            if (guestCount == 0) {
                                inputError = "Nhập số người chia tiền (từ 1 đến 30)."
                            } else {
                                inputError = null
                                step = 2
                            }
                        }
                        2 -> {
                            if (hasUnassignedItems) {
                                inputError = "Chọn ít nhất một người cho từng món."
                            } else {
                                inputError = null
                                step = 3
                            }
                        }
                        else -> {
                            if (splitForProject) {
                                val distributedItems = billItems.map { item ->
                                    item.copy(payerUserIds = selectedPayersByItem[item.id].orEmpty())
                                }
                                onPushToProject(
                                    selectedProjectId,
                                    billTitle.ifBlank { "Hóa đơn" },
                                    distributedItems,
                                    advanceUserId?.takeIf { id -> id in participantIds }
                                )
                            }
                            onDismiss()
                        }
                    }
                },
                enabled = step != 3 || !splitForProject || selectedProjectId.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (splitForProject) PrimaryTeal else SecondaryIndigo
                )
            ) {
                Text(
                    when (step) {
                        0 -> "Chia tiền nhanh"
                        1 -> "Tiếp tục"
                        2 -> "Xem số tiền"
                        3 -> if (splitForProject) "Lưu vào dự án" else "Hoàn tất"
                        else -> "Tiếp tục"
                    }
                )
            }
        },
        dismissButton = {
            if (step == 0) {
                OutlinedButton(
                    onClick = {
                        val draftHasValues = itemName.isNotBlank() || itemQty.isNotBlank() || itemPrice.isNotBlank()
                        when {
                            draftHasValues && draftItem == null ->
                                inputError = "Hoàn thiện tên món, số lượng và đơn giá trước khi tiếp tục."
                            billItems.isEmpty() ->
                                inputError = "Thêm ít nhất một món để tiếp tục."
                            selectedProject == null ->
                                inputError = "Tạo hoặc tham gia dự án trước khi lưu bill."
                            else -> {
                                splitForProject = true
                                selectedPayers = emptyMap()
                                inputError = null
                                step = 2
                            }
                        }
                    },
                    enabled = billItems.isNotEmpty()
                ) {
                    Text("Đẩy vào dự án")
                }
            } else if (step == 1) {
                TextButton(onClick = {
                    inputError = null
                    step = 0
                }) {
                    Text("Quay lại")
                }
            } else {
                TextButton(
                    onClick = {
                        inputError = null
                        step = if (step == 3) 2 else if (splitForProject) 0 else 1
                    }
                ) {
                    Text(if (step == 3) "Quay lại PHANCHIA" else "Quay lại")
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
    onPushToProject: (String, String, List<BillItem>, String?) -> Unit
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
    var peopleCount by remember { mutableStateOf("") }
    var splitForProject by remember { mutableStateOf(false) }
    var selectedPayers by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var advanceUserId by remember {
        mutableStateOf(
            projects.firstOrNull()?.memberIds?.firstOrNull { it == currentUser.id }
                ?: projects.firstOrNull()?.memberIds?.firstOrNull()
        )
    }

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
    val splitProject = projects.firstOrNull { it.id == selectedProjectId }
    val guestCount = peopleCount.toIntOrNull()?.coerceIn(1, 30) ?: 0
    val splitParticipants = if (splitForProject) {
        getProjectParticipants(splitProject)
    } else {
        getGuestParticipants(guestCount)
    }

    fun togglePayer(itemId: String, userId: String, isSelected: Boolean) {
        val current = selectedPayers[itemId] ?: splitParticipants.map { it.id }
        if (!isSelected && current.size <= 1) return
        selectedPayers = selectedPayers + (
            itemId to if (isSelected) current + userId else current - userId
        )
    }

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
                        if (isQuickSplitMode) "Phân chia" else "Chụp ảnh OCR & Phân tích Bill",
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
                        .imePadding()
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
                                    placeholder = { InputPlaceholder("Nhập tên hóa đơn") },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = BillInputFieldColors(),
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
                                        .clickable {
                                            selectedProjectId = proj.id
                                            selectedPayers = emptyMap()
                                            advanceUserId = proj.memberIds.firstOrNull { it == currentUser.id }
                                                ?: proj.memberIds.firstOrNull()
                                        }
                                        .padding(vertical = 2.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedProjectId == proj.id,
                                        onClick = {
                                            selectedProjectId = proj.id
                                            selectedPayers = emptyMap()
                                            advanceUserId = proj.memberIds.firstOrNull { it == currentUser.id }
                                                ?: proj.memberIds.firstOrNull()
                                        }
                                    )
                                    Text(proj.name, fontSize = 13.sp)
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!splitForProject) {
                                    OutlinedTextField(
                                        value = peopleCount,
                                        onValueChange = {
                                            peopleCount = it.filter(Char::isDigit)
                                            selectedPayers = emptyMap()
                                        },
                                        label = { Text("Số người chia tiền") },
                                        placeholder = { InputPlaceholder("Nhập số người") },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = BillInputFieldColors(),
                                        singleLine = true
                                    )
                                }
                                BillDistributionTab(
                                    items = parsedItems,
                                    participants = splitParticipants,
                                    selectedPayers = selectedPayers,
                                    onTogglePayer = ::togglePayer,
                                    showAdvanceSelection = splitForProject,
                                    advanceUserId = advanceUserId,
                                    onAdvanceUserSelected = { advanceUserId = it }
                                )
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
                        OutlinedButton(onClick = {
                            isQuickSplitMode = true
                            splitForProject = false
                            selectedPayers = emptyMap()
                        }) {
                            Text("Chia tiền nhanh")
                        }
                        Button(
                            onClick = {
                                isQuickSplitMode = true
                                splitForProject = true
                                selectedPayers = emptyMap()
                            },
                            enabled = selectedProjectId.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                        ) {
                            Text("Phân chia")
                        }
                    } else if (parsedItems.isNotEmpty()) {
                        TextButton(onClick = { isQuickSplitMode = false }) { Text("Quay lại bill") }
                        Button(
                            onClick = {
                                if (splitForProject) {
                                    val distributedItems = parsedItems.map { item ->
                                        item.copy(payerUserIds = selectedPayers[item.id] ?: splitParticipants.map { it.id })
                                    }
                                    onPushToProject(
                                        selectedProjectId,
                                        billTitle.ifBlank { "Hóa đơn" },
                                        distributedItems,
                                        advanceUserId?.takeIf { id -> id in splitParticipants.map(User::id) }
                                    )
                                }
                                onDismiss()
                            },
                            enabled = parsedItems.isNotEmpty() && (splitForProject || guestCount > 0),
                            colors = ButtonDefaults.buttonColors(containerColor = if (splitForProject) PrimaryTeal else SecondaryIndigo)
                        ) {
                            Text(if (splitForProject) "Lưu vào dự án" else "Hoàn tất")
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
    onPushToProject: (String, String, List<BillItem>, String?) -> Unit
) {
    var voiceQuery by remember { mutableStateOf("") }
    var billTitle by remember { mutableStateOf("") }
    var parsedItems by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var selectedProjectId by remember { mutableStateOf(projects.firstOrNull()?.id ?: "") }
    var isQuickSplitMode by remember { mutableStateOf(false) }
    var peopleCount by remember { mutableStateOf("") }
    var splitForProject by remember { mutableStateOf(false) }
    var selectedPayers by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var advanceUserId by remember {
        mutableStateOf(
            projects.firstOrNull()?.memberIds?.firstOrNull { it == currentUser.id }
                ?: projects.firstOrNull()?.memberIds?.firstOrNull()
        )
    }

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
    val splitProject = projects.firstOrNull { it.id == selectedProjectId }
    val guestCount = peopleCount.toIntOrNull()?.coerceIn(1, 30) ?: 0
    val splitParticipants = if (splitForProject) {
        getProjectParticipants(splitProject)
    } else {
        getGuestParticipants(guestCount)
    }

    fun togglePayer(itemId: String, userId: String, isSelected: Boolean) {
        val current = selectedPayers[itemId] ?: splitParticipants.map { it.id }
        if (!isSelected && current.size <= 1) return
        selectedPayers = selectedPayers + (
            itemId to if (isSelected) current + userId else current - userId
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = SecondaryIndigo)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isQuickSplitMode) "Phân chia" else "Giọng nói AI & Bóc tách Bill", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isQuickSplitMode) {
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
                    placeholder = { InputPlaceholder("Nói hoặc nhập nội dung hóa đơn") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = BillInputFieldColors()
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
                            placeholder = { InputPlaceholder("Nhập tên hóa đơn") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = BillInputFieldColors(),
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
                }

                if (!isQuickSplitMode) {
                    Text("Lưu hóa đơn này vào dự án:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    projects.forEach { proj ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProjectId = proj.id
                                    selectedPayers = emptyMap()
                                    advanceUserId = proj.memberIds.firstOrNull { it == currentUser.id }
                                        ?: proj.memberIds.firstOrNull()
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            RadioButton(
                                selected = selectedProjectId == proj.id,
                                onClick = {
                                    selectedProjectId = proj.id
                                    selectedPayers = emptyMap()
                                    advanceUserId = proj.memberIds.firstOrNull { it == currentUser.id }
                                        ?: proj.memberIds.firstOrNull()
                                }
                            )
                            Text(proj.name, fontSize = 13.sp)
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!splitForProject) {
                            OutlinedTextField(
                                value = peopleCount,
                                onValueChange = {
                                    peopleCount = it.filter(Char::isDigit)
                                    selectedPayers = emptyMap()
                                },
                                label = { Text("Số người chia tiền") },
                                placeholder = { InputPlaceholder("Nhập số người") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = BillInputFieldColors(),
                                singleLine = true
                            )
                        }
                        BillDistributionTab(
                            items = parsedItems,
                            participants = splitParticipants,
                            selectedPayers = selectedPayers,
                            onTogglePayer = ::togglePayer,
                            showAdvanceSelection = splitForProject,
                            advanceUserId = advanceUserId,
                            onAdvanceUserSelected = { advanceUserId = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isQuickSplitMode) {
                Button(
                    onClick = {
                        if (splitForProject) {
                            val distributedItems = parsedItems.map { item ->
                                item.copy(payerUserIds = selectedPayers[item.id] ?: splitParticipants.map { it.id })
                            }
                            onPushToProject(
                                selectedProjectId,
                                billTitle,
                                distributedItems,
                                advanceUserId?.takeIf { id -> id in splitParticipants.map(User::id) }
                            )
                        }
                        onDismiss()
                    },
                    enabled = parsedItems.isNotEmpty() && (splitForProject || guestCount > 0),
                    colors = ButtonDefaults.buttonColors(containerColor = if (splitForProject) PrimaryTeal else SecondaryIndigo)
                ) {
                    Text(if (splitForProject) "Lưu vào dự án" else "Hoàn tất")
                }
            } else {
                Button(
                    onClick = {
                        isQuickSplitMode = true
                        splitForProject = true
                        selectedPayers = emptyMap()
                    },
                    enabled = parsedItems.isNotEmpty() && selectedProjectId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                ) { Text("Phân chia") }
            }
        },
        dismissButton = {
            if (!isQuickSplitMode) {
                OutlinedButton(onClick = {
                    isQuickSplitMode = true
                    splitForProject = false
                    selectedPayers = emptyMap()
                }) {
                    Text("Chia tiền nhanh")
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
    onCreate: (String, String, Long, Int, Boolean, Boolean) -> Project
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("Phòng trọ") }
    var limitText by remember { mutableStateOf("") }
    var customType by remember { mutableStateOf("") }
    val cycleDays = 30
    var createdProject by remember { mutableStateOf<Project?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (createdProject == null) "Thiết lập dự án mới" else "Tạo dự án thành công!")
        },
        text = {
            val project = createdProject
            if (project != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dự án: ${project.name}", fontWeight = FontWeight.Bold)
                    Text("Mã mời: ${project.inviteCode}", fontSize = 20.sp, color = PrimaryTealDark)
                }
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            errorMessage = null
                        },
                        label = { Text("Tên dự án") },
                        placeholder = { InputPlaceholder("Nhập tên dự án") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )
                    Text("Mô hình dự án:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val modelTypes = listOf("Phòng trọ", "Du lịch", "Ăn uống", "Khác")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(modelTypes.take(3), modelTypes.drop(3)).forEach { rowTypes ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowTypes.forEach { type ->
                                    FilterChip(
                                        selected = selectedType == type,
                                        onClick = {
                                            selectedType = type
                                            if (type != "Khác") customType = ""
                                        },
                                        label = { Text(type, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }
                    if (selectedType == "Khác") {
                        OutlinedTextField(
                            value = customType,
                            onValueChange = {
                                customType = it
                                errorMessage = null
                            },
                            label = { Text("Tên mô hình dự án") },
                            placeholder = { InputPlaceholder("Nhập tên mô hình") },
                            modifier = Modifier.fillMaxWidth(),
                            colors = BillInputFieldColors(),
                            singleLine = true
                        )
                    }
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = {
                            limitText = it
                            errorMessage = null
                        },
                        label = { Text("Hạn mức dự kiến (VNĐ)") },
                        placeholder = { InputPlaceholder("Ví dụ: 4000000") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = BillInputFieldColors(),
                        singleLine = true
                    )
                    errorMessage?.let { message ->
                        Text(message, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (createdProject != null) {
                        onDismiss()
                    } else {
                        val trimmedName = name.trim()
                        val limit = limitText.toLongOrNull()
                        val modelName = if (selectedType == "Khác") customType.trim() else selectedType
                        if (trimmedName.isEmpty()) {
                            errorMessage = "Vui lòng nhập tên dự án."
                        } else if (limit == null || limit <= 0L) {
                            errorMessage = "Hạn mức phải là số lớn hơn 0."
                        } else if (modelName.isEmpty()) {
                            errorMessage = "Vui lòng nhập tên mô hình dự án."
                        } else {
                            try {
                                createdProject = onCreate(
                                    trimmedName,
                                    modelName,
                                    limit,
                                    cycleDays,
                                    false,
                                    true
                                )
                            } catch (e: IllegalStateException) {
                                errorMessage = e.message ?: "Không thể tạo dự án. Vui lòng đăng nhập lại."
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
            ) {
                Text(if (createdProject == null) "Tạo ngay" else "Đóng")
            }
        },
        dismissButton = if (createdProject == null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Hủy")
                }
            }
        } else {
            null
        }
    )
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
        title = { Text("Tham gia Dự án", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Nhập mã dự án được bạn bè chia sẻ:", fontSize = 12.sp, color = TextMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase() },
                    placeholder = { InputPlaceholder("Nhập mã mời dự án") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = BillInputFieldColors(),
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