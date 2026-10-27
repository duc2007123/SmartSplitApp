package com.smartsplit.app.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.smartsplit.app.data.AppRepository

// ==========================================
// [MỤC 1.9.4.4] DỊCH VỤ TỰ ĐỘNG ĐỌC THÔNG BÁO NGÂN HÀNG & GẠCH NỢ
// ==========================================
class BankNotificationListenerService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // [SỬA NHANH KHI VẤN ĐÁP] Regex tìm chuỗi cú pháp SM_[MãDựÁn]_[MãBill]_[Tên]
        val regex = Regex("""SM_[A-Za-z0-9_]+""")

        val extras = sbn.notification?.extras ?: return
        val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        val fullContent = "$title $text $bigText"

        val match = regex.find(fullContent)
        if (match != null) {
            val transferCode = match.value
            Log.d("BankListener", "Phát hiện thông báo chứa mã chuyển khoản: $transferCode")
            
            // Bước 1: Kích hoạt tự động gạch nợ trong AppRepository
            AppRepository.autoGachNoFromBankNotification(transferCode)
        }
    }
}
