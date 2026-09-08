package com.example

import com.example.converter.ConversionOptions
import com.example.converter.EpubFontFamily
import com.example.converter.EpubMargin
import com.example.converter.ImageCompressionLevel
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun conversionOptions_defaultSettings_areAccurate() {
    val options = ConversionOptions()
    assertEquals(EpubFontFamily.SANS_SERIF, options.fontFamily)
    assertEquals(16, options.fontSizePt)
    assertEquals(EpubMargin.STANDARD, options.margin)
    assertEquals(ImageCompressionLevel.BALANCED, options.imageCompression)
    assertNull(options.customMarginPercent)
    assertNull(options.customImageQuality)
    assertFalse(options.isCustomized)
    assertEquals(5, options.effectiveHorizontalMargin)
    assertEquals(4, options.effectiveVerticalMargin)
    assertEquals(80, options.effectiveImageQuality)
  }

  @Test
  fun conversionOptions_customSettings_affectEffectiveValues() {
    val options = ConversionOptions(
      fontFamily = EpubFontFamily.SERIF,
      fontSizePt = 18,
      margin = EpubMargin.COMPACT,
      customMarginPercent = 7,
      imageCompression = ImageCompressionLevel.LOW,
      customImageQuality = 95
    )
    assertTrue(options.isCustomized)
    assertEquals(7, options.effectiveHorizontalMargin)
    assertEquals(6, options.effectiveVerticalMargin)
    assertEquals(95, options.effectiveImageQuality)
  }

  @Test
  fun imageCompressionLevels_haveValidParameters() {
    ImageCompressionLevel.values().forEach { level ->
      assertTrue(level.qualityPercent in 20..100)
      assertTrue(level.maxDimension >= 400)
      assertTrue(level.displayName.isNotBlank())
    }
  }

  @Test
  fun epubFontFamilies_haveValidCss() {
    EpubFontFamily.values().forEach { family ->
      assertTrue(family.fontCss.contains("font-family"))
      assertTrue(family.displayName.isNotBlank())
    }
  }
}
