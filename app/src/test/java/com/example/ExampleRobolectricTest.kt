package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Cine Arena", appName)
  }

  @Test
  fun `test stream url parser for live php`() {
    val parsed = com.example.player.StreamUrlParser.parse("https://hey-lookme.shop/live.php?id=279")
    assertEquals("hls", parsed.streamType)
    assertEquals("https://hey-lookme.shop/live.php?id=279", parsed.cleanUrl)
  }

  @Test
  fun `test stream url parser for xtream ts`() {
    val parsed = com.example.player.StreamUrlParser.parse("http://premiumtvs.space/YqXTywueEV/damp2purchase/612020")
    assertEquals("ts", parsed.streamType)
  }

  @Test
  fun `test app update info creation`() {
    val update = com.example.update.AppUpdateInfo(
      latestVersionCode = 2,
      latestVersionName = "1.0.1",
      downloadUrl = "https://cinexcricket.com/download/cinearena-v1.0.1.apk",
      releaseNotes = "Added new channels and fixed playback.",
      forceUpdate = false
    )
    assertEquals(2, update.latestVersionCode)
    assertEquals("1.0.1", update.latestVersionName)
    org.junit.Assert.assertFalse(update.forceUpdate)
  }
}
