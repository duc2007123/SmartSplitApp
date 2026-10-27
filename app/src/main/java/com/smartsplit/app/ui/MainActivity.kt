package com.smartsplit.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.smartsplit.app.ui.theme.SmartSplitAppTheme

// ==========================================
// [NAVIGATION] ĐỊNH NGHĨA CÁC MÀN HÌNH TRONG ỨNG DỤNG
// ==========================================
sealed class Screen {
    object Home : Screen()
    data class ProjectDetail(val projectId: String) : Screen()
    data class BillDetail(val billId: String) : Screen()
}

// ==========================================
// [MAIN ACTIVITY] ĐIỀU HƯỚNG NỐI TOÀN BỘ CÁC MÀN HÌNH TỪ 1.1 ĐẾN 1.9.5
// ==========================================
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // [YÊU CẦU KỸ THUẬT #3] Khởi tạo NotificationHelper và AppRepository
        com.smartsplit.app.data.AppRepository.init(this)

        setContent {
            SmartSplitAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

// ==========================================
// HÀM QUẢN LÝ ĐIỀU HƯỚNG TRUNG TÂM (NAVIGATION CONTROLLER)
// ==========================================
@Composable
fun AppNavigation() {
    // [SỬA NHANH KHI VẤN ĐÁP] Màn hình khởi động mặc định của App
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    // Quản lý nút Back vật lý trên điện thoại Android
    BackHandler(enabled = currentScreen !is Screen.Home) {
        currentScreen = when (currentScreen) {
            is Screen.BillDetail -> {
                // Bước 1: Khi ở màn hình BillDetail, bấm Back sẽ quay về màn hình trước đó
                Screen.Home
            }
            is Screen.ProjectDetail -> {
                // Bước 2: Khi ở màn hình ProjectDetail, bấm Back quay về Tab chính
                Screen.Home
            }
            else -> Screen.Home
        }
    }

    // Điều hướng chuyển đổi giữa các màn hình
    when (val screen = currentScreen) {
        is Screen.Home -> {
            // MÀN HÌNH 1: TAB CHÍNH (Từ Mục 1.1 đến 1.9)
            HomeScreen(
                onNavigateToProject = { projectId ->
                    currentScreen = Screen.ProjectDetail(projectId)
                },
                onNavigateToBill = { billId ->
                    currentScreen = Screen.BillDetail(billId)
                }
            )
        }

        is Screen.ProjectDetail -> {
            // MÀN HÌNH 2: CHI TIẾT DỰ ÁN (Từ Mục 1.9.1 đến 1.9.5)
            ProjectDetailScreen(
                projectId = screen.projectId,
                onBack = { currentScreen = Screen.Home },
                onNavigateToBill = { billId ->
                    currentScreen = Screen.BillDetail(billId)
                }
            )
        }

        is Screen.BillDetail -> {
            // MÀN HÌNH 3: CHI TIẾT HÓA ĐƠN & VIETQR (Từ Mục 1.9.4.1 đến 1.9.4.6)
            BillDetailScreen(
                billId = screen.billId,
                onBack = { currentScreen = Screen.Home }
            )
        }
    }
}
