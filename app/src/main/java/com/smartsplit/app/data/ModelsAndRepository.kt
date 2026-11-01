package com.smartsplit.app.data

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.smartsplit.app.service.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.security.SecureRandom
import kotlin.math.abs
import kotlin.math.min

// ==========================================
// [MỤC 1.1] THÔNG TIN TÀI KHOẢN NGƯỜI DÙNG & TÀI KHOẢN NGÂN HÀNG
// ==========================================
data class User(
    val id: String,
    val name: String,
    val avatar: String,
    val bankName: String,
    val bankBin: String, // Mã BIN ngân hàng theo chuẩn VietQR (VD: 970415 = VietinBank, 970422 = MBBank)
    val accountNumber: String,
    val qrImageUri: String = ""
)

// ==========================================
// [MỤC 1.2 & 1.9.1] CẤU HÌNH HẠN MỨC & CHU KỲ CẤP LẠI NGÂN SÁCH
// ==========================================
data class BudgetConfig(
    val totalLimit: Long = 4_000_000L,
    val remainingAmount: Long = 2_450_000L,
    val isAutoRenew: Boolean = false, // Mặc định: KHÔNG cấp lại tự động (Người dùng tự cấp lại thủ công)
    val cycleDays: Int = 0,          // 0 = Cấp lại thủ công (không tự động)
    val daysLeft: Int = 0,
    val lastRenewalTimestamp: Long = System.currentTimeMillis() // Thời gian cấp lại cuối cùng
)

// ==========================================
// [MỤC 1.7 & 1.9] THÔNG TIN MÔ HÌNH DỰ ÁN (PROJECT)
// ==========================================
data class Project(
    val id: String,
    val name: String,
    val inviteCode: String,
    val iconName: String = "home", // home, flight, restaurant, work
    val modelType: String = "Phòng trọ",
    val budgetConfig: BudgetConfig = BudgetConfig(),
    val memberIds: List<String> = emptyList(),
    val isElectricityWaterEnabled: Boolean = true,
    val requireBillApproval: Boolean = false // [MỤC 1.7.2] Yêu cầu duyệt hóa đơn
)

// ==========================================
// [MỤC 1.9.3] CHẾ ĐỘ NHẬP LIỆU: NB1 (CÁ NHÂN) HOẶC NB2 (ĐỒNG THỜI CÙNG SỬA)
// ==========================================
enum class InputMode {
    NB1, // Cá nhân tạo, chỉ người tạo sửa, mặc định chia đều
    NB2  // Mọi người cùng vào sửa và tick chọn món thời gian thực
}

// ==========================================
// [MỤC 1.9.3] 4 QUYỀN PHÂN PHỐI CHO CHẾ ĐỘ NB2
// ==========================================
data class Nb2Permission(
    val canEditName: Boolean = true,      // 1. Cho phép sửa Tên món
    val canEditQty: Boolean = true,       // 2. Cho phép sửa Số lượng
    val canEditPrice: Boolean = true,     // 3. Cho phép sửa Giá tiền
    val canSelectItem: Boolean = true     // 4. Cho phép tự chọn món nào mình trả tiền
)

fun calendarDateMillis(timestamp: Long): Long {
    val date = Calendar.getInstance().apply { timeInMillis = timestamp }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US).apply {
        clear()
        set(
            date.get(Calendar.YEAR),
            date.get(Calendar.MONTH),
            date.get(Calendar.DAY_OF_MONTH)
        )
    }.timeInMillis
}

// ==========================================
// [MỤC 1.9.4.2] THÔNG TIN TỪNG MÓN ĐỒ TRONG HÓA ĐƠN
// ==========================================
data class BillItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Int = 1,
    val unitPrice: Long,
    val payerUserIds: List<String> = emptyList() // Danh sách ID các user cùng chia tiền món này
) {
    val totalPrice: Long get() = quantity * unitPrice
}

// ==========================================
// [MỤC 1.9.4.2 ➔ 1.9.4.5] TRẠNG THÁI CỦA TỪNG THÀNH VIÊN TRONG BILL
// ==========================================
data class MemberBillStatus(
    val userId: String,
    val itemCount: Int = 0,
    val amountOwed: Long = 0L,
    val isApproved: Boolean = false,        // Người này đã bấm Duyệt phần của tôi (1.9.4.2)
    val isPaid: Boolean = false,            // Người này đã thanh toán (1.9.4.4)
    val proofImageUri: String? = null,      // Ảnh chụp biên lai chuyển tiền (1.9.4.4)
    val isCreatorConfirmed: Boolean = false // Chủ bill đã bấm Confirm đã nhận tiền (1.9.4.5)
)

data class BillDebtObject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Khoản nợ chưa có người ứng",
    val ownerUserId: String
)

// ==========================================
// [MỤC 1.9.5] GIAO DỊCH CHUYỂN TIỀN ĐÃ RÚT GỌN (THUẬT TOÁN XOAY VÒNG TIỀN)
// ==========================================
data class OptimizedTransfer(
    val id: String = UUID.randomUUID().toString(),
    val fromUserId: String,                 // Người cần chuyển tiền
    val toUserId: String,                   // Người nhận tiền
    val amount: Long,                       // Số tiền đã tối ưu
    val transferCode: String,               // Mã chuyển khoản quy ước (SM_P302_B01_TUAN)
    val isPaid: Boolean = false,            // Đã trả tiền chưa
    val isConfirmed: Boolean = false        // Người nhận đã xác nhận chưa
)

data class BillEditHistory(
    val editorUserId: String,
    val editedAt: Long = System.currentTimeMillis(),
    val changes: String
)

// ==========================================
// [MỤC 1.9.4 & 1.9.5] THÔNG TIN TOÀN DIỆN CỦA MỘT HÓA ĐƠN (BILL THƯỜNG HOẶC BILL GỘP)
// ==========================================
data class Bill(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val creatorId: String,                  // Người ứng tiền trước
    val advanceUserId: String? = creatorId,
    val debtObject: BillDebtObject? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val billDate: Long = calendarDateMillis(createdAt),
    val mode: InputMode = InputMode.NB1,
    val nb2Permission: Nb2Permission = Nb2Permission(),
    val receiptImageUri: String? = null,    // Minh chứng khi tạo bill (1.9.4.1)
    val recurringDay: Int? = null,          // Ngày xét lịch định kỳ (1.9.3: ngày 1..31)
    val items: List<BillItem> = emptyList(),
    val memberStatuses: Map<String, MemberBillStatus> = emptyMap(),
    
    // Thuộc tính phục vụ Mục 1.9.5: Gộp Bill & Làm tối màu
    val isMergedParent: Boolean = false,    // True nếu đây là "Bill Đã Gộp Mới"
    val childBillIds: List<String> = emptyList(), // Danh sách ID các bill con cũ
    val mergedIntoBillId: String? = null,   // Nếu bill cũ này đã bị gộp, lưu ID của bill gộp cha
    val optimizedTransfers: List<OptimizedTransfer> = emptyList(), // Danh sách lệnh chuyển tiền xoay vòng
    val editHistory: List<BillEditHistory> = emptyList()
) {
    // Tổng số tiền cả bill
    val totalAmount: Long
        get() = if (items.isNotEmpty()) items.sumOf { it.totalPrice }
                else memberStatuses.values.sumOf { it.amountOwed }

    // Kiểm tra xem bill này đã được duyệt hết chưa (Mặt ngoài 1.9.4)
    val isAllApproved: Boolean
        get() = memberStatuses.isNotEmpty() && memberStatuses.values.all { it.isApproved }

    // Kiểm tra xem bill này đã được thanh toán hết chưa (Mặt ngoài 1.9.4)
    val isAllPaid: Boolean
        get() = if (isMergedParent) {
            optimizedTransfers.isNotEmpty() && optimizedTransfers.all { it.isPaid && it.isConfirmed }
        } else {
            memberStatuses.isNotEmpty() && memberStatuses.values.all { it.isPaid && it.isCreatorConfirmed }
        }
}

fun Bill.unpaidAmountFor(userId: String): Long {
        if (mergedIntoBillId != null) return 0L
        if (isMergedParent) {
            return optimizedTransfers
                .filter { it.fromUserId == userId && !it.isPaid }
                .sumOf { it.amount }
        }
        val status = memberStatuses[userId] ?: return 0L
        return if (status.isCreatorConfirmed) 0L else status.amountOwed
}

// Thông báo hệ thống (Notification Push)
data class AppNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ==========================================
// KHO DỮ LIỆU SINGLETON REPOSITORY (QUẢN LÝ TOÀN BỘ TRẠNG THÁI APP)
// ==========================================
object AppRepository {
    private const val NOTIFICATION_ID_BASE = 1000
    private var appContext: Context? = null
    private var appDataDatabase: AppDataDatabaseHelper? = null
    private var cloudDataRepository: CloudDataRepository? = null

    // Khởi tạo context để push local notification
    fun init(context: Context) {
        appContext = context
        appDataDatabase = AppDataDatabaseHelper(context)
        cloudDataRepository = if (FirebaseApp.getApps(context).isNotEmpty()) {
            CloudDataRepository(context.applicationContext) { error ->
                _syncError.value = error
            }
        } else {
            null
        }
        NotificationHelper.createNotificationChannel(context)
    }

    // Load each account's locally persisted projects and bills after login.
    fun setCurrentUser(userId: String?) {
        _currentUserId.value = userId
        if (userId == null) {
            cloudDataRepository?.stopListening()
            _projects.value = emptyList()
            _bills.value = emptyList()
            _users.value = emptyList()
            _notifications.value = emptyList()
            return
        }
        val database = appDataDatabase ?: appContext?.let(::AppDataDatabaseHelper) ?: return
        appDataDatabase = database
        _projects.value = database.projectsForUser(userId)
        _bills.value = database.billsForUser(userId)
        _syncError.value = null
        val cloud = cloudDataRepository ?: run {
            _syncError.value = "Firebase chưa được cấu hình. Thêm google-services.json để bật đồng bộ cloud."
            return
        }
        cloud.listen(
            userId = userId,
            onProjects = { projects ->
                _projects.value = projects
                database.saveSnapshot(projects, _bills.value)
            },
            onBills = { bills ->
                _bills.value = bills
                database.saveSnapshot(_projects.value, bills)
            },
            onUsers = { users ->
                users.forEach(cloud::cacheUser)
                _users.value = (cloudUsersFallback() + users).distinctBy(User::id)
            },
            onNotifications = { notifications ->
                _notifications.value = notifications
            }
        )
    }

    // Danh sách 3 tài khoản mẫu để test phân quyền theo Mục 1.1
    val sampleUsers = listOf(
        User("u1", "Tuấn", "avatar_tuan", "VietinBank", "970415", "101869999999"),
        User("u2", "Hùng", "avatar_hung", "MBBank", "970422", "0987654321"),
        User("u3", "Nam", "avatar_nam", "Techcombank", "970407", "1903456789")
    )

    // Tài khoản hiện đang đăng nhập (Mục 1.1)
    private val _currentUserId = MutableStateFlow<String?>(null) // Sẽ được set sau khi đăng nhập
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    // Danh sách dự án (Mục 1.9)
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    // Danh sách hóa đơn (Mục 1.9.4)
    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills: StateFlow<List<Bill>> = _bills.asStateFlow()

    // Danh sách thông báo đẩy (In-App Push Notifications)
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()
    private val _users = MutableStateFlow<List<User>>(emptyList())
    val users: StateFlow<List<User>> = _users.asStateFlow()
    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private var notificationCounter = 0

    // Khởi tạo sẵn dữ liệu mẫu đúng kịch bản thiết kế
    init {
        // Không auto-load sample data nữa, sẽ load sau khi user đăng nhập
    }

    // Lấy user hiện tại từ database (suspend)
    suspend fun getCurrentUserFromDb(): User? {
        val userId = _currentUserId.value ?: return null
        cloudDataRepository?.currentUser(userId)?.let { user ->
            cloudDataRepository?.cacheUser(user)
            _users.value = (_users.value.filterNot { it.id == userId } + user)
            return user
        }
        val context = appContext ?: return null
        val database = UserDatabaseHelper(context)
        val dbUser = database.getUserById(userId)
        return dbUser?.let {
            User(
                id = it.id,
                name = it.fullName,
                avatar = it.avatar,
                bankName = it.bankName,
                bankBin = it.bankBin,
                accountNumber = it.accountNumber,
                qrImageUri = it.qrImageUri
            )
        }
    }

    fun getUserById(id: String): User? {
        cloudDataRepository?.cachedUser(id)?.let { return it }
        _users.value.firstOrNull { it.id == id }?.let { return it }
        sampleUsers.firstOrNull { it.id == id }?.let { return it }
        val databaseUser = appContext?.let { UserDatabaseHelper(it).getUserById(id) } ?: return null
        return User(
            id = databaseUser.id,
            name = databaseUser.fullName,
            avatar = databaseUser.avatar,
            bankName = databaseUser.bankName,
            bankBin = databaseUser.bankBin,
            accountNumber = databaseUser.accountNumber,
            qrImageUri = databaseUser.qrImageUri
        )
    }

    suspend fun updateCurrentUserQrImage(uri: String): Boolean {
        val userId = _currentUserId.value ?: return false
        val cloud = cloudDataRepository ?: return false
        val cloudUri = cloud.updateCurrentUserQr(uri)
        val user = cloud.cachedUser(userId) ?: getCurrentUser()
        val updatedUser = user.copy(qrImageUri = cloudUri)
        cloud.cacheUser(updatedUser)
        _users.value = _users.value.filterNot { it.id == userId } + updatedUser
        return true
    }

    private fun persistCurrentData() {
        val userId = _currentUserId.value ?: return
        val visibleProjects = _projects.value.filter { userId in it.memberIds }
        val visibleProjectIds = visibleProjects.mapTo(HashSet()) { it.id }
        val visibleBills = _bills.value.filter {
            it.projectId in visibleProjectIds || it.creatorId == userId
        }
        val database = appDataDatabase ?: appContext?.let(::AppDataDatabaseHelper)
        if (database != null) {
            appDataDatabase = database
            database.saveSnapshot(visibleProjects, visibleBills)
        }
        cloudDataRepository?.saveSnapshot(visibleProjects, visibleBills)
    }

    suspend fun signIn(email: String, password: String): String =
        (cloudDataRepository ?: throw IllegalStateException(
            "Firebase chưa được cấu hình. Thêm google-services.json để đăng nhập online."
        )).signIn(email, password)

    suspend fun register(email: String, password: String, username: String, fullName: String): String =
        (cloudDataRepository ?: throw IllegalStateException(
            "Firebase chưa được cấu hình. Thêm google-services.json để đăng ký online."
        )).register(email, password, username, fullName)

    fun signOut() {
        cloudDataRepository?.stopListening()
        FirebaseApp.getApps(appContext ?: return).firstOrNull()?.let {
            FirebaseAuth.getInstance(it).signOut()
        }
    }

    private fun cloudUsersFallback(): List<User> = sampleUsers

    fun unpaidAmountForUser(bills: List<Bill>, userId: String): Long =
        bills.sumOf { it.unpaidAmountFor(userId) }

    // Fallback getCurrentUser for when database is not ready
    fun getCurrentUser(): User {
        val userId = _currentUserId.value
        return if (userId != null) {
            _users.value.firstOrNull { it.id == userId }
                ?: sampleUsers.firstOrNull { it.id == userId }
                ?: User(userId, "Đang tải...", "", "", "", "")
        } else {
            sampleUsers.first()
        }
    }

    fun restoreAuthenticatedSession() {
        val app = appContext?.let { FirebaseApp.getApps(it).firstOrNull() } ?: return
        FirebaseAuth.getInstance(app).currentUser?.uid?.let(::setCurrentUser)
    }

    // ==========================================
    // [MỤC 1.2 & 1.9.1] HÀM CẬP NHẬT HẠN MỨC & CẤP LẠI THỦ CÔNG
    // ==========================================
    fun updateProjectBudget(projectId: String, newLimit: Long, newCycleDays: Int = 0) {
        _projects.value = _projects.value.map { proj ->
            if (proj.id == projectId) {
                proj.copy(
                    budgetConfig = proj.budgetConfig.copy(
                        totalLimit = newLimit,
                        remainingAmount = newLimit,
                        isAutoRenew = (newCycleDays > 0),
                        cycleDays = newCycleDays,
                        daysLeft = newCycleDays,
                        lastRenewalTimestamp = System.currentTimeMillis()
                    )
                )
            } else proj
        }
        persistCurrentData()
        pushNotification("Cập nhật ngân sách", "Đã cập nhật hạn mức ${formatCurrency(newLimit)} (Cấp lại: ${if (newCycleDays > 0) "Tự động sau $newCycleDays ngày" else "Thủ công"})")
    }

    // Cấp lại ngân sách thủ công (Reset số tiền còn lại về bằng hạn mức ban đầu)
    fun renewBudgetManually(projectId: String? = null) {
        if (projectId != null) {
            _projects.value = _projects.value.map { proj ->
                if (proj.id == projectId) {
                    proj.copy(
                        budgetConfig = proj.budgetConfig.copy(
                            remainingAmount = proj.budgetConfig.totalLimit,
                            daysLeft = proj.budgetConfig.cycleDays,
                            lastRenewalTimestamp = System.currentTimeMillis()
                        )
                    )
                } else proj
            }
            persistCurrentData()
            val projName = _projects.value.firstOrNull { it.id == projectId }?.name ?: ""
            pushNotification("Cấp lại ngân sách", "Đã cấp lại thủ công hạn mức đầy đủ cho $projName!")
        } else {
            _projects.value = _projects.value.map { proj ->
                proj.copy(
                    budgetConfig = proj.budgetConfig.copy(
                        remainingAmount = proj.budgetConfig.totalLimit,
                        daysLeft = proj.budgetConfig.cycleDays,
                        lastRenewalTimestamp = System.currentTimeMillis()
                    )
                )
            }
            persistCurrentData()
            pushNotification("Cấp lại ngân sách", "Đã cấp lại thủ công hạn mức đầy đủ cho tất cả dự án!")
        }
    }

    // [YÊU CẦU KỸ THUẬT #2] Kiểm tra và tự động reset ngân sách khi hết chu kỳ
    fun checkAndAutoRenewBudgets() {
        val currentTime = System.currentTimeMillis()
        val oneDayInMillis = 24 * 60 * 60 * 1000L

        var hasAutoRenewed = false

        _projects.value = _projects.value.map { proj ->
            if (proj.budgetConfig.isAutoRenew && proj.budgetConfig.cycleDays > 0) {
                val daysPassed = ((currentTime - proj.budgetConfig.lastRenewalTimestamp) / oneDayInMillis).toInt()
                val newDaysLeft = proj.budgetConfig.cycleDays - daysPassed

                if (newDaysLeft <= 0) {
                    // Hết chu kỳ -> tự động reset
                    hasAutoRenewed = true
                    proj.copy(
                        budgetConfig = proj.budgetConfig.copy(
                            remainingAmount = proj.budgetConfig.totalLimit,
                            daysLeft = proj.budgetConfig.cycleDays,
                            lastRenewalTimestamp = currentTime
                        )
                    )
                } else {
                    // Cập nhật số ngày còn lại
                    proj.copy(
                        budgetConfig = proj.budgetConfig.copy(
                            daysLeft = newDaysLeft
                        )
                    )
                }
            } else {
                proj
            }
        }

        persistCurrentData()
        if (hasAutoRenewed) {
            pushNotification("Tự động cấp lại ngân sách", "Đã tự động reset ngân sách cho các dự án hết chu kỳ!")
        }
    }

    // ==========================================
    // [MỤC 1.7] TẠO DỰ ÁN MỚI
    // ==========================================
    fun createProject(name: String, modelType: String, limit: Long, cycleDays: Int, requireBillApproval: Boolean, enableElectricityWater: Boolean): Project {
        val inviteAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val secureRandom = SecureRandom()
        val inviteCode = buildString {
            repeat(8) { append(inviteAlphabet[secureRandom.nextInt(inviteAlphabet.length)]) }
        }
        val currentId = _currentUserId.value ?: throw IllegalStateException("User not logged in")
        val newProj = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            inviteCode = inviteCode,
            modelType = modelType,
            budgetConfig = BudgetConfig(totalLimit = limit, remainingAmount = limit, cycleDays = cycleDays, daysLeft = cycleDays),
            memberIds = listOf(currentId),
            isElectricityWaterEnabled = enableElectricityWater,
            requireBillApproval = requireBillApproval
        )
        _projects.value = _projects.value + newProj
        persistCurrentData()
        pushNotification("Tạo dự án mới", "Đã tạo dự án \"$name\" với mã mời: $inviteCode")
        return newProj
    }

    // ==========================================
    // [MỤC 1.8] THAM GIA DỰ ÁN BẰNG MÃ MỜI 6 KÝ TỰ
    // ==========================================
    suspend fun joinProjectByCode(code: String): Boolean {
        val currentId = _currentUserId.value ?: return false
        val cloud = cloudDataRepository ?: throw IllegalStateException(
            "Firebase chưa được cấu hình. Không thể gia nhập dự án online."
        )
        val currentUser = getCurrentUserFromDb()
            ?: throw IllegalStateException("Không tải được hồ sơ tài khoản.")
        val joinedProject = cloud.joinProject(code, currentUser) ?: return false
        _projects.value = (_projects.value.filterNot { it.id == joinedProject.id } + joinedProject)
            .sortedByDescending { it.id == joinedProject.id }
        persistCurrentData()
        pushNotification("Gia nhập dự án", "Bạn đã tham gia dự án: ${joinedProject.name}")
        return true
    }

    // ==========================================
    // [MỤC 1.9.3] THÊM HOẶC CẬP NHẬT BILL (NB1 / NB2)
    // ==========================================
    fun addBill(bill: Bill) {
        _bills.value = listOf(bill) + _bills.value
        if (!bill.isMergedParent) adjustProjectRemaining(bill.projectId, -bill.totalAmount)
        persistCurrentData()
        pushNotification("Hóa đơn mới", "Có hóa đơn mới: \"${bill.title}\" (${formatCurrency(bill.totalAmount)})")
    }

    fun updateBill(updatedBill: Bill) {
        _bills.value = _bills.value.map { if (it.id == updatedBill.id) updatedBill else it }
        persistCurrentData()
    }

    fun saveBillEdits(updatedBill: Bill): Boolean {
        val oldBill = _bills.value.firstOrNull { it.id == updatedBill.id } ?: return false
        val editorId = _currentUserId.value ?: return false
        if (oldBill.isMergedParent || oldBill.mergedIntoBillId != null) return false
        if (editorId != oldBill.creatorId && oldBill.mode != InputMode.NB2) return false
        if (updatedBill.title.isBlank()) return false

        val normalizedItems = updatedBill.items.map { item ->
            item.copy(name = item.name.trim(), quantity = item.quantity.coerceAtLeast(1))
        }
        val normalizedBill = updatedBill.copy(title = updatedBill.title.trim(), items = normalizedItems)
        if (oldBill.title == normalizedBill.title && oldBill.items == normalizedItems) return true
        val savedBill = normalizedBill.copy(
            memberStatuses = recalculateStatuses(oldBill, normalizedItems),
            editHistory = oldBill.editHistory + BillEditHistory(
                editorUserId = editorId,
                changes = describeBillChanges(oldBill, normalizedBill)
            )
        )
        _bills.value = _bills.value.map { if (it.id == oldBill.id) savedBill else it }
        adjustProjectRemaining(oldBill.projectId, oldBill.totalAmount - savedBill.totalAmount)
        persistCurrentData()
        pushNotification(
            "Bill đã chỉnh sửa",
            "${getUserById(editorId)?.name ?: "Thành viên"} đã sửa bill “${oldBill.title}”."
        )
        return true
    }

    private fun recalculateStatuses(
        bill: Bill,
        items: List<BillItem>
    ): Map<String, MemberBillStatus> {
        val amounts = bill.memberStatuses.keys.associateWith { 0L }.toMutableMap()
        val itemCounts = bill.memberStatuses.keys.associateWith { 0 }.toMutableMap()
        val payerSelectionUnchanged = bill.items.map { it.id to it.payerUserIds } ==
            items.map { it.id to it.payerUserIds }
        if (items.isEmpty()) {
            bill.memberStatuses.forEach { (userId, status) ->
                amounts[userId] = status.amountOwed
                itemCounts[userId] = status.itemCount
            }
        } else if (payerSelectionUnchanged && bill.totalAmount > 0L) {
            var distributed = 0L
            val members = bill.memberStatuses.keys.toList()
            members.forEachIndexed { index, userId ->
                val share = if (index == members.lastIndex) {
                    items.sumOf { it.totalPrice } - distributed
                } else {
                    items.sumOf { it.totalPrice } * bill.memberStatuses.getValue(userId).amountOwed / bill.totalAmount
                }
                amounts[userId] = share
                distributed += share
            }
            bill.memberStatuses.forEach { (userId, status) -> itemCounts[userId] = status.itemCount }
        } else {
            items.forEach { item ->
                val payers = item.payerUserIds.filter { it in amounts }.ifEmpty { amounts.keys.toList() }
                if (payers.isNotEmpty()) {
                    val baseAmount = item.totalPrice / payers.size
                    val remainder = item.totalPrice % payers.size
                    payers.forEachIndexed { index, userId ->
                        amounts[userId] = (amounts[userId] ?: 0L) + baseAmount + if (index < remainder) 1L else 0L
                        itemCounts[userId] = (itemCounts[userId] ?: 0) + 1
                    }
                }
            }
        }
        val creditorId = bill.advanceUserId ?: bill.creatorId
        return bill.memberStatuses.mapValues { (userId, status) ->
            val amount = amounts[userId] ?: 0L
            val creditorPaid = bill.debtObject == null && userId == creditorId
            status.copy(
                itemCount = itemCounts[userId] ?: 0,
                amountOwed = amount,
                isApproved = creditorPaid || amount == 0L,
                isPaid = creditorPaid || amount == 0L,
                proofImageUri = null,
                isCreatorConfirmed = creditorPaid || amount == 0L
            )
        }
    }

    private fun describeBillChanges(oldBill: Bill, updatedBill: Bill): String {
        val changes = mutableListOf<String>()
        if (oldBill.title != updatedBill.title) {
            changes += "Tên bill: ${oldBill.title} -> ${updatedBill.title}"
        }
        val updatedItemIds = updatedBill.items.mapTo(HashSet()) { it.id }
        val oldItemIds = oldBill.items.mapTo(HashSet()) { it.id }
        oldBill.items.filterNot { it.id in updatedItemIds }.forEach {
            changes += "Xóa món: ${it.name}"
        }
        updatedBill.items.filterNot { it.id in oldItemIds }.forEach {
            changes += "Thêm món: ${it.name} (${it.quantity} x ${formatCurrency(it.unitPrice)})"
        }
        updatedBill.items.forEach { updated ->
            val old = oldBill.items.firstOrNull { it.id == updated.id } ?: return@forEach
            val itemChanges = mutableListOf<String>()
            if (old.name != updated.name) itemChanges += "tên: ${old.name} -> ${updated.name}"
            if (old.quantity != updated.quantity) itemChanges += "SL: ${old.quantity} -> ${updated.quantity}"
            if (old.unitPrice != updated.unitPrice) {
                itemChanges += "đơn giá: ${formatCurrency(old.unitPrice)} -> ${formatCurrency(updated.unitPrice)}"
            }
            if (old.payerUserIds != updated.payerUserIds) {
                val payerNames = updated.payerUserIds
                    .map { getUserById(it)?.name ?: it }
                    .ifEmpty { listOf("Chưa chọn") }
                itemChanges += "người chia: ${payerNames.joinToString()}"
            }
            if (itemChanges.isNotEmpty()) changes += "${updated.name}: ${itemChanges.joinToString(", ")}"
        }
        return changes.ifEmpty { listOf("Cập nhật bill") }.joinToString("; ")
    }

    private fun adjustProjectRemaining(projectId: String, delta: Long) {
        if (delta == 0L) return
        var overrunMessage: String? = null
        _projects.value = _projects.value.map { project ->
            if (project.id == projectId) {
                val remaining = project.budgetConfig.remainingAmount + delta
                if (delta < 0L && remaining < 0L) {
                    overrunMessage =
                        "${project.name} đã vượt hạn mức ${formatCurrency(-remaining)} sau khi cập nhật bill."
                }
                project.copy(budgetConfig = project.budgetConfig.copy(remainingAmount = remaining))
            } else {
                project
            }
        }
        overrunMessage?.let { pushNotification("Cảnh báo vượt hạn mức", it) }
    }

    fun deleteBill(billId: String): Boolean {
        val userId = _currentUserId.value ?: return false
        val bill = _bills.value.firstOrNull { it.id == billId } ?: return false
        if (bill.creatorId != userId || bill.isMergedParent || bill.mergedIntoBillId != null) return false
        val database = appDataDatabase ?: appContext?.let(::AppDataDatabaseHelper) ?: return false
        database.deleteBill(billId)
        _bills.value = _bills.value.filterNot { it.id == billId }
        adjustProjectRemaining(bill.projectId, bill.totalAmount)
        persistCurrentData()
        cloudDataRepository?.deleteBill(bill.projectId, billId)
        pushNotification("Đã xóa bill", "Hóa đơn \"${bill.title}\" đã được xóa.")
        return true
    }

    // [MỤC 1.9.3 NB2] Cập nhật người trả món đồ (tick món)
    fun updateItemPayerUserIds(billId: String, itemId: String, newPayerIds: List<String>) {
        val bill = _bills.value.firstOrNull { it.id == billId } ?: return
        saveBillEdits(
            bill.copy(
                items = bill.items.map { item ->
                    if (item.id == itemId) item.copy(payerUserIds = newPayerIds) else item
                }
            )
        )
    }

    // ==========================================
    // [MỤC 1.9.4.2] THÀNH VIÊN BẤM DUYỆT PHẦN CỦA MÌNH
    // ==========================================
    fun approveMemberPortion(billId: String, userId: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId) {
                val currentStatus = bill.memberStatuses[userId] ?: return@map bill
                if (
                    currentStatus.amountOwed > 0L &&
                    bill.debtObject == null &&
                    !currentStatus.isCreatorConfirmed
                ) return@map bill
                val newStatuses = bill.memberStatuses.toMutableMap()
                newStatuses[userId] = currentStatus.copy(isApproved = true)
                bill.copy(memberStatuses = newStatuses)
            } else bill
        }
        persistCurrentData()
        pushNotification("Duyệt bill", "Thành viên ${getUserById(userId)?.name} đã duyệt phần chi tiêu!")
    }

    // ==========================================
    // A payment report clears debt only for object-backed bills; other bills await creditor confirmation.
    // ==========================================
    fun markMemberAsPaid(billId: String, userId: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId) {
                val status = bill.memberStatuses[userId] ?: return@map bill
                val newStatuses = bill.memberStatuses.toMutableMap()
                newStatuses[userId] = if (bill.debtObject != null) {
                    status.copy(
                        isApproved = true,
                        isPaid = true,
                        isCreatorConfirmed = true
                    )
                } else {
                    status.copy(isPaid = true)
                }
                bill.copy(memberStatuses = newStatuses)
            } else bill
        }
        persistCurrentData()
        val bill = _bills.value.firstOrNull { it.id == billId }
        val message = if (bill?.debtObject != null) {
            "${getUserById(userId)?.name} đã tự xác nhận thanh toán khoản nợ chung."
        } else {
            "${getUserById(userId)?.name} đã báo thanh toán; chờ người ứng xác nhận nhận tiền."
        }
        pushNotification("Thanh toán", message)
    }

    fun saveMemberPaymentProof(billId: String, userId: String, proofUri: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId) {
                val status = bill.memberStatuses[userId] ?: return@map bill
                if (bill.debtObject != null || status.isCreatorConfirmed) return@map bill
                val newStatuses = bill.memberStatuses.toMutableMap()
                newStatuses[userId] = status.copy(proofImageUri = proofUri)
                bill.copy(memberStatuses = newStatuses)
            } else {
                bill
            }
        }
        persistCurrentData()
        pushNotification("Minh chứng thanh toán", "${getUserById(userId)?.name} đã tải minh chứng lên bill.")
    }

    // ==========================================
    // [MỤC 1.9.4.5] CHỦ BILL XÁC NHẬN ĐÃ NHẬN TIỀN (TAB ĐỐI SOÁT)
    // ==========================================
    fun confirmPaymentByCreator(billId: String, userId: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId) {
                val status = bill.memberStatuses[userId] ?: return@map bill
                if (!status.isPaid || bill.debtObject != null) return@map bill
                val newStatuses = bill.memberStatuses.toMutableMap()
                newStatuses[userId] = status.copy(
                    isApproved = true,
                    isPaid = true,
                    isCreatorConfirmed = true
                )
                bill.copy(memberStatuses = newStatuses)
            } else bill
        }
        persistCurrentData()
        pushNotification("Đối soát thành công", "Đã xác nhận nhận tiền từ ${getUserById(userId)?.name}")
    }

    fun rejectPaymentByCreator(billId: String, userId: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId && bill.debtObject == null) {
                val status = bill.memberStatuses[userId] ?: return@map bill
                if (!status.isPaid || status.isCreatorConfirmed) return@map bill
                val newStatuses = bill.memberStatuses.toMutableMap()
                newStatuses[userId] = status.copy(
                    isApproved = false,
                    isPaid = false,
                    proofImageUri = null,
                    isCreatorConfirmed = false
                )
                bill.copy(memberStatuses = newStatuses)
            } else {
                bill
            }
        }
        persistCurrentData()
        pushNotification(
            "Cần gửi lại minh chứng",
            "Khoản thanh toán của ${getUserById(userId)?.name} bị từ chối. Vui lòng thanh toán và gửi lại minh chứng."
        )
    }

    // ==========================================
    // [MỤC 1.9.4.4] TỰ ĐỘNG GẠCH NỢ KHI CÓ THÔNG BÁO NGÂN HÀNG (SM_...)
    // ==========================================
    fun autoGachNoFromBankNotification(transferCode: String) {
        val parts = transferCode.split("_")
        // Định dạng chuẩn: SM_[MãDựÁn]_[MãBill]_[TênNgườiTrả]
        if (parts.size < 4) return
        val projectCode = parts[1]
        val billCode = parts[2]
        val payerName = parts[3]

        // Tìm bill tương ứng trong hệ thống
        val project = _projects.value.firstOrNull { it.inviteCode.equals(projectCode, ignoreCase = true) }
        val targetUser = (_users.value + sampleUsers)
            .firstOrNull { it.name.equals(payerName, ignoreCase = true) } ?: return

        _bills.value = _bills.value.map { bill ->
            // Kiểm tra khớp bill lẻ hoặc bill gộp
            val isTargetBill = (project != null && bill.projectId == project.id) &&
                    (bill.id.contains(billCode, ignoreCase = true) || bill.isMergedParent)

            if (isTargetBill) {
                // Bank notifications report payment; the creditor still confirms receipt manually.
                val status = bill.memberStatuses[targetUser.id]
                if (status != null) {
                    val newStatuses = bill.memberStatuses.toMutableMap()
                    newStatuses[targetUser.id] = status.copy(
                        isPaid = true
                    )
                    bill.copy(memberStatuses = newStatuses)
                } else if (bill.isMergedParent) {
                    // Cập nhật lệnh chuyển tiền xoay vòng
                    val newTransfers = bill.optimizedTransfers.map { tr ->
                        if (tr.fromUserId == targetUser.id) tr.copy(isPaid = true, isConfirmed = true) else tr
                    }
                    bill.copy(optimizedTransfers = newTransfers)
                } else bill
            } else bill
        }

        persistCurrentData()
        pushNotification(
            "Tự động gạch nợ VietQR",
            "Đã nhận diện mã $transferCode từ ngân hàng. Đã ghi nhận người trả đã thanh toán; người ứng cần xác nhận đã nhận tiền."
        )
    }

    // ==========================================
    // [MỤC 1.9.5] THUẬT TOÁN GỘP CÁC BILL ĐÃ DUYỆT & XOAY VÒNG TIỀN (MINIMUM CASH FLOW)
    // ==========================================
    fun mergeApprovedBills(projectId: String): Bill? {
        val project = _projects.value.firstOrNull { it.id == projectId } ?: return null

        // ----------------------------------------------------
        // BƯỚC 1: LỌC CÁC BILL ĐỦ ĐIỀU KIỆN ĐỂ GỘP
        // Điều kiện: Thuộc dự án này, đã duyệt (isAllApproved), chưa thanh toán xong (!isAllPaid)
        // và chưa từng bị gộp vào bill khác (mergedIntoBillId == null)
        // ----------------------------------------------------
        val eligibleBills = _bills.value.filter { bill ->
            bill.projectId == projectId &&
            !bill.isMergedParent &&
            bill.mergedIntoBillId == null &&
            bill.isAllApproved &&
            !bill.isAllPaid
        }

        if (eligibleBills.size < 2) {
            pushNotification("Không thể gộp", "Cần ít nhất 2 bill đã duyệt và chưa thanh toán để gộp xoay vòng tiền!")
            return null
        }

        // ----------------------------------------------------
        // BƯỚC 2: TÍNH TOÁN NET BALANCE (SỐ DƯ RÒNG) CỦA TỪNG NGƯỜI
        // Net Balance = (Tổng số tiền người này đã ứng chi) - (Tổng số tiền người này đã tiêu dùng)
        // Nếu Net > 0: Người này là CHỦ NỢ (ngồi nhận lại tiền)
        // Nếu Net < 0: Người này là CON NỢ (cần chuyển tiền đi)
        // ----------------------------------------------------
        val netBalanceMap = mutableMapOf<String, Long>()
        for (memberId in project.memberIds) {
            netBalanceMap[memberId] = 0L
        }

        for (bill in eligibleBills) {
            // Cộng tiền người ứng (Creator) đã bỏ ra
            val creditorId = bill.advanceUserId ?: bill.debtObject?.ownerUserId ?: bill.creatorId
            netBalanceMap[creditorId] = (netBalanceMap[creditorId] ?: 0L) + bill.totalAmount

            // Trừ tiền từng thành viên đã tiêu trong bill này
            for ((userId, status) in bill.memberStatuses) {
                netBalanceMap[userId] = (netBalanceMap[userId] ?: 0L) - status.amountOwed
            }
        }

        // ----------------------------------------------------
        // BƯỚC 3: ÁP DỤNG THUẬT TOÁN MINIMUM CASH FLOW (RÚT GỌN GIAO DỊCH TỐI ĐA)
        // Phân loại thành 2 nhóm: Người cần nhận tiền (Creditors) và Người cần trả tiền (Debtors)
        // Ghép cặp Greedy để triệt tiêu số tiền nợ nhanh nhất với số lần chuyển tiền ít nhất
        // ----------------------------------------------------
        class BalanceNode(val userId: String, var amount: Long)

        val creditors = mutableListOf<BalanceNode>() // Net > 0
        val debtors = mutableListOf<BalanceNode>()   // Net < 0

        for ((uId, net) in netBalanceMap) {
            if (net > 0) creditors.add(BalanceNode(uId, net))
            else if (net < 0) debtors.add(BalanceNode(uId, abs(net)))
        }

        val optimizedList = mutableListOf<OptimizedTransfer>()
        val mergedBillId = "MERGED_" + (100..999).random().toString()

        var cIdx = 0
        var dIdx = 0

        while (cIdx < creditors.size && dIdx < debtors.size) {
            val creditor = creditors[cIdx]
            val debtor = debtors[dIdx]

            // Số tiền chuyển giữa 2 người là giá trị nhỏ hơn giữa khoản nợ và khoản được nhận
            val settleAmount = min(creditor.amount, debtor.amount)

            val debtorUser = getUserById(debtor.userId)
            val debtorName = debtorUser?.name?.uppercase(Locale.getDefault()) ?: "USER"

            // [SỬA NHANH KHI VẤN ĐÁP] Tiền tố mã chuyển khoản xoay vòng
            val code = "SM_${project.inviteCode}_${mergedBillId}_$debtorName"

            optimizedList.add(
                OptimizedTransfer(
                    fromUserId = debtor.userId,
                    toUserId = creditor.userId,
                    amount = settleAmount,
                    transferCode = code,
                    isPaid = false,
                    isConfirmed = false
                )
            )

            creditor.amount -= settleAmount
            debtor.amount -= settleAmount

            if (creditor.amount == 0L) cIdx++
            if (debtor.amount == 0L) dIdx++
        }

        // ----------------------------------------------------
        // BƯỚC 4: TẠO BILL GỘP MỚI & GẮN ID ĐỂ LÀM TỐI MÀU CÁC BILL CŨ
        // Các bill cũ vẫn được giữ lại nhưng gán mergedIntoBillId để giao diện làm mờ (alpha = 0.45f)
        // ----------------------------------------------------
        val childIds = eligibleBills.map { it.id }

        // Người có số dư ròng lớn nhất sẽ làm creator của bill gộp.
        val primaryReceiverId = netBalanceMap
            .filterValues { it > 0L }
            .maxByOrNull { it.value }
            ?.key
            ?: _currentUserId.value
            ?: eligibleBills.first().creatorId

        val mergedBill = Bill(
            id = mergedBillId,
            projectId = projectId,
            title = "Bill Gộp Xoay Vòng Tiền (${eligibleBills.size} hóa đơn)",
            creatorId = primaryReceiverId,
            createdAt = System.currentTimeMillis(),
            mode = InputMode.NB1,
            isMergedParent = true,
            childBillIds = childIds,
            optimizedTransfers = optimizedList,
            memberStatuses = netBalanceMap.mapValues { (uId, net) ->
                MemberBillStatus(
                    userId = uId,
                    amountOwed = if (net < 0) abs(net) else 0L,
                    isApproved = true,
                    isPaid = (net >= 0)
                )
            }
        )

        // Cập nhật lại danh sách: Gán mergedIntoBillId cho các bill cũ và thêm bill gộp lên đầu
        val updatedBills = listOf(mergedBill) + _bills.value.map { bill ->
            if (childIds.contains(bill.id)) {
                bill.copy(mergedIntoBillId = mergedBillId)
            } else bill
        }

        val database = appDataDatabase ?: appContext?.let(::AppDataDatabaseHelper)
            ?: throw IllegalStateException("App database is not initialized")
        appDataDatabase = database
        val mergedBillsToSave = updatedBills.filter { it.id == mergedBillId || it.id in childIds }
        database.saveBills(mergedBillsToSave)
        _bills.value = updatedBills
        pushNotification(
            "Gộp bill thành công",
            "Đã gộp ${eligibleBills.size} bill thành 1 bill mới! Thuật toán tối ưu chỉ còn ${optimizedList.size} lần chuyển tiền!"
        )

        return mergedBill
    }

    // Xác nhận đã chuyển tiền trong Bill Gộp
    fun markOptimizedTransferPaid(transferId: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.isMergedParent) {
                val updatedList = bill.optimizedTransfers.map { tr ->
                    if (tr.id == transferId) tr.copy(isPaid = true) else tr
                }
                bill.copy(optimizedTransfers = updatedList)
            } else bill
        }
        persistCurrentData()
        pushNotification("Chuyển tiền xoay vòng", "Đã đánh dấu đã trả tiền cho giao dịch xoay vòng!")
    }

    // Chủ nợ xác nhận đã nhận được tiền trong Bill Gộp
    fun confirmOptimizedTransfer(transferId: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.isMergedParent) {
                val updatedList = bill.optimizedTransfers.map { tr ->
                    if (tr.id == transferId) tr.copy(isPaid = true, isConfirmed = true) else tr
                }
                bill.copy(optimizedTransfers = updatedList)
            } else bill
        }
        persistCurrentData()
        pushNotification("Xác nhận thành công", "Đã chốt nhận tiền giao dịch xoay vòng!")
    }

    fun pushNotification(title: String, message: String) {
        val notif = AppNotification(title = title, message = message)
        _notifications.value = listOf(notif) + _notifications.value.take(19)
        cloudDataRepository?.saveNotification(notif)

        // [YÊU CẦU KỸ THUẬT #3] Đẩy Local Notification ra status bar
        appContext?.let { context ->
            notificationCounter++
            NotificationHelper.showNotification(
                context = context,
                title = title,
                message = message,
                notificationId = NOTIFICATION_ID_BASE + notificationCounter
            )
        }
    }

    fun clearNotification(id: String) {
        _notifications.value = _notifications.value.filter { it.id != id }
        cloudDataRepository?.clearNotification(id)
    }

    // Helper định dạng tiền tệ VNĐ
    fun formatCurrency(amount: Long): String {
        val formatter = NumberFormat.getCurrencyInstance(Locale("vi", "VN"))
        return formatter.format(amount)
    }

    // Helper định dạng ngày tháng
    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("vi", "VN"))
        return sdf.format(Date(timestamp))
    }
}
