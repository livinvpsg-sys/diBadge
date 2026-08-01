package com.fabxdi.dibadge.util

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfSharingUtils {

    fun shareLogbookAsPdf(context: Context, logs: List<Any>, startDate: LocalDate, endDate: LocalDate) {
        val pdfDocument = PdfDocument()
        val titlePaint = Paint().apply {
            textSize = 24f
            isFakeBoldText = true
        }
        val headerPaint = Paint().apply {
            textSize = 18f
            isFakeBoldText = true
        }
        val textPaint = Paint().apply {
            textSize = 14f
        }

        val pageWidth = 595 // A4 width in points
        val pageHeight = 842 // A4 height in points
        var pageNumber = 1
        var myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var myPage = pdfDocument.startPage(myPageInfo)
        var canvas = myPage.canvas

        var yPos = 50f
        val margin = 50f
        val lineSpacing = 25f

        val dateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
        val fileDateFormatter = DateTimeFormatter.ofPattern("dd_MMM_yyyy", Locale.getDefault())

        canvas.drawText("Logbook Report", margin, yPos, titlePaint)
        yPos += 30f
        canvas.drawText("${startDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}", margin, yPos, textPaint)
        yPos += 40f

        val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault())

        val groupedLogs = logs.groupBy {
            when (it) {
                is LeaveEntity -> it.appliedDate
                is OvertimeEntity -> it.appliedDate
                else -> LocalDate.MIN
            }
        }

        groupedLogs.forEach { (date, dailyLogs) ->
            if (yPos > pageHeight - 100) {
                pdfDocument.finishPage(myPage)
                pageNumber++
                myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                myPage = pdfDocument.startPage(myPageInfo)
                canvas = myPage.canvas
                yPos = 50f
            }

            canvas.drawText(date.format(dateFormatter), margin, yPos, headerPaint)
            yPos += lineSpacing

            dailyLogs.forEach { log ->
                if (yPos > pageHeight - 50) {
                    pdfDocument.finishPage(myPage)
                    pageNumber++
                    myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    myPage = pdfDocument.startPage(myPageInfo)
                    canvas = myPage.canvas
                    yPos = 50f
                }

                val title = when (log) {
                    is LeaveEntity -> "${log.leaveType} (${log.status})"
                    is OvertimeEntity -> "Overtime ${log.date.format(dateFormatter)} (${log.status})"
                    else -> ""
                }
                val detail = when (log) {
                    is LeaveEntity -> {
                        if (log.endDate != null) "${log.startDate.format(dateFormatter)} - ${log.endDate.format(dateFormatter)}"
                        else log.startDate.format(dateFormatter)
                    }
                    is OvertimeEntity -> "${log.startTime.format(timeFormatter)} - ${log.endTime.format(timeFormatter)}"
                    else -> ""
                }

                canvas.drawText(title, margin + 20f, yPos, textPaint)
                yPos += 18f
                canvas.drawText(detail, margin + 40f, yPos, textPaint)
                yPos += lineSpacing + 5f
            }
            yPos += 10f
        }

        pdfDocument.finishPage(myPage)

        val fileName = "Logbook_${startDate.format(fileDateFormatter)}_${endDate.format(fileDateFormatter)}.pdf"
        val file = File(context.cacheDir, fileName)
        try {
            pdfDocument.writeTo(FileOutputStream(file))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        pdfDocument.close()

        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Logbook PDF"))
    }
}