package com.fabxdi.dibadge.ui.my_activity

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fabxdi.dibadge.data.ActivityLogEntity
import com.fabxdi.dibadge.ui.components.DiBadgeDateRangePicker
import com.fabxdi.dibadge.ui.theme.DiBadgeTheme
import com.fabxdi.dibadge.viewmodel.ActivityLogViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyActivityScreen(
    onBack: () -> Unit,
    onOpenRelatedItem: (String) -> Unit = {},
    viewModel: ActivityLogViewModel = viewModel()
) {
    val context = LocalContext.current
    val allLogs by viewModel.allActivityLogs.collectAsState(initial = emptyList())

    BackHandler(enabled = true, onBack = onBack)

    var searchQuery by remember { mutableStateOf("") }
    var selectedMonthYear by remember { mutableStateOf<YearMonth?>(YearMonth.now()) }
    var isCustomFilter by remember { mutableStateOf(false) }

    var customStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var customEndDate by remember { mutableStateOf<LocalDate?>(null) }

    var showDateRangePicker by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val colors = MaterialTheme.colorScheme
    val diBadgeColors = DiBadgeTheme.colors

    val pageBgColor = colors.background
    val cardBgColor = colors.surface
    val outlineColor = colors.outline
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val tabInactiveColor = diBadgeColors.tabInactive
    val tealAccent = diBadgeColors.accent
    val errorColor = colors.error

    val isDark = primaryTextColor.red > 0.5f
    val dateBandBgColor = if (isDark) Color(0xFF14181D) else Color(0xFFDDE2E7)

    val today = remember { LocalDate.now() }

    // Generate Month Chips (oldest to newest, last 4 months + current month)
    val monthChipsList = remember(allLogs) {
        val currentYM = YearMonth.now()
        val set = mutableSetOf<YearMonth>()
        for (i in 3 downTo 0) {
            set.add(currentYM.minusMonths(i.toLong()))
        }
        allLogs.forEach { log ->
            val logDate = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            set.add(YearMonth.from(logDate))
        }
        set.sorted() // oldest to newest
    }

    val chipListState = rememberLazyListState()
    LaunchedEffect(monthChipsList) {
        if (monthChipsList.isNotEmpty()) {
            chipListState.scrollToItem(monthChipsList.lastIndex)
        }
    }

    // Filter Logs
    val filteredLogs = remember(allLogs, selectedMonthYear, isCustomFilter, customStartDate, customEndDate, searchQuery) {
        var list = allLogs
        if (isCustomFilter && customStartDate != null && customEndDate != null) {
            list = list.filter { log ->
                val date = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                !date.isBefore(customStartDate) && !date.isAfter(customEndDate)
            }
        } else if (selectedMonthYear != null) {
            list = list.filter { log ->
                val date = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                YearMonth.from(date) == selectedMonthYear
            }
        }

        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            list = list.filter { log ->
                log.title.lowercase().contains(q) ||
                        log.source.lowercase().contains(q) ||
                        log.reference.lowercase().contains(q) ||
                        log.result.lowercase().contains(q)
            }
        }

        list.sortedByDescending { it.timestamp }
    }

    // Group logs by date
    val groupedLogs = remember(filteredLogs) {
        filteredLogs.groupBy { log ->
            Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        }
    }

    // Export PDF Title and Period Subtitle
    val exportPeriodText = remember(selectedMonthYear, isCustomFilter, customStartDate, customEndDate) {
        val dFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val mFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

        if (isCustomFilter && customStartDate != null && customEndDate != null) {
            "${customStartDate!!.format(dFormatter)} to ${customEndDate!!.format(dFormatter)}"
        } else if (selectedMonthYear != null) {
            selectedMonthYear!!.format(mFormatter)
        } else {
            "All Time"
        }
    }

    val exportFileName = remember(selectedMonthYear, isCustomFilter, customStartDate, customEndDate) {
        val dFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
        val mFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

        if (isCustomFilter && customStartDate != null && customEndDate != null) {
            "My Activity ${customStartDate!!.format(dFormatter)} to ${customEndDate!!.format(dFormatter)}.pdf"
        } else if (selectedMonthYear != null) {
            "My Activity ${selectedMonthYear!!.format(mFormatter)}.pdf"
        } else {
            "My Activity Statement.pdf"
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBgColor)
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = primaryTextColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "My Activity",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                }

                // Export Icon (Tray with arrow pointing up / Share)
                IconButton(
                    onClick = { showExportSheet = true },
                    enabled = filteredLogs.isNotEmpty(),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Export statement as PDF",
                        tint = if (filteredLogs.isNotEmpty()) primaryTextColor else tabInactiveColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(pageBgColor)
        ) {
            // Search Field
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(14.dp),
                color = cardBgColor
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = secondaryTextColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search",
                                fontSize = 16.sp,
                                color = tabInactiveColor
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                color = primaryTextColor
                            ),
                            cursorBrush = SolidColor(tealAccent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Month Chips LazyRow
            LazyRow(
                state = chipListState,
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                items(monthChipsList) { ym ->
                    val isSelected = !isCustomFilter && selectedMonthYear == ym
                    val monthLabel = if (ym.year == today.year) {
                        ym.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
                    } else {
                        ym.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()))
                    }

                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .clickable {
                                isCustomFilter = false
                                selectedMonthYear = ym
                            },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) primaryTextColor else outlineColor)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = monthLabel,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) primaryTextColor else secondaryTextColor
                            )
                        }
                    }
                }

                item {
                    val isSelected = isCustomFilter
                    val customLabel = if (isCustomFilter && customStartDate != null && customEndDate != null) {
                        "${customStartDate!!.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))} to ${customEndDate!!.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"
                    } else {
                        "Custom"
                    }

                    Surface(
                        modifier = Modifier
                            .height(34.dp)
                            .clickable {
                                showDateRangePicker = true
                            },
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) primaryTextColor else outlineColor)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = customLabel,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) primaryTextColor else secondaryTextColor
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main List or Empty State
            if (groupedLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = tabInactiveColor,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Your approvals will appear here",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = primaryTextColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Everything you sign, approve or confirm is recorded in this statement",
                            fontSize = 13.sp,
                            color = secondaryTextColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    groupedLogs.forEach { (date, logs) ->
                        val formattedDateBand = date.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()))

                        // Date Band
                        item(key = "date_band_${date}") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .background(dateBandBgColor)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = formattedDateBand,
                                    fontSize = 13.sp,
                                    color = secondaryTextColor
                                )
                            }
                        }

                        // Day's Entries
                        items(logs, key = { it.id }) { log ->
                            ActivityEntryRow(
                                log = log,
                                cardBgColor = cardBgColor,
                                outlineColor = outlineColor,
                                primaryTextColor = primaryTextColor,
                                secondaryTextColor = secondaryTextColor,
                                tealAccent = tealAccent,
                                errorColor = errorColor,
                                onClick = {
                                    if (log.relatedId.isNotBlank()) {
                                        onOpenRelatedItem(log.relatedId)
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("This item is no longer available")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDateRangePicker) {
        DiBadgeDateRangePicker(
            initialStartDate = customStartDate,
            initialEndDate = customEndDate,
            onRangeSelected = { start, end ->
                showDateRangePicker = false
                customStartDate = start
                customEndDate = end
                isCustomFilter = true
            },
            onDismiss = { showDateRangePicker = false }
        )
    }

    if (showExportSheet) {
        ExportActivityPdfSheet(
            periodText = exportPeriodText,
            fileName = exportFileName,
            logs = filteredLogs,
            context = context,
            onDismiss = { showExportSheet = false },
            onSavedToDownloads = {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Saved to Downloads")
                }
            }
        )
    }
}

@Composable
fun ActivityEntryRow(
    log: ActivityLogEntity,
    cardBgColor: Color,
    outlineColor: Color,
    primaryTextColor: Color,
    secondaryTextColor: Color,
    tealAccent: Color,
    errorColor: Color,
    onClick: () -> Unit
) {
    val formattedTime = remember(log.timestamp) {
        Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).toLocalTime().format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    }

    val isRejected = log.result.equals("Rejected", ignoreCase = true)
    val resultColor = if (isRejected) errorColor else tealAccent

    val kindIcon = when (log.kind.lowercase()) {
        "permit" -> Icons.Outlined.Security
        "toolbox" -> Icons.Outlined.Group
        else -> Icons.Outlined.Mail
    }

    val rowAccessibility = "${log.title}, ${log.source}, ${log.reference}, ${log.result}, $formattedTime"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = rowAccessibility
            },
        color = cardBgColor
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 44dp Round Icon
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, outlineColor)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = kindIcon,
                            contentDescription = null,
                            tint = secondaryTextColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Title + Subline
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${log.source} · ${log.reference}",
                        fontSize = 13.sp,
                        color = secondaryTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Result Word + Time
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = log.result,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = resultColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formattedTime,
                        fontSize = 12.sp,
                        color = secondaryTextColor
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 76.dp),
                thickness = 1.dp,
                color = outlineColor
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportActivityPdfSheet(
    periodText: String,
    fileName: String,
    logs: List<ActivityLogEntity>,
    context: Context,
    onDismiss: () -> Unit,
    onSavedToDownloads: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val primaryTextColor = colors.onBackground
    val secondaryTextColor = colors.onSurfaceVariant
    val cardBgColor = colors.surface
    val outlineColor = colors.outline

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = cardBgColor,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = 4.dp),
                    shape = RoundedCornerShape(2.dp),
                    color = secondaryTextColor.copy(alpha = 0.40f)
                ) {}
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = "Export as PDF",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                color = primaryTextColor,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = periodText,
                fontSize = 13.sp,
                color = secondaryTextColor,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Share Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable {
                        onDismiss()
                        val pdfFile = generateActivityStatementPdf(context, logs, periodText, fileName)
                        if (pdfFile != null) {
                            try {
                                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Activity Statement"))
                            } catch (e: Exception) {}
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Share",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                    Text(
                        text = "Send the PDF with another app",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = outlineColor)

            // Download Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable {
                        onDismiss()
                        val pdfFile = generateActivityStatementPdf(context, logs, periodText, fileName)
                        if (pdfFile != null) {
                            try {
                                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                                val destFile = File(downloadsDir, fileName)
                                pdfFile.copyTo(destFile, overwrite = true)
                                onSavedToDownloads()
                            } catch (e: Exception) {}
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    tint = secondaryTextColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Download",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Normal,
                        color = primaryTextColor
                    )
                    Text(
                        text = "Save the PDF on this phone",
                        fontSize = 13.sp,
                        color = secondaryTextColor
                    )
                }
            }
        }
    }
}

fun generateActivityStatementPdf(context: Context, logs: List<ActivityLogEntity>, periodText: String, fileName: String): File? {
    return try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        val paint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
        }

        var y = 50f

        // Title
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Activity statement", 40f, y, paint)
        y += 20f

        // Period & Entries Count
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = android.graphics.Color.GRAY
        canvas.drawText("Period: $periodText · Total Entries: ${logs.size}", 40f, y, paint)
        y += 30f

        // Table Header
        paint.color = android.graphics.Color.BLACK
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Date & Time", 40f, y, paint)
        canvas.drawText("Title", 160f, y, paint)
        canvas.drawText("Source & Reference", 320f, y, paint)
        canvas.drawText("Result", 480f, y, paint)
        y += 16f

        paint.strokeWidth = 1f
        canvas.drawLine(40f, y, 550f, y, paint)
        y += 16f

        // Table Rows
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

        for (log in logs) {
            val dtStr = Instant.ofEpochMilli(log.timestamp).atZone(ZoneId.systemDefault()).format(dateFormatter)
            canvas.drawText(dtStr, 40f, y, paint)
            canvas.drawText(log.title.take(20), 160f, y, paint)
            canvas.drawText("${log.source.take(15)} · ${log.reference}", 320f, y, paint)
            canvas.drawText(log.result, 480f, y, paint)
            y += 20f

            if (y > 780f) break
        }

        // Footer
        paint.color = android.graphics.Color.GRAY
        paint.textSize = 9f
        canvas.drawText("diBadge", 40f, 810f, paint)
        canvas.drawText("Page 1 of 1", 500f, 810f, paint)

        pdfDocument.finishPage(page)

        val tempFile = File(context.cacheDir, fileName)
        val outputStream = FileOutputStream(tempFile)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
        outputStream.close()
        tempFile
    } catch (e: Exception) {
        null
    }
}
