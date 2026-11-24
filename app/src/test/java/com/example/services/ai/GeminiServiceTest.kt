package com.example.services.ai

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class GeminiServiceTest {

    @Test
    fun testContractUrlContainsGemini25Flash() {
        // Look for the GeminiService.kt file
        val pathsToTry = listOf(
            "src/main/java/com/example/services/ai/GeminiService.kt",
            "app/src/main/java/com/example/services/ai/GeminiService.kt"
        )
        
        var sourceFile: File? = null
        for (path in pathsToTry) {
            val file = File(path)
            if (file.exists()) {
                sourceFile = file
                break
            }
        }
        
        assertNotNull("GeminiService.kt source file not found for contract verification!", sourceFile)
        val content = sourceFile!!.readText()
        
        // Assertions for model usage
        assertTrue("GeminiService.kt should use gemini-2.5-flash model!", content.contains("gemini-2.5-flash"))
        assertFalse("GeminiService.kt should not use gemini-1.5-flash model!", content.contains("gemini-1.5-flash"))
        assertFalse("GeminiService.kt should not use gemini-3.5-flash model!", content.contains("gemini-3.5-flash"))
    }
}
