package com.expensetracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.TransactionEntity
import com.expensetracker.model.TxnType
import com.expensetracker.ui.theme.incomeColor
import com.expensetracker.ui.theme.money
import com.expensetracker.util.Money

/** "+₹500" in green for money in, "−₹250" for money out, struck through when not counted. */
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
        style = style.money(),
        fontWeight = FontWeight.Bold,
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
    showDate: Boolean = false,
) {
    val details = listOf(
        category?.name ?: "Others",
        if (showDate) txn.timestamp.dateLabel() else txn.timestamp.timeLabel(),
    ).joinToString(" · ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(txn.merchant, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            SignedAmount(txn)
            if (txn.excluded) {
                Text("Not counted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
