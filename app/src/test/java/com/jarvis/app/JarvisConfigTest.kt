package com.jarvis.app

import org.junit.Assert.*
import org.junit.Test

/**
 * JarvisConfigTest
 * Tests unitarios básicos para verificar configuración de Jarvis.
 * No requieren emulador ni dispositivo (unit tests puros).
 */
class JarvisConfigTest {

    @Test
    fun `SYSTEM_PROMPT no debe estar vacío`() {
        val prompt = com.jarvis.app.utils.JarvisConfig.SYSTEM_PROMPT
        assertTrue("El prompt del sistema no puede estar vacío", prompt.isNotBlank())
    }

    @Test
    fun `SYSTEM_PROMPT debe contener instrucción de español`() {
        val prompt = com.jarvis.app.utils.JarvisConfig.SYSTEM_PROMPT
        assertTrue(
            "El prompt debe indicar responder en español",
            prompt.contains("español", ignoreCase = true)
        )
    }

    @Test
    fun `DEFAULT_MAX_TOKENS debe ser positivo`() {
        assertTrue(com.jarvis.app.utils.JarvisConfig.DEFAULT_MAX_TOKENS > 0)
    }

    @Test
    fun `DEFAULT_TEMPERATURE debe estar en rango válido`() {
        val temp = com.jarvis.app.utils.JarvisConfig.DEFAULT_TEMPERATURE
        assertTrue("Temperature debe estar entre 0 y 2", temp in 0f..2f)
    }

    @Test
    fun `DEFAULT_TOP_P debe estar en rango válido`() {
        val topP = com.jarvis.app.utils.JarvisConfig.DEFAULT_TOP_P
        assertTrue("Top-P debe estar entre 0 y 1", topP in 0f..1f)
    }

    @Test
    fun `detectChatTemplate reconoce phi correctamente`() {
        val template = com.jarvis.app.utils.JarvisConfig.detectChatTemplate("phi3_mini_q4")
        assertEquals(
            com.jarvis.app.utils.JarvisConfig.ChatTemplate.PHI3,
            template
        )
    }

    @Test
    fun `detectChatTemplate reconoce llama correctamente`() {
        val template = com.jarvis.app.utils.JarvisConfig.detectChatTemplate("llama3_2_3b_q4")
        assertEquals(
            com.jarvis.app.utils.JarvisConfig.ChatTemplate.LLAMA3,
            template
        )
    }

    @Test
    fun `buildImagePrompt enriquece el prompt de usuario`() {
        val userPrompt = "un gato en el espacio"
        val enriched = com.jarvis.app.utils.JarvisConfig.buildImagePrompt(userPrompt)
        assertTrue("El prompt enriquecido debe contener el prompt original",
            enriched.contains(userPrompt))
        assertTrue("El prompt enriquecido debe añadir calidad",
            enriched.contains("quality", ignoreCase = true))
    }

    @Test
    fun `SD_NEGATIVE_PROMPT no debe estar vacío`() {
        assertTrue(com.jarvis.app.utils.JarvisConfig.SD_NEGATIVE_PROMPT.isNotBlank())
    }
}
