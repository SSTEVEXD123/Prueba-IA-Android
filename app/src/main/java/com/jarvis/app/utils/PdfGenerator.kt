package com.jarvis.app.utils

import android.content.Context
import android.util.Log
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.*
import com.itextpdf.layout.properties.TextAlignment
import com.itextpdf.layout.properties.UnitValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * PdfGenerator
 * Genera PDFs desde texto del usuario, conversaciones de chat
 * y cualquier contenido de Jarvis
 */
object PdfGenerator {

    private const val TAG = "PdfGenerator"

    // Colores de marca Jarvis
    private val COLOR_PRIMARY = DeviceRgb(0, 122, 255)    // Azul
    private val COLOR_DARK = DeviceRgb(30, 30, 30)         // Casi negro
    private val COLOR_GRAY = DeviceRgb(100, 100, 100)      // Gris
    private val COLOR_LIGHT = DeviceRgb(240, 240, 245)     // Fondo claro
    private val COLOR_ACCENT = DeviceRgb(88, 86, 214)      // Morado

    /**
     * Generar PDF desde texto libre del usuario
     */
    suspend fun generateFromText(
        title: String,
        content: String,
        outputPath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            JarvisConfig.createDirectories()

            val timestamp = System.currentTimeMillis()
            val fileName = "jarvis_doc_$timestamp.pdf"
            val filePath = outputPath ?: "${JarvisConfig.GENERATED_PDFS_DIR}/$fileName"

            PdfWriter(filePath).use { writer ->
                PdfDocument(writer).use { pdfDoc ->
                    Document(pdfDoc).use { document ->
                        addHeader(document, title)
                        addContent(document, content)
                        addFooter(document)
                    }
                }
            }

            Log.i(TAG, "PDF generado: $filePath")
            Result.success(filePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error generando PDF: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Generar PDF desde una conversación de chat
     */
    suspend fun generateFromConversation(
        conversationTitle: String,
        messages: List<ChatMessage>,
        outputPath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            JarvisConfig.createDirectories()

            val timestamp = System.currentTimeMillis()
            val fileName = "chat_export_$timestamp.pdf"
            val filePath = outputPath ?: "${JarvisConfig.CHATS_EXPORT_DIR}/$fileName"

            PdfWriter(filePath).use { writer ->
                PdfDocument(writer).use { pdfDoc ->
                    Document(pdfDoc).use { document ->
                        // Encabezado
                        addHeader(document, "Conversación: $conversationTitle")

                        // Fecha de exportación
                        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es")).format(Date())
                        document.add(
                            Paragraph("Exportado el: $dateStr")
                                .setFontColor(COLOR_GRAY)
                                .setFontSize(10f)
                                .setMarginBottom(20f)
                        )

                        // Mensajes
                        messages.forEach { msg ->
                            addChatMessage(document, msg)
                        }

                        addFooter(document)
                    }
                }
            }

            Result.success(filePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generar PDF estructurado con secciones
     */
    suspend fun generateStructured(
        title: String,
        sections: List<PdfSection>,
        outputPath: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            JarvisConfig.createDirectories()

            val timestamp = System.currentTimeMillis()
            val filePath = outputPath ?: "${JarvisConfig.GENERATED_PDFS_DIR}/doc_$timestamp.pdf"

            PdfWriter(filePath).use { writer ->
                PdfDocument(writer).use { pdfDoc ->
                    Document(pdfDoc).use { document ->
                        addHeader(document, title)

                        sections.forEach { section ->
                            // Título de sección
                            document.add(
                                Paragraph(section.title)
                                    .setFontColor(COLOR_PRIMARY)
                                    .setFontSize(14f)
                                    .setBold()
                                    .setMarginTop(15f)
                                    .setMarginBottom(8f)
                            )

                            // Contenido de la sección
                            document.add(
                                Paragraph(section.content)
                                    .setFontSize(11f)
                                    .setFontColor(COLOR_DARK)
                                    .setMarginBottom(10f)
                            )

                            // Línea separadora — FIXED: constructor correcto de iText7
                            document.add(
                                com.itextpdf.layout.element.LineSeparator(
                                    com.itextpdf.kernel.pdf.canvas.draw.SolidLine(0.5f)
                                ).setMarginTop(4f).setMarginBottom(4f)
                            )
                        }

                        addFooter(document)
                    }
                }
            }

            Result.success(filePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =====================================================================
    // Elementos de diseño
    // =====================================================================

    private fun addHeader(document: Document, title: String) {
        // Banner de Jarvis
        val headerTable = Table(UnitValue.createPercentArray(floatArrayOf(80f, 20f)))
            .useAllAvailableWidth()
            .setBackgroundColor(COLOR_PRIMARY)
            .setMarginBottom(25f)

        // Título
        val titleCell = Cell().add(
            Paragraph("⚡ JARVIS APP")
                .setFontColor(ColorConstants.WHITE)
                .setFontSize(10f)
                .setBold()
        ).add(
            Paragraph(title)
                .setFontColor(ColorConstants.WHITE)
                .setFontSize(18f)
                .setBold()
        ).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPadding(15f)

        // Fecha
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale("es")).format(Date())
        val dateCell = Cell().add(
            Paragraph(dateStr)
                .setFontColor(ColorConstants.WHITE)
                .setFontSize(11f)
                .setTextAlignment(TextAlignment.RIGHT)
        ).setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)
            .setPaddingTop(20f)
            .setPaddingRight(15f)

        headerTable.addCell(titleCell)
        headerTable.addCell(dateCell)
        document.add(headerTable)
    }

    private fun addContent(document: Document, content: String) {
        // Parsear markdown básico
        val lines = content.lines()
        for (line in lines) {
            when {
                line.startsWith("# ") -> {
                    document.add(
                        Paragraph(line.removePrefix("# "))
                            .setFontSize(20f)
                            .setBold()
                            .setFontColor(COLOR_PRIMARY)
                            .setMarginTop(15f)
                            .setMarginBottom(8f)
                    )
                }
                line.startsWith("## ") -> {
                    document.add(
                        Paragraph(line.removePrefix("## "))
                            .setFontSize(16f)
                            .setBold()
                            .setFontColor(COLOR_ACCENT)
                            .setMarginTop(12f)
                            .setMarginBottom(6f)
                    )
                }
                line.startsWith("### ") -> {
                    document.add(
                        Paragraph(line.removePrefix("### "))
                            .setFontSize(13f)
                            .setBold()
                            .setFontColor(COLOR_DARK)
                            .setMarginTop(8f)
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    val text = line.removePrefix("- ").removePrefix("* ")
                    document.add(
                        Paragraph("• $text")
                            .setFontSize(11f)
                            .setFontColor(COLOR_DARK)
                            .setMarginLeft(20f)
                    )
                }
                line.isBlank() -> {
                    document.add(Paragraph(" ").setFontSize(6f))
                }
                else -> {
                    document.add(
                        Paragraph(line)
                            .setFontSize(11f)
                            .setFontColor(COLOR_DARK)
                    )
                }
            }
        }
    }

    private fun addChatMessage(document: Document, msg: ChatMessage) {
        val isUser = msg.role == "user"
        val roleLabel = if (isUser) "👤 Usuario" else "🤖 Jarvis"
        val bgColor = if (isUser) COLOR_LIGHT else DeviceRgb(230, 240, 255)

        val table = Table(UnitValue.createPercentArray(floatArrayOf(100f)))
            .useAllAvailableWidth()
            .setMarginBottom(10f)
            .setBackgroundColor(bgColor)

        val cell = Cell()
            .add(Paragraph(roleLabel)
                .setFontSize(9f)
                .setBold()
                .setFontColor(if (isUser) COLOR_DARK else COLOR_PRIMARY)
            )
            .add(Paragraph(msg.content)
                .setFontSize(11f)
                .setFontColor(COLOR_DARK)
            )
            .setPadding(10f)
            .setBorder(com.itextpdf.layout.borders.Border.NO_BORDER)

        table.addCell(cell)
        document.add(table)
    }

    private fun addFooter(document: Document) {
        document.add(
            Paragraph("\n\nGenerado por Jarvis App • IA Local • ${SimpleDateFormat("yyyy", Locale.getDefault()).format(Date())}")
                .setFontSize(9f)
                .setFontColor(COLOR_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginTop(30f)
        )
    }
}

data class PdfSection(
    val title: String,
    val content: String
)

data class ChatMessage(
    val role: String,
    val content: String
)
