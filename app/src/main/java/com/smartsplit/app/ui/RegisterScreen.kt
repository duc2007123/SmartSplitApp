package com.smartsplit.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    context: android.content.Context,
    onRegisterSuccess: (userId: String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Đăng Ký Tài Khoản") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "SmartSplit",
                fontSize = 32.sp,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Tạo tài khoản mới",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Username field
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Tên đăng nhập") },
                placeholder = { InputPlaceholder("Nhập tên đăng nhập") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = BillInputFieldColors(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Email field
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                placeholder = { InputPlaceholder("Nhập email") },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = BillInputFieldColors(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Full name field
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Họ và tên") },
                placeholder = { InputPlaceholder("Nhập họ và tên") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                colors = BillInputFieldColors(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Password field
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Mật khẩu") },
                placeholder = { InputPlaceholder("Nhập mật khẩu") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = BillInputFieldColors(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Error message
            errorMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // Register button
            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null

                    if (username.isBlank() || email.isBlank() || password.isBlank() || fullName.isBlank()) {
                        errorMessage = "Vui lòng điền đầy đủ thông tin"
                        isLoading = false
                        return@Button
                    }

                    if (password.length < 6) {
                        errorMessage = "Mật khẩu phải có ít nhất 6 ký tự"
                        isLoading = false
                        return@Button
                    }

                    coroutineScope.launch {
                        try {
                            val userId = com.smartsplit.app.data.AppRepository.register(
                                email = email,
                                password = password,
                                username = username,
                                fullName = fullName
                            )
                            isLoading = false
                            onRegisterSuccess(userId)
                        } catch (e: Exception) {
                            errorMessage = "Đăng ký thất bại: ${e.localizedMessage ?: "Vui lòng thử lại."}"
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Đăng Ký")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigate to login
            TextButton(onClick = onNavigateToLogin) {
                Text("Đã có tài khoản? Đăng nhập")
            }
        }
    }
}
