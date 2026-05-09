package com.soumil.moneytracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
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
            ) {
                Text(confirmLabel)
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
fun DeleteTransactionDialog(
    merchant: String,
    amount: Double,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete transaction") },
        text = { Text("Delete $merchant for ${amount.asCurrency()}? This cannot be undone.") },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Delete")
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
private fun DirectionChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = !selected) {
        Text(label)
    }
}

private fun Double.toInputAmount(): String {
    return if (this % 1.0 == 0.0) {
        toLong().toString()
    } else {
        toString()
    }
}
