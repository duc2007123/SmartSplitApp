package com.smartsplit.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.FirebaseException
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import android.util.Base64
import com.smartsplit.app.service.ReceiptOcrAnalyzer

private sealed interface LocalImageState {
    data object Loading : LocalImageState
    data class Loaded(val bitmap: Bitmap) : LocalImageState
    data object Failed : LocalImageState
}

@Composable
fun LocalImagePreview(
    uri: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current.applicationContext
    val imageState by produceState<LocalImageState>(initialValue = LocalImageState.Loading, uri) {
        value = withContext(Dispatchers.IO) {
            try {
                val parsedUri = Uri.parse(uri)
                val bitmap = if (parsedUri.scheme == "firestore-image") {
                    val collectionName = parsedUri.authority
                        ?: error("Đường dẫn ảnh cloud không hợp lệ.")
                    val segments = parsedUri.pathSegments
                    require(collectionName in setOf("users", "projects"))
                    require(segments.size == 3 && segments[1] == "images")
                    val encoded = FirebaseFirestore.getInstance()
                        .collection(collectionName)
                        .document(segments[0])
                        .collection("images")
                        .document(segments[2])
                        .get()
                        .await()
                        .getString("data")
                        ?: error("Không tìm thấy ảnh đã đồng bộ.")
                    require(encoded.length < 900_000) { "Ảnh cloud vượt kích thước cho phép." }
                    decodeCloudImage(Base64.decode(encoded, Base64.DEFAULT))
                } else if (parsedUri.scheme == "https" || parsedUri.scheme == "http") {
                    val bytes = FirebaseStorage.getInstance().getReferenceFromUrl(uri)
                        .getBytes(15L * 1024 * 1024)
                        .await()
                    decodeCloudImage(bytes)
                } else {
                    ReceiptOcrAnalyzer.loadBitmap(context, parsedUri)
                }
                LocalImageState.Loaded(bitmap)
            } catch (_: IOException) {
                LocalImageState.Failed
            } catch (_: SecurityException) {
                LocalImageState.Failed
            } catch (_: IllegalArgumentException) {
                LocalImageState.Failed
            } catch (_: IllegalStateException) {
                LocalImageState.Failed
            } catch (_: FirebaseException) {
                LocalImageState.Failed
            }
        }
    }

    when (val state = imageState) {
        LocalImageState.Loading -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is LocalImageState.Loaded -> Image(
            bitmap = state.bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
        LocalImageState.Failed -> Box(modifier, contentAlignment = Alignment.Center) {
            Text("Không thể tải ảnh")
        }
    }
}

private fun decodeCloudImage(bytes: ByteArray): Bitmap {
    val maxSide = 1920
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(bytes)) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > maxSide) {
                val scale = longest.toFloat() / maxSide
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
    var sampleSize = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > maxSide) {
        sampleSize *= 2
    }
    return requireNotNull(
        BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        )
    ) { "Không giải mã được ảnh cloud." }
}
