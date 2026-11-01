package com.smartsplit.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class CloudDataRepository(
    private val context: Context,
    private val onError: (String) -> Unit
) {
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()
    private val listeners = mutableListOf<ListenerRegistration>()
    private val projectListeners = mutableMapOf<String, ListenerRegistration>()
    private val memberListeners = mutableMapOf<String, ListenerRegistration>()
    private val billsByProject = ConcurrentHashMap<String, List<Bill>>()
    private val usersById = ConcurrentHashMap<String, User>()
    private val projectFingerprints = ConcurrentHashMap<String, String>()
    private val billFingerprints = ConcurrentHashMap<String, String>()
    private val uploads = ConcurrentHashMap<String, String>()

    @Volatile
    private var activeUserId: String? = null

    fun listen(
        userId: String,
        onProjects: (List<Project>) -> Unit,
        onBills: (List<Bill>) -> Unit,
        onUsers: (List<User>) -> Unit,
        onNotifications: (List<AppNotification>) -> Unit
    ) {
        stopListening()
        activeUserId = userId

        listeners += firestore.collection("users").document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError("Không thể đồng bộ hồ sơ: ${error.localizedMessage}")
                } else if (snapshot?.exists() == true) {
                    val user = snapshot.toUser(userId)
                    user?.let { onUsers(listOf(it)) }
                }
            }

        listeners += firestore.collection("users").document(userId)
            .collection("notifications")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError("Không thể đồng bộ thông báo: ${error.localizedMessage}")
                } else {
                    onNotifications(snapshot?.documents.orEmpty().mapNotNull { document ->
                        val title = document.getString("title") ?: return@mapNotNull null
                        val message = document.getString("message") ?: return@mapNotNull null
                        AppNotification(
                            id = document.id,
                            title = title,
                            message = message,
                            timestamp = document.getLong("timestamp") ?: 0L
                        )
                    }.sortedByDescending(AppNotification::timestamp).take(20))
                }
            }

        listeners += firestore.collection("projects")
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError("Không thể đồng bộ dự án: ${error.localizedMessage}")
                    return@addSnapshotListener
                }
                val projects = snapshot?.documents.orEmpty().mapNotNull { document ->
                    runCatching { codec().projectFromMap(document.data.orEmpty()) }
                        .onFailure { onError("Dữ liệu dự án ${document.id} không hợp lệ: ${it.localizedMessage}") }
                        .getOrNull()
                }
                val currentProjectIds = projects.mapTo(HashSet()) { it.id }
                projectFingerprints.keys.filterNot { it in currentProjectIds }
                    .forEach(projectFingerprints::remove)
                projects.forEach { project ->
                    projectFingerprints[project.id] = projectFingerprint(project)
                }
                onProjects(projects)
                updateProjectListeners(projects, onBills, onUsers)
            }
    }

    private fun updateProjectListeners(
        projects: List<Project>,
        onBills: (List<Bill>) -> Unit,
        onUsers: (List<User>) -> Unit
    ) {
        val projectIds = projects.mapTo(HashSet()) { it.id }
        (projectListeners.keys - projectIds).forEach { projectId ->
            projectListeners.remove(projectId)?.remove()
            memberListeners.remove(projectId)?.remove()
            billsByProject.remove(projectId)
        }
        if (projects.isEmpty()) onBills(emptyList())
        projects.forEach { project ->
            if (project.id !in projectListeners) {
                projectListeners[project.id] = firestore.collection("projects")
                    .document(project.id).collection("bills")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            onError("Không thể đồng bộ bill của ${project.name}: ${error.localizedMessage}")
                            return@addSnapshotListener
                        }
                        val bills = snapshot?.documents.orEmpty().mapNotNull { document ->
                            runCatching { codec().billFromMap(document.data.orEmpty()) }
                                .onFailure { onError("Dữ liệu bill ${document.id} không hợp lệ: ${it.localizedMessage}") }
                                .getOrNull()
                        }
                        val currentBillIds = bills.mapTo(HashSet()) { it.id }
                        billFingerprints.entries
                            .filter { it.value.substringBefore('|') == project.id && it.key !in currentBillIds }
                            .forEach { billFingerprints.remove(it.key) }
                        bills.forEach { bill ->
                            billFingerprints[bill.id] = billFingerprint(bill)
                        }
                        billsByProject[project.id] = bills
                        onBills(billsByProject.values.flatten())
                    }
                memberListeners[project.id] = firestore.collection("projects")
                    .document(project.id).collection("members")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            onError("Không thể đồng bộ thành viên: ${error.localizedMessage}")
                            return@addSnapshotListener
                        }
                        val users = snapshot?.documents.orEmpty().mapNotNull { document ->
                            document.toUser(document.id)
                        }
                        users.forEach { usersById[it.id] = it }
                        onUsers(usersById.values.toList())
                    }
            }
        }
    }

    fun stopListening() {
        listeners.forEach(ListenerRegistration::remove)
        listeners.clear()
        projectListeners.values.forEach(ListenerRegistration::remove)
        projectListeners.clear()
        memberListeners.values.forEach(ListenerRegistration::remove)
        memberListeners.clear()
        billsByProject.clear()
        usersById.clear()
        projectFingerprints.clear()
        billFingerprints.clear()
        activeUserId = null
    }

    fun cachedUser(userId: String): User? = usersById[userId]

    fun cacheUser(user: User) {
        usersById[user.id] = user
    }

    fun saveNotification(notification: AppNotification) {
        val userId = activeUserId ?: return
        scope.launch {
            try {
                firestore.collection("users").document(userId)
                    .collection("notifications").document(notification.id)
                    .set(
                        mapOf(
                            "title" to notification.title,
                            "message" to notification.message,
                            "timestamp" to notification.timestamp
                        )
                    ).await()
            } catch (error: Exception) {
                onError("Không thể đồng bộ thông báo: ${error.localizedMessage}")
            }
        }
    }

    fun clearNotification(notificationId: String) {
        val userId = activeUserId ?: return
        scope.launch {
            try {
                firestore.collection("users").document(userId)
                    .collection("notifications").document(notificationId).delete().await()
            } catch (error: Exception) {
                onError("Không thể xóa thông báo trên cloud: ${error.localizedMessage}")
            }
        }
    }

    suspend fun signIn(emailOrUsername: String, password: String): String {
        val input = emailOrUsername.trim()
        val email = if (input.contains('@')) {
            input.lowercase()
        } else {
            firestore.collection("usernames").document(input.lowercase())
                .get().await().getString("email")
                ?: UserDatabaseHelper(context).getUserByUsername(input)?.email
                ?: error("Không tìm thấy tài khoản '$input'. Hãy thử đăng nhập bằng email.")
        }
        val auth = FirebaseAuth.getInstance()
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Đăng nhập không trả về tài khoản.")
        return try {
            migrateLegacyData(user.uid, user.email ?: email)
            user.uid
        } catch (error: Exception) {
            auth.signOut()
            throw error
        }
    }

    suspend fun register(
        email: String,
        password: String,
        username: String,
        fullName: String
    ): String {
        val user = FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email.trim(), password).await().user
            ?: error("Tạo tài khoản không trả về người dùng.")
        try {
            val profile = User(user.uid, fullName.trim(), "", "", "", "").toCloudMap().toMutableMap().apply {
                put("username", username.trim())
                put("email", email.trim().lowercase())
            }
            val usernameRef = firestore.collection("usernames").document(username.trim().lowercase())
            firestore.runTransaction { transaction ->
                check(!transaction.get(usernameRef).exists()) {
                    "Tên đăng nhập đã được sử dụng."
                }
                transaction.set(usernameRef, mapOf("uid" to user.uid, "email" to email.trim().lowercase()))
                transaction.set(firestore.collection("users").document(user.uid), profile)
            }.await()
            migrateLegacyData(user.uid, email)
            return user.uid
        } catch (error: Exception) {
            user.delete().await()
            throw error
        }
    }

    suspend fun currentUser(userId: String): User? {
        val profile = firestore.collection("users").document(userId).get().await()
        return profile.toUser(userId)
    }

    fun saveSnapshot(projects: List<Project>, bills: List<Bill>) {
        val userId = activeUserId ?: return
        scope.launch {
            writeMutex.withLock {
                try {
                    if (activeUserId != userId) return@withLock
                    val changedProjects = projects.filter {
                        projectFingerprints[it.id] != projectFingerprint(it)
                    }
                    val changedBills = bills.filter {
                        billFingerprints[it.id] != billFingerprint(it)
                    }
                    changedProjects.forEach { project ->
                        val projectData = codec().projectToMap(project).toMutableMap()
                        if (projectFingerprints[project.id] != null) {
                            projectData.remove("memberIds")
                        }
                        firestore.collection("projects").document(project.id)
                            .set(projectData, SetOptions.merge()).await()
                        ensureCurrentUserIsProjectMember(project.id, userId)
                        firestore.collection("inviteCodes").document(project.inviteCode.uppercase())
                            .set(inviteCodeData(project), SetOptions.merge()).await()
                        projectFingerprints[project.id] = projectFingerprint(project)
                    }
                    changedBills.groupBy { it.projectId }.forEach { (projectId, projectBills) ->
                        val collection = firestore.collection("projects").document(projectId).collection("bills")
                        projectBills.forEach { bill ->
                            val cloudBill = uploadBillImages(bill)
                            collection.document(cloudBill.id)
                                .set(codec().billToMap(cloudBill), SetOptions.merge()).await()
                            billFingerprints[bill.id] = billFingerprint(bill)
                        }
                    }
                } catch (error: Exception) {
                    onError("Không thể lưu dữ liệu lên cloud: ${error.localizedMessage}")
                }
            }
        }
    }

    suspend fun createProject(project: Project) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("Bạn cần đăng nhập lại.")
        firestore.collection("projects").document(project.id)
            .set(codec().projectToMap(project)).await()
        ensureCurrentUserIsProjectMember(project.id, userId)
        firestore.collection("inviteCodes").document(project.inviteCode.uppercase())
            .set(inviteCodeData(project)).await()
    }

    suspend fun joinProject(code: String, user: User): Project? {
        val codeDoc = firestore.collection("inviteCodes").document(code.trim().uppercase()).get().await()
        val projectId = codeDoc.getString("projectId") ?: return null
        val projectRef = firestore.collection("projects").document(projectId)
        val projectData = codeDoc.get("projectData") as? Map<*, *> ?: return null
        val project = codec().projectFromMap(
            projectData.entries.associate { (key, value) -> key.toString() to value }
        )
        val joined = if (user.id in project.memberIds) project else project.copy(memberIds = project.memberIds + user.id)
        projectRef.update("memberIds", FieldValue.arrayUnion(user.id)).await()
        firestore.collection("projects").document(projectId).collection("members")
            .document(user.id).set(user.toCloudMap()).await()
        return joined
    }

    fun deleteBill(projectId: String, billId: String) {
        scope.launch {
            writeMutex.withLock {
                try {
                    firestore.collection("projects").document(projectId)
                        .collection("bills").document(billId).delete().await()
                } catch (error: Exception) {
                    onError("Không thể xóa bill trên cloud: ${error.localizedMessage}")
                }
            }
        }
    }

    suspend fun updateCurrentUserQr(uri: String): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: run {
            error("Bạn cần đăng nhập lại.")
        }
        val cloudUri = uploadImage(
            uri,
            firestore.collection("users").document(uid).collection("images"),
            uid
        )
        val userRef = firestore.collection("users").document(uid)
        userRef.update("qrImageUri", cloudUri).await()
        val memberProfile = userRef.get().await().data.orEmpty().toMutableMap().apply {
            put("qrImageUri", cloudUri)
        }
        firestore.collection("projects").whereArrayContains("memberIds", uid)
            .get().await().documents.forEach { project ->
                project.reference.collection("members").document(uid)
                    .set(projectMemberProfile(memberProfile), SetOptions.merge()).await()
            }
        uploads[uri] = cloudUri
        return cloudUri
    }

    suspend fun migrateLegacyQr(uri: String, userId: String): String? {
        if (uri.isBlank()) return null
        return uploadImage(
            uri,
            firestore.collection("users").document(userId).collection("images"),
            userId
        )
    }

    private suspend fun writeUserProjectAssets(
        userId: String,
        profile: User
    ): User {
        val cloudQr = migrateLegacyQr(profile.qrImageUri, userId)
        return profile.copy(qrImageUri = cloudQr.orEmpty())
    }

    private suspend fun ensureCurrentUserIsProjectMember(projectId: String, userId: String) {
        val profile = firestore.collection("users").document(userId).get().await()
        if (!profile.exists()) return
        firestore.collection("projects").document(projectId).collection("members")
            .document(userId).set(projectMemberProfile(profile.data.orEmpty()), SetOptions.merge()).await()
    }

    private fun projectMemberProfile(profile: Map<String, Any?>): Map<String, Any?> =
        mapOf(
            "id" to profile["id"],
            "name" to (profile["name"] ?: profile["fullName"]),
            "avatar" to profile["avatar"],
            "bankName" to profile["bankName"],
            "bankBin" to profile["bankBin"],
            "accountNumber" to profile["accountNumber"],
            "qrImageUri" to profile["qrImageUri"]
        )

    private suspend fun migrateLegacyData(userId: String, email: String) {
        val userRef = firestore.collection("users").document(userId)
        val profileSnapshot = userRef.get().await()
        if (profileSnapshot.getBoolean("legacyMigrationCompleted") == true) return

        val legacyUser = UserDatabaseHelper(context).getUserByEmail(email.trim())
        val legacyProjects = legacyUser?.let {
            AppDataDatabaseHelper(context).projectsForUser(it.id)
        }.orEmpty()
        val projectIds = legacyProjects.map { it.id }
        val legacyBills = AppDataDatabaseHelper(context).billsForProjects(projectIds)
        val profile = legacyUser?.let {
            User(
                id = userId,
                name = it.fullName,
                avatar = it.avatar,
                bankName = it.bankName,
                bankBin = it.bankBin,
                accountNumber = it.accountNumber,
                qrImageUri = it.qrImageUri
            )
        } ?: profileSnapshot.toUser(userId)
            ?: User(userId, email.substringBefore('@'), "", "", "", "")

        val migratedProfile = writeUserProjectAssets(userId, profile)
        val cloudProfile = migratedProfile.toCloudMap().toMutableMap().apply {
            put("email", email.trim().lowercase())
            put("legacyMigrationCompleted", false)
        }
        userRef.set(cloudProfile, SetOptions.merge()).await()

        val projectDocs = legacyProjects.map { project ->
            val migrated = project.copy(
                memberIds = project.memberIds.map { if (it == legacyUser?.id) userId else it }.distinct()
            )
            migrated
        }
        val billDocs = legacyBills.map { bill ->
            val migrated = bill.remapUserId(legacyUser?.id, userId)
            uploadBillImages(migrated)
        }

        projectDocs.forEach { project ->
            firestore.collection("projects").document(project.id)
                .set(codec().projectToMap(project), SetOptions.merge()).await()
            firestore.collection("projects").document(project.id).collection("members")
                .document(userId).set(projectMemberProfile(cloudProfile), SetOptions.merge()).await()
            firestore.collection("inviteCodes").document(project.inviteCode.uppercase())
                .set(inviteCodeData(project), SetOptions.merge()).await()
        }
        billDocs.forEach { bill ->
            firestore.collection("projects").document(bill.projectId).collection("bills")
                .document(bill.id).set(codec().billToMap(bill), SetOptions.merge()).await()
        }
        userRef.update("legacyMigrationCompleted", true).await()
    }

    private fun Bill.remapUserId(oldId: String?, newId: String): Bill {
        if (oldId == null || oldId == newId) return this
        return copy(
            creatorId = if (creatorId == oldId) newId else creatorId,
            advanceUserId = if (advanceUserId == oldId) newId else advanceUserId,
            debtObject = debtObject?.copy(ownerUserId = if (debtObject.ownerUserId == oldId) newId else debtObject.ownerUserId),
            items = items.map { item ->
                item.copy(payerUserIds = item.payerUserIds.map { if (it == oldId) newId else it }.distinct())
            },
            memberStatuses = memberStatuses.values.associate { status ->
                val mappedId = if (status.userId == oldId) newId else status.userId
                mappedId to status.copy(userId = mappedId)
            },
            optimizedTransfers = optimizedTransfers.map { transfer ->
                transfer.copy(
                    fromUserId = if (transfer.fromUserId == oldId) newId else transfer.fromUserId,
                    toUserId = if (transfer.toUserId == oldId) newId else transfer.toUserId
                )
            },
            editHistory = editHistory.map { record ->
                record.copy(editorUserId = if (record.editorUserId == oldId) newId else record.editorUserId)
            }
        )
    }

    private suspend fun uploadBillImages(bill: Bill): Bill {
        val images = firestore.collection("projects").document(bill.projectId).collection("images")
        val ownerId = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("Bạn cần đăng nhập lại để đồng bộ ảnh.")
        val receipt = bill.receiptImageUri?.let {
            uploadImage(it, images, ownerId, bill.projectId, bill.id)
        }
        val statuses = bill.memberStatuses.mapValues { (memberId, status) ->
            val proof = status.proofImageUri?.let {
                uploadImage(it, images, ownerId, bill.projectId, bill.id, memberId)
            }
            status.copy(proofImageUri = proof)
        }
        return bill.copy(receiptImageUri = receipt, memberStatuses = statuses)
    }

    private suspend fun uploadImage(
        uriString: String,
        collection: com.google.firebase.firestore.CollectionReference,
        ownerId: String,
        projectId: String? = null,
        billId: String? = null,
        memberId: String? = null
    ): String {
        if (uriString.startsWith("https://") || uriString.startsWith("http://") ||
            uriString.startsWith("firestore-image://")
        ) return uriString
        uploads[uriString]?.let { return it }
        val imageRef = collection.document(uriHash(uriString))
        val existing = imageRef.get().await()
        if (!existing.exists()) {
            val imageBytes = compressImage(uriString)
            val imageData = mutableMapOf<String, Any>(
                "ownerId" to ownerId,
                "contentType" to "image/jpeg",
                "data" to Base64.encodeToString(imageBytes, Base64.NO_WRAP),
                "createdAt" to System.currentTimeMillis()
            )
            projectId?.let { imageData["projectId"] = it }
            billId?.let { imageData["billId"] = it }
            memberId?.let { imageData["memberId"] = it }
            imageRef.set(imageData).await()
        }
        val collectionPath = collection.path.split('/')
        check(collectionPath.size == 3 && collectionPath[2] == "images") {
            "Đường dẫn lưu ảnh không hợp lệ."
        }
        val cloudUri = Uri.Builder()
            .scheme("firestore-image")
            .authority(collectionPath[0])
            .appendPath(collectionPath[1])
            .appendPath(collectionPath[2])
            .appendPath(imageRef.id)
            .build()
            .toString()
        uploads[uriString] = cloudUri
        return cloudUri
    }

    private fun compressImage(uriString: String): ByteArray {
        val uri = Uri.parse(uriString)
        val sourceBytes = readImageBytes(uri)
        var bitmap = decodeImage(sourceBytes)

        try {
            while (true) {
                for (quality in IMAGE_QUALITIES) {
                    val output = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
                    val bytes = output.toByteArray()
                    if (bytes.size <= MAX_COMPRESSED_IMAGE_BYTES) return bytes
                }
                check(maxOf(bitmap.width, bitmap.height) > MIN_IMAGE_SIDE) {
                    "Ảnh quá lớn để đồng bộ. Hãy chọn ảnh nhỏ hơn."
                }
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * IMAGE_SCALE_FACTOR).toInt().coerceAtLeast(1),
                    (bitmap.height * IMAGE_SCALE_FACTOR).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== bitmap) bitmap.recycle()
                bitmap = scaled
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun readImageBytes(uri: Uri): ByteArray {
        val resolver = context.contentResolver
        val directStream = try {
            resolver.openInputStream(uri)
        } catch (_: FileNotFoundException) {
            null
        }
        val bytes: ByteArray? = if (directStream != null) {
            directStream.use(::readBounded)
        } else {
            try {
                resolver.openTypedAssetFileDescriptor(uri, "image/*", null)
                    ?.use { descriptor ->
                        descriptor.createInputStream().use(::readBounded)
                    }
            } catch (_: FileNotFoundException) {
                null
            }
        }
        val resolvedBytes = bytes ?: error(
            "Bộ chọn ảnh không cung cấp dữ liệu đọc được " +
                "(scheme=${uri.scheme}, provider=${uri.authority ?: "không rõ"})."
        )
        check(resolvedBytes.isNotEmpty()) { "Ảnh được chọn đang trống." }
        return resolvedBytes
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            totalBytes += read
            check(totalBytes <= MAX_SOURCE_IMAGE_BYTES) {
                "Ảnh quá lớn để xử lý. Hãy chọn ảnh nhỏ hơn."
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun decodeImage(bytes: ByteArray): Bitmap {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            ) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_IMAGE_SIDE) {
                    val scale = longest.toFloat() / MAX_IMAGE_SIDE
                    decoder.setTargetSize(
                        (info.size.width / scale).toInt(),
                        (info.size.height / scale).toInt()
                    )
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        check(bounds.outWidth > 0 && bounds.outHeight > 0) {
            "Không đọc được định dạng ảnh đã chọn."
        }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > MAX_IMAGE_SIDE) {
            sampleSize *= 2
        }
        var bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: error("Không giải mã được ảnh đã chọn.")
        val orientation = ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    postRotate(90f)
                    postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    postRotate(270f)
                    postScale(-1f, 1f)
                }
            }
        }
        if (!matrix.isIdentity) {
            val oriented = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            if (oriented !== bitmap) bitmap.recycle()
            bitmap = oriented
        }
        return bitmap
    }

    private fun uriHash(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_IMAGE_SIDE = 1600
        const val MIN_IMAGE_SIDE = 640
        const val MAX_SOURCE_IMAGE_BYTES = 25 * 1024 * 1024
        const val MAX_COMPRESSED_IMAGE_BYTES = 600 * 1024
        const val IMAGE_SCALE_FACTOR = 0.8f
        val IMAGE_QUALITIES = listOf(85, 75, 65, 55, 45)
    }

    private fun codec() = AppDataDatabaseHelper(context)

    private fun projectFingerprint(project: Project): String =
        codec().projectToMap(project).toString()

    private fun inviteCodeData(project: Project): Map<String, Any> = mapOf(
        "projectId" to project.id,
        "projectData" to codec().projectToMap(project)
    )

    private fun billFingerprint(bill: Bill): String =
        "${bill.projectId}|${codec().billToMap(bill)}"

    private fun User.toCloudMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "name" to name,
        "avatar" to avatar,
        "username" to "",
        "email" to "",
        "bankName" to bankName,
        "bankBin" to bankBin,
        "accountNumber" to accountNumber,
        "qrImageUri" to qrImageUri
    )

    private fun com.google.firebase.firestore.DocumentSnapshot.toUser(id: String): User? {
        if (!exists()) return null
        return User(
            id = id,
            name = getString("name") ?: getString("fullName") ?: "",
            avatar = getString("avatar") ?: "",
            bankName = getString("bankName") ?: "",
            bankBin = getString("bankBin") ?: "",
            accountNumber = getString("accountNumber") ?: "",
            qrImageUri = getString("qrImageUri") ?: ""
        )
    }
}
