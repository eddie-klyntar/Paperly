package com.example.paperly

import com.example.paperly.theme.ThemeCollection
import com.example.paperly.theme.ThemeJsonCodec
import com.example.paperly.theme.ThemePresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeEngineTest {

  @Test
  fun `presets cover all 40 collections with at least 250 distinct themes`() {
    val all = ThemePresets.allPresets
    assertTrue("Expected >= 250 themes, found ${all.size}", all.size >= 250)
    assertEquals(all.size, all.map { it.id }.distinct().size)
    assertEquals(all.size, all.map { it.name }.distinct().size)

    val coveredCollections = all.map { it.collection }.toSet()
    assertEquals(ThemeCollection.entries.toSet(), coveredCollections)
  }

  @Test
  fun `theme json codec round-trips custom theme accurately`() {
    val sample = ThemePresets.findById("retro_terminal_phosphor_green")!!.copy(
      id = "custom_test_1",
      name = "My Phosphor Desk",
      isCustom = true,
      cornerRadiusDp = 12f
    )
    val encoded = ThemeJsonCodec.encode(sample)
    val decoded = ThemeJsonCodec.decode(encoded)
    assertEquals("My Phosphor Desk", decoded.name)
    assertEquals(sample.collection, decoded.collection)
    assertEquals(sample.primaryBg, decoded.primaryBg)
    assertEquals(sample.headingFont, decoded.headingFont)
    assertEquals(12f, decoded.cornerRadiusDp, 0.01f)
    assertTrue(decoded.isCustom)
  }
}
