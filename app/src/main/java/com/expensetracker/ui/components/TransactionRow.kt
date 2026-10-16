package com.expensetracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.model.TxnType
import com.expensetracker.ui.theme.incomeColor
import com.expensetracker.util.Money

@Composable
fun CategoryAvatar(category: CategoryEntity?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val tint = category?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.2f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(category?.emoji ?: "📦", fontSize = (size.value * 0.45f).sp)
    }
}

/** "+₹500" in green for money in, "−₹250" for money out. */
@Composable
fun SignedAmount(
    txn: TransactionEntity,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val credit = txn.type == TxnType.CREDIT
    Text(
        text = (if (credit) "+" else "−") + Money.format(txn.amountPaise),
        modifier = modifier,
        style = style,
        color = when {
            txn.excluded -> MaterialTheme.colorScheme.onSurfaceVariant
            credit -> incomeColor
            else -> MaterialTheme.colorScheme.onSurface
        },
        textDecoration = if (txn.excluded) TextDecoration.LineThrough else null,
    )
}

@Composable
fun TransactionRow(
    txn: TransactionEntity,
    category: CategoryEntity?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTime: Boolean = true,
) {
    val details = buildList {
        add(category?.name ?: "Others")
        add(txn.mode.label + (txn.accountTail?.let { " ••$it" } ?: ""))
        add(if (showTime) txn.timestamp.timeLabel() else txn.timestamp.dateLabel())
    }.joinToString(" · ")

    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { CategoryAvatar(category) },
        headlineContent = { Text(txn.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                SignedAmount(txn)
                if (txn.excluded) {
                    Text(
                        "Not counted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
