package com.alananasss.kittytune.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.alananasss.kittytune.R

val Typography = Typography()

@OptIn(ExperimentalTextApi::class)
fun getDynamicTypography(
    useCustomFont: Boolean,
    wght: Int, wdth: Float, slnt: Float, rond: Float, grad: Float, opsz: Float
): Typography {
    if (!useCustomFont) return Typography

    val customFamily = FontFamily(
        Font(
            resId = R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(wght),
                FontVariation.width(wdth),
                FontVariation.slant(slnt),
                FontVariation.Setting("ROND", rond),
                FontVariation.Setting("GRAD", grad),
                FontVariation.Setting("opsz", opsz)
            )
        )
    )

    val customFamilyRounded = FontFamily(
        Font(
            resId = R.font.google_sans_flex,
            variationSettings = FontVariation.Settings(
                FontVariation.weight(wght),
                FontVariation.width(wdth),
                FontVariation.slant(slnt),
                FontVariation.Setting("ROND", 100f),
                FontVariation.Setting("GRAD", grad),
                FontVariation.Setting("opsz", opsz)
            )
        )
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