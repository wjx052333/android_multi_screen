package com.example.multiscreen.player

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFactoryTest {

    @Test
    fun testDecoderErrorMessageRecognition() {
        val message = PlayerFactory.getFriendlyErrorMessage(
            isDecoderInitException = true,
            fallbackMessage = "Unknown error"
        )
        assertTrue(message.contains("硬件解码器") || message.contains("解码"))
    }

    @Test
    fun testGenericErrorMessageFallback() {
        val fallback = "File not found"
        val message = PlayerFactory.getFriendlyErrorMessage(
            isDecoderInitException = false,
            fallbackMessage = fallback
        )
        assertTrue(message.contains(fallback))
    }
}
