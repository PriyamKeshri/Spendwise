package com.expensetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.expensetracker.R

/** Plus Jakarta Sans is a variable font, so one file provides every weight. */
@OptIn(ExperimentalTextApi::class)
val Jakarta = FontFamily(
    listOf(400, 500, 600, 700, 800).map { weight ->
        Font(
            resId = R.font.plus_jakarta_sans,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

private val base = Typography()

private fun TextStyle.jakarta(weight: FontWeight, tracking: Float? = null) =
    copy(fontFamily = Jakarta, fontWeight = weight, letterSpacing = tracking?.sp ?: letterSpacing)

val AppTypography = Typography(
    displayLarge = base.displayLarge.jakarta(FontWeight.ExtraBold, -1.5f),
    displayMedium = base.displayMedium.jakarta(FontWeight.ExtraBold, -1f),
    displaySmall = base.displaySmall.jakarta(FontWeight.ExtraBold, -0.8f),
    headlineLarge = base.headlineLarge.jakarta(FontWeight.Bold, -0.5f),
    headlineMedium = base.headlineMedium.jakarta(FontWeight.Bold, -0.4f),
    headlineSmall = base.headlineSmall.jakarta(FontWeight.Bold, -0.3f),
    titleLarge = base.titleLarge.jakarta(FontWeight.Bold, -0.2f),
    titleMedium = base.titleMedium.jakarta(FontWeight.SemiBold, 0f),
    titleSmall = base.titleSmall.jakarta(FontWeight.SemiBold, 0f),
    bodyLarge = base.bodyLarge.jakarta(FontWeight.Normal, 0.1f),
    bodyMedium = base.bodyMedium.jakarta(FontWeight.Normal, 0.1f),
    bodySmall = base.bodySmall.jakarta(FontWeight.Normal, 0.2f),
    labelLarge = base.labelLarge.jakarta(FontWeight.SemiBold, 0.1f),
    labelMedium = base.labelMedium.jakarta(FontWeight.SemiBold, 0.2f),
    labelSmall = base.labelSmall.jakarta(FontWeight.Medium, 0.3f),
)

/** Tabular figures keep amounts aligned and stop numbers jiggling as they change. */
fun TextStyle.money(): TextStyle = copy(fontFeatureSettings = "tnum")
