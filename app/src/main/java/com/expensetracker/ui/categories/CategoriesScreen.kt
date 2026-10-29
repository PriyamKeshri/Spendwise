package com.expensetracker.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.MerchantRuleEntity
import com.expensetracker.model.CategoryKind
import com.expensetracker.ui.components.AppCard
import com.expensetracker.ui.components.CategoryTile
import com.expensetracker.ui.components.PillToggle
import com.expensetracker.ui.components.ScreenTitle

@Composable
fun CategoriesScreen(onBack: () -> Unit, viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val byId = categories.associateBy { it.id }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { ScreenTitle("Categories", onBack = onBack) }
            item { SectionHeader("Spending", "Matched by merchant name. Anything else goes to Others.") }
            item {
                CategoryGroup(categories.filter { it.kind == CategoryKind.EXPENSE }) { editing = it }
            }
            item { SectionHeader("Income", "Matched by sender and message text.") }
            item {
                CategoryGroup(categories.filter { it.kind == CategoryKind.INCOME }) { editing = it }
            }
            if (rules.isNotEmpty()) {
                item { SectionHeader("Merchants you've sorted", "These come before keywords. Remove one to undo it.") }
                item {
                    AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
                        rules.forEachIndexed { index, rule ->
                            if (index > 0) RowDivider()
                            RuleRow(rule, byId[rule.categoryId]) { viewModel.forget(rule) }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { editing = newCategory() },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("New category") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    editing?.let { category ->
        CategoryEditor(
            initial = category,
            onDismiss = { editing = null },
            onSave = {
                viewModel.save(it)
                editing = null
            },
            onDelete = if (category.isSystem || category.id == 0L) {
                null
            } else {
                {
                    viewModel.delete(category)
                    editing = null
                }
            },
        )
    }
}

private fun newCategory() = CategoryEntity(
    name = "",
    emoji = "🏷️",
    color = 0,
    kind = CategoryKind.EXPENSE,
    keywords = emptyList(),
    sortOrder = 0,
)

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 16.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 74.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

@Composable
private fun CategoryGroup(categories: List<CategoryEntity>, onEdit: (CategoryEntity) -> Unit) {
    AppCard(contentPadding = PaddingValues(vertical = 6.dp)) {
        categories.forEachIndexed { index, category ->
            if (index > 0) RowDivider()
            CategoryRow(category) { onEdit(category) }
        }
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, onClick: () -> Unit) {
    val description = when {
        category.id == CategoryIds.OTHERS -> "Anything not matched elsewhere"
        category.id == CategoryIds.OTHER_INCOME -> "Money in not matched elsewhere"
        category.keywords.isEmpty() -> "No keywords yet"
        else -> category.keywords.take(5).joinToString(", ") +
            if (category.keywords.size > 5) " +${category.keywords.size - 5} more" else ""
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(category.name, style = MaterialTheme.typography.titleSmall)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun RuleRow(rule: MerchantRuleEntity, category: CategoryEntity?, onForget: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryTile(category)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(rule.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "Always ${category?.name ?: "a deleted category"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onForget) {
            Icon(Icons.Rounded.Close, contentDescription = "Remove ${rule.label}")
        }
    }
}

@Composable
private fun CategoryEditor(
    initial: CategoryEntity,
    onDismiss: () -> Unit,
    onSave: (CategoryEntity) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val isNew = initial.id == 0L
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji) }
    var kind by remember { mutableStateOf(initial.kind) }
    var keywords by remember { mutableStateOf(initial.keywords.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New category" else "Edit ${initial.name}") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (initial.isSystem) {
                        CategoryTile(initial, size = 52.dp)
                    } else {
                        OutlinedTextField(
                            value = emoji,
                            onValueChange = { emoji = it.take(8) },
                            label = { Text("Emoji") },
                            singleLine = true,
                            modifier = Modifier.width(84.dp),
                        )
                    }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isNew) {
                    PillToggle(
                        options = listOf("Spending", "Income"),
                        selected = if (kind == CategoryKind.EXPENSE) 0 else 1,
                        onSelect = { kind = if (it == 0) CategoryKind.EXPENSE else CategoryKind.INCOME },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Keywords") },
                    supportingText = { Text("Separate with commas, like: chai point, tea stall") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Delete category")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            emoji = emoji.trim().ifEmpty { "🏷️" },
                            kind = kind,
                            keywords = keywords.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
