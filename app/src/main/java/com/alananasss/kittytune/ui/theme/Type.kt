package com.alananasss.kittytune.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.LyricsFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

val Typography = Typography()

@OptIn(ExperimentalTextApi::class)
fun getDynamicTypography(
    useCustomFont: Boolean,
    wght: Int, wdth: Float, slnt: Float, rond: Float, grad: Float, opsz: Float
): Typography {
    if (!useCustomFont) return Typography

    val customFamily = FontFamily(
        listOf(
            androidx.compose.ui.text.font.FontWeight.W100,
            androidx.compose.ui.text.font.FontWeight.W200,
            androidx.compose.ui.text.font.FontWeight.W300,
            androidx.compose.ui.text.font.FontWeight.W400,
            androidx.compose.ui.text.font.FontWeight.W500,
            androidx.compose.ui.text.font.FontWeight.W600,
            androidx.compose.ui.text.font.FontWeight.W700,
            androidx.compose.ui.text.font.FontWeight.W800,
            androidx.compose.ui.text.font.FontWeight.W900
        ).map { fw ->
            val adjustedWeight = (wght + (fw.weight - 400)).coerceIn(100, 1000)
            Font(
                resId = R.font.google_sans_flex,
                weight = fw,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(adjustedWeight),
                    FontVariation.width(wdth),
                    FontVariation.slant(slnt),
                    FontVariation.Setting("ROND", rond),
                    FontVariation.Setting("GRAD", grad),
                    FontVariation.Setting("opsz", opsz)
                )
            )
        }
    )

    val customFamilyRounded = FontFamily(
        listOf(
            androidx.compose.ui.text.font.FontWeight.W100,
            androidx.compose.ui.text.font.FontWeight.W200,
            androidx.compose.ui.text.font.FontWeight.W300,
            androidx.compose.ui.text.font.FontWeight.W400,
            androidx.compose.ui.text.font.FontWeight.W500,
            androidx.compose.ui.text.font.FontWeight.W600,
            androidx.compose.ui.text.font.FontWeight.W700,
            androidx.compose.ui.text.font.FontWeight.W800,
            androidx.compose.ui.text.font.FontWeight.W900
        ).map { fw ->
            val adjustedWeight = (wght + (fw.weight - 400)).coerceIn(100, 1000)
            Font(
                resId = R.font.google_sans_flex,
                weight = fw,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(adjustedWeight),
                    FontVariation.width(wdth),
                    FontVariation.slant(slnt),
                    FontVariation.Setting("ROND", 100f),
                    FontVariation.Setting("GRAD", grad),
                    FontVariation.Setting("opsz", opsz)
                )
            )
        }
    )

    return Typography(
        displayLarge = Typography.displayLarge.copy(fontFamily = customFamilyRounded),
        displayMedium = Typography.displayMedium.copy(fontFamily = customFamilyRounded),
        displaySmall = Typography.displaySmall.copy(fontFamily = customFamilyRounded),
        headlineLarge = Typography.headlineLarge.copy(fontFamily = customFamilyRounded),
        headlineMedium = Typography.headlineMedium.copy(fontFamily = customFamilyRounded),
        headlineSmall = Typography.headlineSmall.copy(fontFamily = customFamilyRounded),
        titleLarge = Typography.titleLarge.copy(fontFamily = customFamilyRounded),
        titleMedium = Typography.titleMedium.copy(fontFamily = customFamilyRounded),
        titleSmall = Typography.titleSmall.copy(fontFamily = customFamilyRounded),
        bodyLarge = Typography.bodyLarge.copy(fontFamily = customFamily),
        bodyMedium = Typography.bodyMedium.copy(fontFamily = customFamily),
        bodySmall = Typography.bodySmall.copy(fontFamily = customFamily),
        labelLarge = Typography.labelLarge.copy(fontFamily = customFamily),
        labelMedium = Typography.labelMedium.copy(fontFamily = customFamily),
        labelSmall = Typography.labelSmall.copy(fontFamily = customFamily)
    )
}

val LyricsFontFamily = FontFamily(
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W100),
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W200),
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W300),
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W400),
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W500),
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W600),
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W700),
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W800),
    Font(R.font.sfprodisplaybold, androidx.compose.ui.text.font.FontWeight.W900)
)

@OptIn(ExperimentalTextApi::class)
val GoogleSansRounded = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = androidx.compose.ui.text.font.FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = androidx.compose.ui.text.font.FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(600),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = androidx.compose.ui.text.font.FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

private val montserrat = androidx.compose.ui.text.googlefonts.GoogleFont("Montserrat")
private val googleFontProvider = androidx.compose.ui.text.googlefonts.GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val MontserratFamily = FontFamily(
    androidx.compose.ui.text.googlefonts.Font(googleFont = montserrat, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.Black),
    androidx.compose.ui.text.googlefonts.Font(googleFont = montserrat, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
    androidx.compose.ui.text.googlefonts.Font(googleFont = montserrat, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.Bold),
    androidx.compose.ui.text.googlefonts.Font(googleFont = montserrat, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.SemiBold),
    androidx.compose.ui.text.googlefonts.Font(googleFont = montserrat, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.Medium),
    androidx.compose.ui.text.googlefonts.Font(googleFont = montserrat, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.Normal),
    androidx.compose.ui.text.googlefonts.Font(googleFont = montserrat, fontProvider = googleFontProvider, weight = androidx.compose.ui.text.font.FontWeight.Light)
)

val ExpTitleTypography = Typography(
    displayLarge = androidx.compose.ui.text.TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        fontSize = 60.sp,
        textGeometricTransform = androidx.compose.ui.text.style.TextGeometricTransform(scaleX = 1.5f),
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
    ),
    displayMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSize = 50.sp,
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
    ),
    titleMedium = androidx.compose.ui.text.TextStyle(
        fontFamily = MontserratFamily,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        fontSize = 32.sp,
        textGeometricTransform = androidx.compose.ui.text.style.TextGeometricTransform(scaleX = 1.3f),
        letterSpacing = (-0.02).em,
        lineHeight = 0.95.em,
        platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false)
    )
)

val LocalLyricsFontFamily = compositionLocalOf { LyricsFontFamily }

@Composable
fun rememberLyricsFontFamily(lyricsFont: LyricsFont): FontFamily {
    val appFontFamily = MaterialTheme.typography.headlineMedium.fontFamily ?: FontFamily.Default
    return remember(lyricsFont, appFontFamily) {
        when (lyricsFont) {
            LyricsFont.APPLE -> LyricsFontFamily
            LyricsFont.APP_DEFAULT -> appFontFamily
        }
    }
}

