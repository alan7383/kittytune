package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.utils.LocaleUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.Locale

class LocaleUtilsTest {

    @Test
    fun testLanguageResolution() {
        assertEquals("vi", LocaleUtils.getLocaleForLanguage(AppLanguage.VIETNAMESE).language)
        assertEquals("fr", LocaleUtils.getLocaleForLanguage(AppLanguage.FRENCH).language)
        assertEquals("en", LocaleUtils.getLocaleForLanguage(AppLanguage.ENGLISH).language)
        assertEquals("hu", LocaleUtils.getLocaleForLanguage(AppLanguage.HUNGARIAN).language)
        assertEquals("ru", LocaleUtils.getLocaleForLanguage(AppLanguage.RUSSIAN).language)
    }

    @Test
    fun testSystemLanguageFallbackDoesNotStickToPreviousSelection() {
        val initialSys = LocaleUtils.getSystemLocale()

        // Simulate choosing Vietnamese
        val viLocale = LocaleUtils.getLocaleForLanguage(AppLanguage.VIETNAMESE)
        assertEquals("vi", viLocale.language)

        // Mutate process default (as applyAppLanguage previously did)
        Locale.setDefault(viLocale)
        assertEquals("vi", Locale.getDefault().language)

        // Now user switches back to SYSTEM:
        // Even though Locale.getDefault() was mutated to "vi",
        // getLocaleForLanguage(AppLanguage.SYSTEM) must return the system locale, NOT "vi" (assuming system is not Vietnamese)
        val systemResolved = LocaleUtils.getLocaleForLanguage(AppLanguage.SYSTEM)
        assertEquals(initialSys.language, systemResolved.language)

        // And if we reset Locale.setDefault to the resolved system locale:
        Locale.setDefault(systemResolved)
        assertEquals(initialSys.language, Locale.getDefault().language)
    }

    @Test
    fun testAcceptLanguageHeaders() {
        assertEquals("vi-VN,vi;q=0.9,en;q=0.8", LocaleUtils.getAcceptLanguageForLanguage(AppLanguage.VIETNAMESE))
        assertEquals("fr-FR,fr;q=0.9,en;q=0.8", LocaleUtils.getAcceptLanguageForLanguage(AppLanguage.FRENCH))
        assertEquals("en-US,en;q=0.9", LocaleUtils.getAcceptLanguageForLanguage(AppLanguage.ENGLISH))
        assertEquals("hu-HU,hu;q=0.9,en;q=0.8", LocaleUtils.getAcceptLanguageForLanguage(AppLanguage.HUNGARIAN))
        assertEquals("ru-RU,ru;q=0.9,en;q=0.8", LocaleUtils.getAcceptLanguageForLanguage(AppLanguage.RUSSIAN))
    }
}
