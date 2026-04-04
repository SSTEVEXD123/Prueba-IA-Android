package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

/**
 * FileManagerTest
 * Tests unitarios para la lógica de FileInfo (sin I/O real)
 */
class FileInfoTest {

    @Test
    fun `formattedSize muestra bytes correctamente`() {
        val fi = makeFileInfo(500L)
        assertEquals("500 B", fi.formattedSize())
    }

    @Test
    fun `formattedSize muestra KB correctamente`() {
        val fi = makeFileInfo(2048L)
        assertTrue(fi.formattedSize().contains("KB"))
    }

    @Test
    fun `formattedSize muestra MB correctamente`() {
        val fi = makeFileInfo(5 * 1024 * 1024L)
        assertTrue(fi.formattedSize().contains("MB"))
    }

    @Test
    fun `formattedSize muestra GB correctamente`() {
        val fi = makeFileInfo(2L * 1024 * 1024 * 1024)
        assertTrue(fi.formattedSize().contains("GB"))
    }

    private fun makeFileInfo(size: Long) = com.jarvis.app.utils.FileInfo(
        name = "test.txt",
        path = "/sdcard/JarvisApp/files/test.txt",
        size = size,
        extension = "txt",
        lastModified = System.currentTimeMillis(),
        lastModifiedStr = "01/01/2026 12:00"
    )
}

/**
 * GenerationParamsTest
 * Tests de los parámetros de generación del LLM
 */
class GenerationParamsTest {

    @Test
    fun `parámetros por defecto son válidos`() {
        val params = com.jarvis.app.ai.llm.GenerationParams()
        assertTrue("maxTokens debe ser > 0", params.maxTokens > 0)
        assertTrue("temperature debe ser >= 0", params.temperature >= 0f)
        assertTrue("topP debe estar entre 0 y 1", params.topP in 0f..1f)
        assertTrue("repeatPenalty debe ser >= 1", params.repeatPenalty >= 1f)
    }

    @Test
    fun `ChatMessage conserva el rol y contenido`() {
        val msg = com.jarvis.app.ai.llm.ChatMessage(
            role = "user",
            content = "Hola Jarvis"
        )
        assertEquals("user", msg.role)
        assertEquals("Hola Jarvis", msg.content)
        assertTrue("timestamp debe ser positivo", msg.timestamp > 0)
    }
}
