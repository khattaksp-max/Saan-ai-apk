package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.action.PhoneActionHandler
import com.example.audio.WavUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context verifies SANA app name`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SANA", appName)
  }

  @Test
  fun `test PhoneActionHandler extracts actions in English Urdu and Roman Urdu`() {
    val english = PhoneActionHandler.extractAction("SANA, open WhatsApp please")
    assertEquals("OPEN_WHATSAPP", english)

    val romanUrdu = PhoneActionHandler.extractAction("WhatsApp kholo jaldi")
    assertEquals("OPEN_WHATSAPP", romanUrdu)

    val urdu = PhoneActionHandler.extractAction("واٹس ایپ کھولو")
    assertEquals("OPEN_WHATSAPP", urdu)

    val youtube = PhoneActionHandler.extractAction("Open YouTube")
    assertEquals("OPEN_YOUTUBE", youtube)

    val camera = PhoneActionHandler.extractAction("Open camera")
    assertEquals("OPEN_CAMERA", camera)

    val settings = PhoneActionHandler.extractAction("Open Settings")
    assertEquals("OPEN_SETTINGS", settings)
  }

  @Test
  fun `test WavUtils creates valid WAV header`() {
    val dummyPcm = ByteArray(3200) // 100ms at 16kHz 16-bit mono
    val wav = WavUtils.pcmToWav(dummyPcm, sampleRate = 16000)
    assertTrue(WavUtils.isWav(wav))
    assertEquals(44 + 3200, wav.size)
  }
}

