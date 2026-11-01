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
    object Login : Screen()
    object Register : Screen()
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
        com.smartsplit.app.data.AppRepository.restoreAuthenticatedSession()

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
    val context = androidx.compose.ui.platform.LocalContext.current

    // [SỬA NHANH KHI VẤN ĐÁP] Màn hình khởi động mặc định của App
    var currentScreen by remember {
        mutableStateOf(
            if (com.smartsplit.app.data.AppRepository.currentUserId.value != null) {
                Screen.Home
            } else {
                Screen.Login
            }
        )
    }
    var currentUserId by remember {
        mutableStateOf(com.smartsplit.app.data.AppRepository.currentUserId.value)
    }

    // Quản lý nút Back vật lý trên điện thoại Android
    BackHandler(enabled = currentScreen !is Screen.Home && currentScreen !is Screen.Login) {
        currentScreen = when (val screen = currentScreen) {
            is Screen.BillDetail -> {
                val projectId = com.smartsplit.app.data.AppRepository.bills.value
                    .firstOrNull { it.id == screen.billId }
                    ?.projectId
                projectId?.let { Screen.ProjectDetail(it) } ?: Screen.Home
            }
            is Screen.ProjectDetail -> {
                // Bước 2: Khi ở màn hình ProjectDetail, bấm Back quay về Tab chính
                Screen.Home
            }
            is Screen.Register -> {
                Screen.Login
            }
            else -> Screen.Home
        }
    }

    // Điều hướng chuyển đổi giữa các màn hình
    when (val screen = currentScreen) {
        is Screen.Login -> {
            // MÀN HÌNH ĐĂNG NHẬP
            LoginScreen(
                context = context,
                onLoginSuccess = { userId ->
                    currentUserId = userId
                    com.smartsplit.app.data.AppRepository.setCurrentUser(userId)
                    currentScreen = Screen.Home
                },
                onNavigateToRegister = {
                    currentScreen = Screen.Register
                }
            )
        }

        is Screen.Register -> {
            // MÀN HÌNH ĐĂNG KÝ
            RegisterScreen(
                context = context,
                onRegisterSuccess = { userId ->
                    currentUserId = userId
                    com.smartsplit.app.data.AppRepository.setCurrentUser(userId)
                    currentScreen = Screen.Home
                },
                onNavigateToLogin = {
                    currentScreen = Screen.Login
                }
            )
        }

        is Screen.Home -> {
            // MÀN HÌNH 1: TAB CHÍNH (Từ Mục 1.1 đến 1.9)
            HomeScreen(
                onNavigateToProject = { projectId ->
                    currentScreen = Screen.ProjectDetail(projectId)
                },
                onNavigateToBill = { billId ->
                    currentScreen = Screen.BillDetail(billId)
                },
                onLogout = {
                    com.smartsplit.app.data.AppRepository.signOut()
                    currentUserId = null
                    com.smartsplit.app.data.AppRepository.setCurrentUser(null)
                    currentScreen = Screen.Login
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
                onBack = { projectId -> currentScreen = Screen.ProjectDetail(projectId) }
            )
        }
    }
}
