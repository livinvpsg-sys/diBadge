package com.fabxdi.dibadge.ui.home.home_entry.chat

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * In-app PDF viewer (no third party app). Pages are rendered lazily with PdfRenderer.
 */
@Composable
fun PdfViewerScreen(
    fileUri: Uri,
    fileName: String,
    isDark: Boolean,
    onClose: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    BackHandler(onBack = onClose)

    val pageBgColor = if (isDark) Color(0xFF0E1114) else Color(0xFFE8ECEF)
    val cardColor = if (isDark) Color(0xFF181C21) else Color(0xFFF5F7F9)
    val primaryTextColor = if (isDark) Color(0xFFF0F3F5) else Color(0xFF11161A)
    val secondaryTextColor = if (isDark) Color(0xFF8A9199) else Color(0xFF65707A)
    val errorColor = if (isDark) Color(0xFFE8837A) else Color(0xFFC4433A)

    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val renderLock = remember { Mutex() }
    val listState = rememberLazyListState()

    DisposableEffect(fileUri) {
        var pfd: ParcelFileDescriptor? = null
        var r: PdfRenderer? = null
        try {
            pfd = context.contentResolver.openFileDescriptor(fileUri, "r")
            r = PdfRenderer(pfd!!)
            renderer = r
            pageCount = r.pageCount
        } catch (e: Exception) {
            failed = true
            try { pfd?.close() } catch (_: Exception) {}
        }
        onDispose {
            renderer = null
            try { r?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = pageBgColor) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(cardColor)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = primaryTextColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(
                        text = fileName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (pageCount > 0) {
                        Text(
                            text = "Page ${listState.firstVisibleItemIndex + 1} of $pageCount",
                            fontSize = 12.sp,
                            color = secondaryTextColor
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = errorColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                val r = renderer
                if (failed) {
                    Text(
                        text = "This PDF can't be shown",
                        color = secondaryTextColor,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (r != null && widthPx > 0) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(pageCount) { index ->
                            PdfPageImage(renderer = r, index = index, widthPx = widthPx, lock = renderLock)
                        }
                    }
                } else {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}

@Composable
private fun PdfPageImage(renderer: PdfRenderer, index: Int, widthPx: Int, lock: Mutex) {
    var bmp by remember(index, widthPx) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(index, widthPx) {
        bmp = withContext(Dispatchers.IO) {
            lock.withLock {
                try {
                    val page = renderer.openPage(index)
                    try {
                        val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                        val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(android.graphics.Color.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    } finally {
                        page.close()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = "Page ${index + 1}",
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(b.width.toFloat() / b.height.toFloat())
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.707f)
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
        }
    }
}
