package com.smartsplit.app.data

import android.content.Context
import com.smartsplit.app.service.NotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
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
    val accountNumber: String
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
    val inviteCode: String, // [SỬA NHANH KHI VẤN ĐÁP] Mã mời 6 ký tự viết hoa
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

// ==========================================
// [MỤC 1.9.4 & 1.9.5] THÔNG TIN TOÀN DIỆN CỦA MỘT HÓA ĐƠN (BILL THƯỜNG HOẶC BILL GỘP)
// ==========================================
data class Bill(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String,
    val creatorId: String,                  // Người ứng tiền trước
    val createdAt: Long = System.currentTimeMillis(),
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
    val optimizedTransfers: List<OptimizedTransfer> = emptyList() // Danh sách lệnh chuyển tiền xoay vòng
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

    // Khởi tạo context để push local notification
    fun init(context: Context) {
        appContext = context
        NotificationHelper.createNotificationChannel(context)
    }

    // Danh sách 3 tài khoản mẫu để test phân quyền theo Mục 1.1
    val sampleUsers = listOf(
        User("u1", "Tuấn", "avatar_tuan", "VietinBank", "970415", "101869999999"),
        User("u2", "Hùng", "avatar_hung", "MBBank", "970422", "0987654321"),
        User("u3", "Nam", "avatar_nam", "Techcombank", "970407", "1903456789")
    )

    // Tài khoản hiện đang đăng nhập (Mục 1.1)
    private val _currentUserId = MutableStateFlow("u1") // Mặc định là Tuấn
    val currentUserId: StateFlow<String> = _currentUserId.asStateFlow()

    // Danh sách dự án (Mục 1.9)
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects: StateFlow<List<Project>> = _projects.asStateFlow()

    // Danh sách hóa đơn (Mục 1.9.4)
    private val _bills = MutableStateFlow<List<Bill>>(emptyList())
    val bills: StateFlow<List<Bill>> = _bills.asStateFlow()

    // Danh sách thông báo đẩy (In-App Push Notifications)
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private var notificationCounter = 0

    // Khởi tạo sẵn dữ liệu mẫu đúng kịch bản thiết kế
    init {
        initSampleData()
    }

    private fun initSampleData() {
        // Tạo 2 dự án mẫu: "Phòng trọ 302" và "Chuyến đi Sapa"
        val p1 = Project(
            id = "p1",
            name = "Phòng trọ 302",
            inviteCode = "P302VN",
            iconName = "home",
            modelType = "Phòng trọ",
            budgetConfig = BudgetConfig(totalLimit = 4_000_000L, remainingAmount = 2_950_000L, isAutoRenew = false, cycleDays = 0, daysLeft = 0, lastRenewalTimestamp = System.currentTimeMillis()),
            memberIds = listOf("u1", "u2", "u3"),
            isElectricityWaterEnabled = true,
            requireBillApproval = false
        )

        val p2 = Project(
            id = "p2",
            name = "Chuyến đi Sapa",
            inviteCode = "SAPA24",
            iconName = "flight",
            modelType = "Du lịch",
            budgetConfig = BudgetConfig(totalLimit = 6_000_000L, remainingAmount = 5_200_000L, isAutoRenew = false, cycleDays = 0, daysLeft = 0, lastRenewalTimestamp = System.currentTimeMillis()),
            memberIds = listOf("u1", "u2", "u3"),
            isElectricityWaterEnabled = false,
            requireBillApproval = false
        )

        _projects.value = listOf(p1, p2)

        // Tạo sẵn 3 bill ĐÃ DUYỆT trong "Phòng trọ 302" để test ngay tính năng Gộp bill 1.9.5:
        // Bill 1: Tuấn ứng 300k (mỗi người 100k)
        val bill1 = Bill(
            id = "b1",
            projectId = "p1",
            title = "Tiền điện nước & vệ sinh tháng 10",
            creatorId = "u1",
            createdAt = System.currentTimeMillis() - 86400000L * 3,
            mode = InputMode.NB1,
            receiptImageUri = "https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=400",
            items = listOf(
                BillItem(name = "Tiền điện", quantity = 1, unitPrice = 200_000L, payerUserIds = listOf("u1", "u2", "u3")),
                BillItem(name = "Tiền nước & rác", quantity = 1, unitPrice = 100_000L, payerUserIds = listOf("u1", "u2", "u3"))
            ),
            memberStatuses = mapOf(
                "u1" to MemberBillStatus("u1", 2, 100_000L, isApproved = true, isPaid = true, isCreatorConfirmed = true),
                "u2" to MemberBillStatus("u2", 2, 100_000L, isApproved = true, isPaid = false, isCreatorConfirmed = false),
                "u3" to MemberBillStatus("u3", 2, 100_000L, isApproved = true, isPaid = false, isCreatorConfirmed = false)
            )
        )

        // Bill 2: Hùng ứng 600k (mỗi người 200k)
        val bill2 = Bill(
            id = "b2",
            projectId = "p1",
            title = "Mua nồi chiên không dầu & gia vị chung",
            creatorId = "u2",
            createdAt = System.currentTimeMillis() - 86400000L * 2,
            mode = InputMode.NB1,
            receiptImageUri = "https://images.unsplash.com/photo-1556742049-0a67c5574f73?w=400",
            items = listOf(
                BillItem(name = "Nồi chiên không dầu", quantity = 1, unitPrice = 600_000L, payerUserIds = listOf("u1", "u2", "u3"))
            ),
            memberStatuses = mapOf(
                "u1" to MemberBillStatus("u1", 1, 200_000L, isApproved = true, isPaid = false, isCreatorConfirmed = false),
                "u2" to MemberBillStatus("u2", 1, 200_000L, isApproved = true, isPaid = true, isCreatorConfirmed = true),
                "u3" to MemberBillStatus("u3", 1, 200_000L, isApproved = true, isPaid = false, isCreatorConfirmed = false)
            )
        )

        // Bill 3: Nam ứng 150k (mỗi người 50k)
        val bill3 = Bill(
            id = "b3",
            projectId = "p1",
            title = "Mua bình nước lọc 20L & túi rác",
            creatorId = "u3",
            createdAt = System.currentTimeMillis() - 86400000L * 1,
            mode = InputMode.NB2,
            receiptImageUri = "https://images.unsplash.com/photo-1586528116311-ad8dd3c8310d?w=400",
            items = listOf(
                BillItem(name = "Nước khoáng Lavie 20L", quantity = 2, unitPrice = 60_000L, payerUserIds = listOf("u1", "u2", "u3")),
                BillItem(name = "Túi rác sinh học", quantity = 1, unitPrice = 30_000L, payerUserIds = listOf("u1", "u2", "u3"))
            ),
            memberStatuses = mapOf(
                "u1" to MemberBillStatus("u1", 2, 50_000L, isApproved = true, isPaid = false, isCreatorConfirmed = false),
                "u2" to MemberBillStatus("u2", 2, 50_000L, isApproved = true, isPaid = false, isCreatorConfirmed = false),
                "u3" to MemberBillStatus("u3", 2, 50_000L, isApproved = true, isPaid = true, isCreatorConfirmed = true)
            )
        )

        _bills.value = listOf(bill1, bill2, bill3)
    }

    // ==========================================
    // [MỤC 1.1] HÀM CHUYỂN NHANH TÀI KHOẢN TEST PHÂN QUYỀN
    // ==========================================
    fun switchUser(userId: String) {
        _currentUserId.value = userId
        val user = getUserById(userId)
        pushNotification("Chuyển tài khoản", "Đã chuyển sang tài khoản test: ${user?.name}")
    }

    fun getCurrentUser(): User {
        return sampleUsers.firstOrNull { it.id == _currentUserId.value } ?: sampleUsers.first()
    }

    fun getUserById(id: String): User? {
        return sampleUsers.firstOrNull { it.id == id }
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

        if (hasAutoRenewed) {
            pushNotification("Tự động cấp lại ngân sách", "Đã tự động reset ngân sách cho các dự án hết chu kỳ!")
        }
    }

    // ==========================================
    // [MỤC 1.7] TẠO DỰ ÁN MỚI
    // ==========================================
    fun createProject(name: String, modelType: String, limit: Long, cycleDays: Int, requireBillApproval: Boolean, enableElectricityWater: Boolean): Project {
        // [SỬA NHANH KHI VẤN ĐÁP] Tạo ngẫu nhiên mã mời 6 ký tự
        val inviteCode = "P" + (100..999).random().toString() + "VN"
        val newProj = Project(
            id = UUID.randomUUID().toString(),
            name = name,
            inviteCode = inviteCode,
            modelType = modelType,
            budgetConfig = BudgetConfig(totalLimit = limit, remainingAmount = limit, cycleDays = cycleDays, daysLeft = cycleDays),
            memberIds = listOf(_currentUserId.value),
            isElectricityWaterEnabled = enableElectricityWater,
            requireBillApproval = requireBillApproval
        )
        _projects.value = _projects.value + newProj
        pushNotification("Tạo dự án mới", "Đã tạo dự án \"$name\" với mã mời: $inviteCode")
        return newProj
    }

    // ==========================================
    // [MỤC 1.8] THAM GIA DỰ ÁN BẰNG MÃ MỜI 6 KÝ TỰ
    // ==========================================
    fun joinProjectByCode(code: String): Boolean {
        val proj = _projects.value.firstOrNull { it.inviteCode.equals(code.trim(), ignoreCase = true) }
        return if (proj != null) {
            val currentId = _currentUserId.value
            if (!proj.memberIds.contains(currentId)) {
                _projects.value = _projects.value.map {
                    if (it.id == proj.id) it.copy(memberIds = it.memberIds + currentId) else it
                }
            }
            pushNotification("Gia nhập dự án", "Bạn đã tham gia dự án: ${proj.name}")
            true
        } else false
    }

    // ==========================================
    // [MỤC 1.9.3] THÊM HOẶC CẬP NHẬT BILL (NB1 / NB2)
    // ==========================================
    fun addBill(bill: Bill) {
        _bills.value = listOf(bill) + _bills.value
        pushNotification("Hóa đơn mới", "Có hóa đơn mới: \"${bill.title}\" (${formatCurrency(bill.totalAmount)})")
    }

    fun updateBill(updatedBill: Bill) {
        _bills.value = _bills.value.map { if (it.id == updatedBill.id) updatedBill else it }
    }

    // [MỤC 1.9.3 NB2] Cập nhật người trả món đồ (tick món)
    fun updateItemPayerUserIds(billId: String, itemId: String, newPayerIds: List<String>) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId) {
                val updatedItems = bill.items.map { item ->
                    if (item.id == itemId) {
                        item.copy(payerUserIds = newPayerIds)
                    } else {
                        item
                    }
                }
                bill.copy(items = updatedItems)
            } else {
                bill
            }
        }
        pushNotification("Cập nhật món", "Đã cập nhật danh sách người trả món!")
    }

    // ==========================================
    // [MỤC 1.9.4.2] THÀNH VIÊN BẤM DUYỆT PHẦN CỦA MÌNH
    // ==========================================
    fun approveMemberPortion(billId: String, userId: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId) {
                val currentStatus = bill.memberStatuses[userId] ?: return@map bill
                val newStatuses = bill.memberStatuses.toMutableMap()
                newStatuses[userId] = currentStatus.copy(isApproved = true)
                bill.copy(memberStatuses = newStatuses)
            } else bill
        }
        pushNotification("Duyệt bill", "Thành viên ${getUserById(userId)?.name} đã duyệt phần chi tiêu!")
    }

    // ==========================================
    // [MỤC 1.9.4.4] ẤN ĐÃ TRẢ & TỰ ĐỘNG TÍCH [☑️ ĐÃ DUYỆT] + [☑️ ĐÃ TRẢ]
    // ==========================================
    fun markMemberAsPaid(billId: String, userId: String, proofUri: String? = null) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId) {
                val status = bill.memberStatuses[userId] ?: return@map bill
                val newStatuses = bill.memberStatuses.toMutableMap()

                // Bước 1: Tự động tích ĐÃ DUYỆT = true và ĐÃ TRẢ = true theo đúng tài liệu
                newStatuses[userId] = status.copy(
                    isApproved = true,
                    isPaid = true,
                    proofImageUri = proofUri ?: status.proofImageUri
                )
                bill.copy(memberStatuses = newStatuses)
            } else bill
        }
        pushNotification("Thanh toán", "${getUserById(userId)?.name} đã chuyển tiền kèm minh chứng!")
    }

    // ==========================================
    // [MỤC 1.9.4.5] CHỦ BILL XÁC NHẬN ĐÃ NHẬN TIỀN (TAB ĐỐI SOÁT)
    // ==========================================
    fun confirmPaymentByCreator(billId: String, userId: String) {
        _bills.value = _bills.value.map { bill ->
            if (bill.id == billId) {
                val status = bill.memberStatuses[userId] ?: return@map bill
                val newStatuses = bill.memberStatuses.toMutableMap()
                newStatuses[userId] = status.copy(
                    isApproved = true,
                    isPaid = true,
                    isCreatorConfirmed = true
                )
                bill.copy(memberStatuses = newStatuses)
            } else bill
        }
        pushNotification("Đối soát thành công", "Chủ bill đã xác nhận nhận đủ tiền từ ${getUserById(userId)?.name}")
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
        val targetUser = sampleUsers.firstOrNull { it.name.equals(payerName, ignoreCase = true) } ?: return

        _bills.value = _bills.value.map { bill ->
            // Kiểm tra khớp bill lẻ hoặc bill gộp
            val isTargetBill = (project != null && bill.projectId == project.id) &&
                    (bill.id.contains(billCode, ignoreCase = true) || bill.isMergedParent)

            if (isTargetBill) {
                // Tự động gạch nợ cho người này: Sáng cả [☑️ Đã duyệt] và [☑️ Đã trả] và xác nhận luôn!
                val status = bill.memberStatuses[targetUser.id]
                if (status != null) {
                    val newStatuses = bill.memberStatuses.toMutableMap()
                    newStatuses[targetUser.id] = status.copy(
                        isApproved = true,
                        isPaid = true,
                        isCreatorConfirmed = true,
                        proofImageUri = "https://images.unsplash.com/photo-1554224154-26032ffc0d07?w=400"
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

        pushNotification(
            "Tự động gạch nợ VietQR",
            "Đã nhận diện mã $transferCode từ ngân hàng! Đã tích ☑️ Đã duyệt & ☑️ Đã trả cho ${targetUser.name}"
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
            val creatorId = bill.creatorId
            netBalanceMap[creatorId] = (netBalanceMap[creatorId] ?: 0L) + bill.totalAmount

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

        // Người nhận nhiều nhất sẽ làm Creator của bill gộp
        val primaryReceiverId = creditors.maxByOrNull { it.amount }?.userId ?: _currentUserId.value

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
        _bills.value = listOf(mergedBill) + _bills.value.map { bill ->
            if (childIds.contains(bill.id)) {
                bill.copy(mergedIntoBillId = mergedBillId)
            } else bill
        }

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
        pushNotification("Xác nhận thành công", "Đã chốt nhận tiền giao dịch xoay vòng!")
    }

    fun pushNotification(title: String, message: String) {
        val notif = AppNotification(title = title, message = message)
        _notifications.value = listOf(notif) + _notifications.value.take(19)

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
