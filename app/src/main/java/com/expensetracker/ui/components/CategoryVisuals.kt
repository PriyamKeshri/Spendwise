package com.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.CategoryIds

/**
 * Icons and colours for the built-in categories live in code rather than the database, so a
 * palette change reaches existing installs. Custom categories use their stored emoji and colour.
 */
object CategoryVisuals {
    private class Visual(val icon: ImageVector, val color: Long)

    private val builtIn = mapOf(
        CategoryIds.FOOD to Visual(Icons.Rounded.Restaurant, 0xFFFF7A45),
        CategoryIds.GROCERIES to Visual(Icons.Rounded.LocalGroceryStore, 0xFF22C55E),
        CategoryIds.SHOPPING to Visual(Icons.Rounded.ShoppingBag, 0xFFEC4899),
        CategoryIds.TRANSPORT to Visual(Icons.Rounded.LocalTaxi, 0xFF0EA5E9),
        CategoryIds.FUEL to Visual(Icons.Rounded.LocalGasStation, 0xFFF59E0B),
        CategoryIds.BILLS to Visual(Icons.Rounded.Bolt, 0xFF3B82F6),
        CategoryIds.RENT to Visual(Icons.Rounded.Home, 0xFF6366F1),
        CategoryIds.ENTERTAINMENT to Visual(Icons.Rounded.Movie, 0xFFA855F7),
        CategoryIds.HEALTH to Visual(Icons.Rounded.MedicalServices, 0xFFEF4444),
        CategoryIds.TRAVEL to Visual(Icons.Rounded.Flight, 0xFF06B6D4),
        CategoryIds.EDUCATION to Visual(Icons.Rounded.School, 0xFF14B8A6),
        CategoryIds.SUBSCRIPTIONS to Visual(Icons.Rounded.Autorenew, 0xFFD946EF),
        CategoryIds.TRANSFERS to Visual(Icons.Rounded.SwapHoriz, 0xFF64748B),
        CategoryIds.CASH to Visual(Icons.Rounded.LocalAtm, 0xFF84CC16),
        CategoryIds.OTHERS to Visual(Icons.Rounded.Category, 0xFF94A3B8),
        CategoryIds.SALARY to Visual(Icons.Rounded.Work, 0xFF10B981),
        CategoryIds.REFUNDS to Visual(Icons.Rounded.Replay, 0xFF06B6D4),
        CategoryIds.INTEREST to Visual(Icons.Rounded.Savings, 0xFFF59E0B),
        CategoryIds.TRANSFERS_IN to Visual(Icons.Rounded.SouthWest, 0xFF3B82F6),
        CategoryIds.OTHER_INCOME to Visual(Icons.Rounded.Paid, 0xFF94A3B8),
    )

    private const val FALLBACK_COLOR = 0xFF94A3B8

    fun color(category: CategoryEntity?): Color =
        Color(category?.let { builtIn[it.id]?.color ?: it.color.takeIf { color -> color != 0L } } ?: FALLBACK_COLOR)

    fun icon(category: CategoryEntity?): ImageVector? = category?.let { builtIn[it.id]?.icon }
}

/** A rounded tile tinted with the category colour, holding its icon (or emoji for custom ones). */
@Composable
fun CategoryTile(category: CategoryEntity?, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val color = CategoryVisuals.color(category)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        CategoryGlyph(category, size = size * 0.52f, tint = color)
    }
}

/** Just the category's icon (or emoji), for chips and other tight spots. */
@Composable
fun CategoryGlyph(category: CategoryEntity?, size: Dp = 18.dp, tint: Color = CategoryVisuals.color(category)) {
    val icon = CategoryVisuals.icon(category)
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    } else {
        Text(category?.emoji ?: "🏷️", fontSize = (size.value * 0.82f).sp)
    }
}
