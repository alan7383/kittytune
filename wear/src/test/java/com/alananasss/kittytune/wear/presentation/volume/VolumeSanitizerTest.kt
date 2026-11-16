package com.alananasss.kittytune.wear.presentation.volume

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive unit tests for [VolumeSanitizer] and [VolumeControlsDefaults].
 * Validates IEEE 754 float edge cases (NaN, Infinities, negative zero), boundary clamping,
 * discrete step snapping, percentage formatting, conversions for Phone and Desktop protocols,
 * and Wear OS 48dp minimum interactive touch target compliance.
 */
class VolumeSanitizerTest {

    // =========================================================================
    // 1. Clamping & Sanitization
    // =========================================================================

    @Test
    fun `sanitize clamps negative values to 0_0f`() {
        assertEquals(0.0f, VolumeSanitizer.sanitize(-0.01f), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(-100.0f), 0.0f)
    }

    @Test
    fun `sanitize clamps values greater than 1_0f to 1_0f`() {
        assertEquals(1.0f, VolumeSanitizer.sanitize(1.01f), 0.0f)
        assertEquals(1.0f, VolumeSanitizer.sanitize(999.0f), 0.0f)
    }

    @Test
    fun `sanitize handles valid ranges accurately`() {
        assertEquals(0.0f, VolumeSanitizer.sanitize(0.0f), 0.0f)
        assertEquals(0.5f, VolumeSanitizer.sanitize(0.5f), 0.0f)
        assertEquals(1.0f, VolumeSanitizer.sanitize(1.0f), 0.0f)
    }

    @Test
    fun `sanitize protects against NaN and Infinities`() {
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.NaN), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.POSITIVE_INFINITY), 0.0f)
        assertEquals(0.0f, VolumeSanitizer.sanitize(Float.NEGATIVE_INFINITY), 0.0f)
    }

    // =========================================================================
    // 2. Increments & Decrements
    // =========================================================================

    @Test
    fun `increment increases volume by step with snapping`() {
        assertEquals(0.55f, VolumeSanitizer.increment(0.50f, 0.05f), 0.001f)
        assertEquals(0.05f, VolumeSanitizer.increment(0.0f, 0.05f), 0.001f)
    }

    @Test
    fun `increment clamps at 1_0f without exceeding bound`() {
        assertEquals(1.0f, VolumeSanitizer.increment(0.98f, 0.05f), 0.001f)
        assertEquals(1.0f, VolumeSanitizer.increment(1.0f, 0.05f), 0.001f)
    }

    @Test
    fun `increment with NaN or infinite step safely preserves volume without crashing`() {
        assertEquals(0.50f, VolumeSanitizer.increment(0.50f, Float.NaN), 0.001f)
        assertEquals(0.50f, VolumeSanitizer.increment(0.50f, Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(0.50f, VolumeSanitizer.increment(0.50f, Float.NEGATIVE_INFINITY), 0.001f)
        assertEquals(0.50f, VolumeSanitizer.increment(0.50f, 0.0f), 0.001f)
        assertEquals(0.50f, VolumeSanitizer.increment(0.50f, -0.05f), 0.001f)
        assertEquals(0.0f, VolumeSanitizer.increment(Float.NaN, Float.NaN), 0.0f)
    }

    @Test
    fun `decrement decreases volume by step with snapping`() {
        assertEquals(0.45f, VolumeSanitizer.decrement(0.50f, 0.05f), 0.001f)
        assertEquals(0.95f, VolumeSanitizer.decrement(1.0f, 0.05f), 0.001f)
    }

    @Test
    fun `decrement clamps at 0_0f without dipping negative`() {
        assertEquals(0.0f, VolumeSanitizer.decrement(0.02f, 0.05f), 0.001f)
        assertEquals(0.0f, VolumeSanitizer.decrement(0.0f, 0.05f), 0.001f)
    }

    @Test
    fun `decrement with NaN or infinite step safely preserves volume without crashing`() {
        assertEquals(0.50f, VolumeSanitizer.decrement(0.50f, Float.NaN), 0.001f)
        assertEquals(0.50f, VolumeSanitizer.decrement(0.50f, Float.POSITIVE_INFINITY), 0.001f)
        assertEquals(0.50f, VolumeSanitizer.decrement(0.50f, Float.NEGATIVE_INFINITY), 0.001f)
        assertEquals(0.50f, VolumeSanitizer.decrement(0.50f, 0.0f), 0.001f)
        assertEquals(0.50f, VolumeSanitizer.decrement(0.50f, -0.05f), 0.001f)
        assertEquals(0.0f, VolumeSanitizer.decrement(Float.NaN, Float.NaN), 0.0f)
    }

    // =========================================================================
    // 3. Step Snapping & IEEE 754 NaN / Infinity Protection
    // =========================================================================

    @Test
    fun `snapToStep eliminates floating-point representation drift`() {
        assertEquals(0.15f, VolumeSanitizer.snapToStep(0.15000001f, 0.05f), 0.0001f)
        assertEquals(0.70f, VolumeSanitizer.snapToStep(0.70000005f, 0.05f), 0.0001f)
        assertEquals(0.0f, VolumeSanitizer.snapToStep(0.01f, 0.05f), 0.0001f)
        assertEquals(0.05f, VolumeSanitizer.snapToStep(0.04f, 0.05f), 0.0001f)
    }

    @Test
    fun `snapToStep with invalid zero or negative step returns sanitized volume`() {
        assertEquals(0.6f, VolumeSanitizer.snapToStep(0.6f, 0.0f), 0.0001f)
        assertEquals(0.6f, VolumeSanitizer.snapToStep(0.6f, -0.05f), 0.0001f)
    }

    @Test
    fun `snapToStep safely handles Float NaN and infinite steps without throwing IllegalArgumentException`() {
        // Critical Gate Iteration 1 fix verification:
        // In IEEE 754, NaN <= 0.0f evaluates to false; our hardened implementation must safely return clamped volume
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, Float.NaN), 0.0001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(0.5f, VolumeSanitizer.snapToStep(0.5f, Float.NEGATIVE_INFINITY), 0.0001f)
        assertEquals(0.0f, VolumeSanitizer.snapToStep(Float.NaN, Float.NaN), 0.0001f)
        assertEquals(0.0f, VolumeSanitizer.snapToStep(Float.NaN, 0.05f), 0.0001f)
        assertEquals(1.0f, VolumeSanitizer.snapToStep(1.5f, Float.NaN), 0.0001f)
        assertEquals(0.0f, VolumeSanitizer.snapToStep(-0.5f, Float.NaN), 0.0001f)
    }

    // =========================================================================
    // 4. Percentage Calculations & Formatting
    // =========================================================================

    @Test
    fun `toPercentage returns integer 0 to 100`() {
        assertEquals(0, VolumeSanitizer.toPercentage(0.0f))
        assertEquals(5, VolumeSanitizer.toPercentage(0.05f))
        assertEquals(50, VolumeSanitizer.toPercentage(0.5f))
        assertEquals(100, VolumeSanitizer.toPercentage(1.0f))
        assertEquals(100, VolumeSanitizer.toPercentage(1.5f)) // Clamped
        assertEquals(0, VolumeSanitizer.toPercentage(Float.NaN)) // Sanitized
    }

    @Test
    fun `formatPercentage formats human readable string`() {
        assertEquals("0%", VolumeSanitizer.formatPercentage(0.0f))
        assertEquals("45%", VolumeSanitizer.formatPercentage(0.45f))
        assertEquals("100%", VolumeSanitizer.formatPercentage(1.0f))
        assertEquals("0%", VolumeSanitizer.formatPercentage(Float.NaN))
    }

    // =========================================================================
    // 5. Phone AudioManager Stream Step Conversions
    // =========================================================================

    @Test
    fun `toStreamStep maps normalized float to discrete integer index`() {
        assertEquals(0, VolumeSanitizer.toStreamStep(0.0f, 15))
        assertEquals(8, VolumeSanitizer.toStreamStep(0.5f, 15))
        assertEquals(15, VolumeSanitizer.toStreamStep(1.0f, 15))
        assertEquals(15, VolumeSanitizer.toStreamStep(1.2f, 15))
        assertEquals(0, VolumeSanitizer.toStreamStep(0.5f, 0)) // Zero steps guard
    }

    @Test
    fun `fromStreamStep converts discrete step index to normalized float`() {
        assertEquals(0.0f, VolumeSanitizer.fromStreamStep(0, 15), 0.001f)
        assertEquals(1.0f, VolumeSanitizer.fromStreamStep(15, 15), 0.001f)
        assertEquals(0.6f, VolumeSanitizer.fromStreamStep(9, 15), 0.001f)
        assertEquals(0.0f, VolumeSanitizer.fromStreamStep(5, 0), 0.001f) // Zero steps guard
    }

    // =========================================================================
    // 6. Desktop Connect Integer (0..1000) Conversions
    // =========================================================================

    @Test
    fun `toDesktopVolume maps normalized float to 0 to 1000 range`() {
        assertEquals(0L, VolumeSanitizer.toDesktopVolume(0.0f))
        assertEquals(500L, VolumeSanitizer.toDesktopVolume(0.5f))
        assertEquals(720L, VolumeSanitizer.toDesktopVolume(0.72f))
        assertEquals(1000L, VolumeSanitizer.toDesktopVolume(1.0f))
        assertEquals(1000L, VolumeSanitizer.toDesktopVolume(1.5f))
        assertEquals(0L, VolumeSanitizer.toDesktopVolume(Float.NaN))
    }

    @Test
    fun `fromDesktopVolume maps 0 to 1000 range to normalized float`() {
        assertEquals(0.0f, VolumeSanitizer.fromDesktopVolume(0L), 0.001f)
        assertEquals(0.5f, VolumeSanitizer.fromDesktopVolume(500L), 0.001f)
        assertEquals(0.72f, VolumeSanitizer.fromDesktopVolume(720L), 0.001f)
        assertEquals(1.0f, VolumeSanitizer.fromDesktopVolume(1000L), 0.001f)
        assertEquals(1.0f, VolumeSanitizer.fromDesktopVolume(2000L), 0.001f) // Clamped
    }

    // =========================================================================
    // 7. Touch Target & Accessibility Compliance (WCAG 2.5.5 / Wear OS HIG)
    // =========================================================================

    @Test
    fun `volume button dimensions strictly satisfy minimum 48x48dp touch target standard`() {
        val minGuidelineDp = 48.0.dp
        val minTargetAreaDp2 = 2304.0 // 48dp * 48dp

        // Assert VolumeControlsDefaults
        assertTrue(
            "Volume minimum touch target (${VolumeControlsDefaults.MinTouchTargetSize}) must be >= 48dp",
            VolumeControlsDefaults.MinTouchTargetSize >= minGuidelineDp
        )
        assertEquals(
            "Volume visual button size (${VolumeControlsDefaults.ButtonSize}) matches 32dp for circular bezel fit",
            32.0.dp,
            VolumeControlsDefaults.ButtonSize
        )

        val touchArea = VolumeControlsDefaults.MinTouchTargetSize.value * VolumeControlsDefaults.MinTouchTargetSize.value
        assertTrue(
            "Touch target interactive hit area ($touchArea dp^2) must be >= $minTargetAreaDp2 dp^2",
            touchArea >= minTargetAreaDp2
        )
    }

    @Test
    fun `volume controls row geometry maintains circular chord clearance and balanced layout`() {
        // Authoritative row width from VolumeControlsDefaults (112dp <= 112dp circular chord limit)
        val totalRowWidth = VolumeControlsDefaults.RowWidth.value
        val spacing = VolumeControlsDefaults.RowSpacing.value

        // Total width must be <= 112dp to prevent circular screen clipping
        assertTrue("Volume row total width ($totalRowWidth dp) must be <= 112dp", totalRowWidth <= 112.0)

        // Spacing must be >= 4dp to prevent accidental dual-tap triggers
        assertTrue("Inter-element spacing ($spacing dp) must be >= 4dp", spacing >= 4.0)
    }
}
