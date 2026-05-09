package com.soumil.moneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.model.SubscriptionDraft
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionDraft
import com.soumil.moneytracker.ui.asCurrency

@Composable
fun BudgetDialog(
    currentValue: Double?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf(currentValue?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set monthly budget") },
        text = {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                singleLine = true,
                label = { Text("Budget amount") },
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (TransactionDraft) -> Unit,
    title: String = "Add transaction",
    confirmLabel: String = "Save",
    initialDraft: TransactionDraft? = null,
    dialogKey: Int = 0,
) {
    var merchant by rememberSaveable(dialogKey) { mutableStateOf(initialDraft?.merchant.orEmpty()) }
    var amount by rememberSaveable(dialogKey) { mutableStateOf(initialDraft?.amount?.toInputAmount().orEmpty()) }
    var note by rememberSaveable(dialogKey) { mutableStateOf(initialDraft?.note.orEmpty()) }
    var selectedDirection by rememberSaveable(dialogKey) {
        mutableStateOf(initialDraft?.direction ?: TransactionDirection.DEBIT)
    }
    var selectedCategory by rememberSaveable(dialogKey) {
        mutableStateOf(initialDraft?.category ?: TransactionCategory.OTHER)
    }
    var selectedAccountId by rememberSaveable(dialogKey) {
        mutableStateOf(initialDraft?.accountId ?: accounts.firstOrNull()?.id)
    }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()

    TrackerDialogScaffold(
        eyebrow = if (initialDraft == null) "Manual Entry" else "Ledger Correction",
        title = title,
        subtitle = if (initialDraft == null) {
            "Capture a transaction in the same ledger style as the imported SMS entries."
        } else {
            "Adjust the merchant, amount, direction, account, or note without changing the original ledger date."
        },
        onDismiss = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (selectedDirection == TransactionDirection.DEBIT) "DR" else "CR",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Column {
                    Text(
                        text = "Ledger details",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "Keep the transaction clean before it hits charts, filters, and budgets.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        OutlinedTextField(
            value = merchant,
            onValueChange = { merchant = it },
            label = { Text("Merchant") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            DirectionChoice(
                label = "Debit",
                selected = selectedDirection == TransactionDirection.DEBIT,
                onClick = { selectedDirection = TransactionDirection.DEBIT },
            )
            DirectionChoice(
                label = "Credit",
                selected = selectedDirection == TransactionDirection.CREDIT,
                onClick = { selectedDirection = TransactionDirection.CREDIT },
            )
        }
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                value = selectedCategory.label,
                onValueChange = {},
                label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
            )
            DropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false },
            ) {
                TransactionCategory.entries.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.label) },
                        onClick = {
                            selectedCategory = category
                            categoryExpanded = false
                        },
                    )
                }
            }
        }
        ExposedDropdownMenuBox(
            expanded = accountExpanded,
            onExpandedChange = { accountExpanded = !accountExpanded },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                readOnly = true,
                value = selectedAccount?.name.orEmpty(),
                onValueChange = {},
                label = { Text("Account") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
            )
            DropdownMenu(
                expanded = accountExpanded,
                onDismissRequest = { accountExpanded = false },
            ) {
                accounts.forEach { account ->
                    DropdownMenuItem(
                        text = { Text(account.name) },
                        onClick = {
                            selectedAccountId = account.id
                            accountExpanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            placeholder = { Text("Optional context for future you") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val parsedAmount = amount.replace(",", "").toDoubleOrNull() ?: return@Button
                    if (merchant.isBlank()) return@Button
                    onConfirm(
                        TransactionDraft(
                            amount = parsedAmount,
                            direction = selectedDirection,
                            merchant = merchant.trim(),
                            category = selectedCategory,
                            accountId = selectedAccount?.id,
                            note = note.trim(),
                            occurredAtMillis = initialDraft?.occurredAtMillis ?: System.currentTimeMillis(),
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@Composable
fun DeleteTransactionDialog(
    merchant: String,
    amount: Double,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    TrackerDialogScaffold(
        eyebrow = "Ledger Cleanup",
        title = "Delete transaction",
        subtitle = "Remove this item from the ledger. This action cannot be undone.",
        onDismiss = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = merchant,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = amount.asCurrency(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            ) {
                Text("Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSubscriptionDialog(
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (SubscriptionDraft) -> Unit,
) {
    var merchant by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var cycleDays by rememberSaveable { mutableStateOf("30") }
    var dueInDays by rememberSaveable { mutableStateOf("30") }
    var accountExpanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = cycleDays,
                        onValueChange = { cycleDays = it },
                        label = { Text("Cycle days") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = dueInDays,
                        onValueChange = { dueInDays = it },
                        label = { Text("Due in days") },
                        modifier = Modifier.weight(1f),
                    )
                }
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = !accountExpanded },
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedAccount?.name.orEmpty(),
                        onValueChange = {},
                        label = { Text("Account") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                    )
                    DropdownMenu(
                        expanded = accountExpanded,
                        onDismissRequest = { accountExpanded = false },
                    ) {
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedAccount = account
                                    accountExpanded = false
                                },
                            )
                        }
                    }
                }
                Text(
                    text = "Suggestions from recurring transactions appear here too. Manual entries stay local on-device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.replace(",", "").toDoubleOrNull() ?: return@Button
                    val parsedCycle = cycleDays.toIntOrNull() ?: return@Button
                    val parsedDueInDays = dueInDays.toIntOrNull() ?: return@Button
                    if (merchant.isBlank()) return@Button
                    onConfirm(
                        SubscriptionDraft(
                            merchant = merchant.trim(),
                            amount = parsedAmount,
                            billingCycleDays = parsedCycle,
                            nextDueAtMillis = System.currentTimeMillis() + parsedDueInDays * 24L * 60 * 60 * 1000,
                            accountId = selectedAccount?.id,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun RowScope.DirectionChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun TrackerDialogScaffold(
    eyebrow: String,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(30.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                ) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                content()
            }
        }
    }
}

private fun Double.toInputAmount(): String {
    return if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString()
    }
}
