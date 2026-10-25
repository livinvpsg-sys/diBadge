package com.fabxdi.dibadge.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import androidx.core.content.FileProvider
import com.fabxdi.dibadge.data.FormQuestionData
import com.fabxdi.dibadge.data.FormSigner
import com.fabxdi.dibadge.data.LeaveEntity
import com.fabxdi.dibadge.data.OvertimeEntity
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object PdfSharingUtils {

    fun shareMyActivityAsPdf(context: Context, logs: List<Any>, startDate: LocalDate, endDate: LocalDate) {
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

        canvas.drawText("My Activity Report", margin, yPos, titlePaint)
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
                    is OvertimeEntity -> "Extra hours ${log.date.format(dateFormatter)} (${log.status})"
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

        val fileName = "MyActivity_${startDate.format(fileDateFormatter)}_${endDate.format(fileDateFormatter)}.pdf"
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
        context.startActivity(Intent.createChooser(shareIntent, "Share My Activity PDF"))
    }

    fun exportFormSubmissionPdf(
        context: Context,
        formTitle: String,
        sublineInfo: String,
        questions: List<FormQuestionData>,
        answersMap: Map<String, String>,
        workers: List<FormSigner> = emptyList(),
        supervisor: FormSigner? = null
    ): File? {
        val pdfDocument = PdfDocument()
        val titlePaint = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
            color = Color.BLACK
        }
        val sublinePaint = Paint().apply {
            textSize = 13f
            color = Color.DKGRAY
        }
        val labelPaint = Paint().apply {
            textSize = 14f
            isFakeBoldText = true
            color = Color.DKGRAY
        }
        val answerPaint = Paint().apply {
            textSize = 15f
            color = Color.BLACK
        }

        val pageWidth = 595
        val pageHeight = 842
        var pageNumber = 1
        var myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var myPage = pdfDocument.startPage(myPageInfo)
        var canvas = myPage.canvas

        var yPos = 50f
        val margin = 40f
        val lineSpacing = 22f

        canvas.drawText(formTitle, margin, yPos, titlePaint)
        yPos += 28f

        canvas.drawText(sublineInfo, margin, yPos, sublinePaint)
        yPos += 35f

        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, linePaint)
        yPos += 25f

        questions.filter { it.type != "signatures" }.forEachIndexed { idx, q ->
            if (yPos > pageHeight - 80) {
                pdfDocument.finishPage(myPage)
                pageNumber++
                myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                myPage = pdfDocument.startPage(myPageInfo)
                canvas = myPage.canvas
                yPos = 50f
            }

            val questionLabel = "${idx + 1}. ${q.label.ifBlank { q.type }}"
            canvas.drawText(questionLabel, margin, yPos, labelPaint)
            yPos += lineSpacing

            val answerText = answersMap[q.id]?.ifBlank { "N/A" } ?: "N/A"
            canvas.drawText(answerText, margin + 15f, yPos, answerPaint)
            yPos += lineSpacing + 12f
        }

        if (workers.isNotEmpty() || supervisor != null) {
            if (yPos > pageHeight - 120) {
                pdfDocument.finishPage(myPage)
                pageNumber++
                myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                myPage = pdfDocument.startPage(myPageInfo)
                canvas = myPage.canvas
                yPos = 50f
            }

            canvas.drawLine(margin, yPos, pageWidth - margin, yPos, linePaint)
            yPos += 20f

            if (workers.isNotEmpty()) {
                canvas.drawText("Workers Signatures", margin, yPos, labelPaint)
                yPos += lineSpacing
                workers.forEach { w ->
                    val stampStr = if (w.stamp != null) {
                        "${w.stamp.fullName} (${w.stamp.badgeNumber}) - Signed ${w.stamp.tzOffset}"
                    } else "${w.name} - Pending"
                    canvas.drawText("• $stampStr", margin + 15f, yPos, answerPaint)
                    yPos += lineSpacing
                }
                yPos += 10f
            }

            if (supervisor != null) {
                canvas.drawText("Supervisor Signature", margin, yPos, labelPaint)
                yPos += lineSpacing
                val stampStr = if (supervisor.stamp != null) {
                    "${supervisor.stamp.fullName} (${supervisor.stamp.badgeNumber}) - Signed ${supervisor.stamp.tzOffset}"
                } else "${supervisor.name} - Pending"
                canvas.drawText("• $stampStr", margin + 15f, yPos, answerPaint)
                yPos += lineSpacing
            }
        }

        pdfDocument.finishPage(myPage)

        val safeTitle = formTitle.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        val fileName = "$safeTitle.pdf"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        return try {
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            pdfDocument.writeTo(FileOutputStream(file))
            pdfDocument.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            pdfDocument.close()
            null
        }
    }

    fun openPdfFile(context: Context, file: File) {
        try {
            val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
