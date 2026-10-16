package com.expensetracker.ui.categories

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.expensetracker.data.db.CategoryEntity
import com.expensetracker.data.db.CategoryIds
import com.expensetracker.data.db.MerchantRuleEntity
import com.expensetracker.model.CategoryKind
import com.expensetracker.ui.components.CategoryAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel = hiltViewModel()) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    val byId = categories.associateBy { it.id }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Categories") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = newCategory() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New category") },
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 96.dp),
        ) {
            item { SectionHeader("Spending", "Matched against the merchant name. Unmatched spending goes to Others.") }
            items(categories.filter { it.kind == CategoryKind.EXPENSE }, key = { it.id }) { category ->
                CategoryRow(category) { editing = category }
            }
            item { SectionHeader("Income", "Matched against the sender and the message text.") }
            items(categories.filter { it.kind == CategoryKind.INCOME }, key = { it.id }) { category ->
                CategoryRow(category) { editing = category }
            }
            if (rules.isNotEmpty()) {
                item { SectionHeader("Merchants you've sorted", "These win over keywords. Remove one to go back to automatic.") }
                items(rules, key = { "rule-${it.merchantKey}" }) { rule ->
                    RuleRow(rule, byId[rule.categoryId]) { viewModel.forget(rule) }
                }
            }
        }
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
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CategoryRow(category: CategoryEntity, onClick: () -> Unit) {
    val description = when {
        category.id == CategoryIds.OTHERS -> "Anything that doesn't match another category"
        category.id == CategoryIds.OTHER_INCOME -> "Money in that doesn't match another category"
        category.keywords.isEmpty() -> "No keywords yet"
        else -> category.keywords.take(5).joinToString(", ") +
            if (category.keywords.size > 5) " +${category.keywords.size - 5} more" else ""
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { CategoryAvatar(category) },
        headlineContent = { Text(category.name) },
        supportingContent = { Text(description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun RuleRow(rule: MerchantRuleEntity, category: CategoryEntity?, onForget: () -> Unit) {
    ListItem(
        leadingContent = { CategoryAvatar(category) },
        headlineContent = { Text(rule.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("→ ${category?.name ?: "Deleted category"}") },
        trailingContent = {
            IconButton(onClick = onForget) { Icon(Icons.Filled.Close, contentDescription = "Forget ${rule.label}") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
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
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(8) },
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(76.dp),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (isNew) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CategoryKind.entries.forEachIndexed { index, option ->
                            SegmentedButton(
                                selected = kind == option,
                                onClick = { kind = option },
                                shape = SegmentedButtonDefaults.itemShape(index, CategoryKind.entries.size),
                            ) { Text(if (option == CategoryKind.EXPENSE) "Spending" else "Income") }
                        }
                    }
                }
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Keywords") },
                    supportingText = { Text("Comma separated, e.g. \"chai point, tea stall\"") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = null)
                        Text("  Delete category")
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
