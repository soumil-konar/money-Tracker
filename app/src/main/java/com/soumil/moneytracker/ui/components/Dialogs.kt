package com.soumil.moneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.model.AccountDraft
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.CardType
import com.soumil.moneytracker.data.model.SubscriptionDraft
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionDraft
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import com.soumil.moneytracker.ui.asCurrency
import com.soumil.moneytracker.ui.asDateTime
import com.soumil.moneytracker.ui.asFullDate
import com.soumil.moneytracker.ui.haptics.LocalAppHaptics
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun BudgetDialog(
    currentValue: Double?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val haptics = LocalAppHaptics.current
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
            Button(onClick = {
                haptics.success()
                onConfirm(amount)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) {
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
    modifier: Modifier = Modifier,
    title: String = "Add transaction",
    confirmLabel: String = "Save",
    initialDraft: TransactionDraft? = null,
    dialogKey: Int = 0,
) {
    val haptics = LocalAppHaptics.current
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
    var countsTowardBudget by rememberSaveable(dialogKey) {
        mutableStateOf(initialDraft?.countsTowardBudget ?: true)
    }
    var occurredAtMillis by rememberSaveable(dialogKey) {
        mutableStateOf(initialDraft?.occurredAtMillis ?: System.currentTimeMillis())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    val selectedAccount = accounts.firstOrNull { it.id == selectedAccountId } ?: accounts.firstOrNull()

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = occurredAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    haptics.click()
                    datePickerState.selectedDateMillis?.let { occurredAtMillis = it }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    showDatePicker = false
                }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            .fillMaxHeight(0.85f)
            .heightIn(max = 620.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 10.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
        ) {
            // 1. Pinned Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = if (initialDraft == null) "Manual Entry" else "Ledger Correction",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                IconButton(
                    onClick = {
                        haptics.click()
                        onDismiss()
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (initialDraft == null) {
                    "Capture a transaction in the same ledger style as imported SMS entries."
                } else {
                    "Adjust the merchant, amount, direction, account, date, or note."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )

            Spacer(modifier = Modifier.height(14.dp))

            // 2. Dedicated Scrollable Form Section
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = if (selectedDirection == TransactionDirection.DEBIT) "DR" else "CR",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column {
                            Text(
                                text = "Ledger details",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = "Keep the transaction clean before it hits charts and budgets.",
                                style = MaterialTheme.typography.bodySmall,
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
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(16.dp),
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

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        haptics.click()
                        showDatePicker = true
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = "Select transaction date",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Column {
                                Text(
                                    text = "Transaction date",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = occurredAtMillis.asFullDate(),
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ) {
                            Text(
                                text = "Change",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedCategory.label,
                        onValueChange = {},
                        shape = RoundedCornerShape(16.dp),
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
                                    haptics.tick()
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
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        readOnly = true,
                        value = selectedAccount?.name.orEmpty(),
                        onValueChange = {},
                        shape = RoundedCornerShape(16.dp),
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
                                    haptics.tick()
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
                    placeholder = { Text("Optional context") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Count toward budget",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = "Excluded transactions skip the budget gauge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = countsTowardBudget,
                        onCheckedChange = {
                            haptics.toggle()
                            countsTowardBudget = it
                        },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Pinned Action Buttons Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        val parsedAmount = amount.replace(",", "").toDoubleOrNull() ?: return@Button
                        if (merchant.isBlank()) return@Button
                        haptics.success()
                        onConfirm(
                            TransactionDraft(
                                amount = parsedAmount,
                                direction = selectedDirection,
                                merchant = merchant.trim(),
                                category = selectedCategory,
                                accountId = selectedAccount?.id,
                                note = note.trim(),
                                occurredAtMillis = occurredAtMillis,
                                countsTowardBudget = countsTowardBudget,
                            ),
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(confirmLabel)
                }
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
    val haptics = LocalAppHaptics.current
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
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    haptics.heavy()
                    onConfirm()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Delete")
            }
        }
    }
}

@Composable
fun DeleteAccountDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val haptics = LocalAppHaptics.current
    val metadata = when (account.kind) {
        AccountKind.CARD -> listOfNotNull(
            account.cardType?.label,
            account.institutionName,
            account.lastFourDigits?.let { "ending $it" },
            "RuPay".takeIf { account.isRupayCreditCard },
        ).joinToString(" | ")

        else -> listOfNotNull(
            account.kind.name.lowercase().replaceFirstChar { it.uppercase() },
            account.institutionName,
            account.lastFourDigits?.let { "A/C $it" },
        ).joinToString(" | ")
    }

    TrackerDialogScaffold(
        eyebrow = "Account Removal",
        title = "Delete account",
        subtitle = "This removes the account or card profile from setup and future matching. Existing transactions stay in the ledger but lose this account label.",
        onDismiss = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    haptics.heavy()
                    onConfirm()
                },
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
    val haptics = LocalAppHaptics.current
    var merchant by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var cycleDays by rememberSaveable { mutableStateOf("30") }
    val defaultDueMillis = remember {
        LocalDate.now().plusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    var nextDueMillis by rememberSaveable { mutableStateOf(defaultDueMillis) }
    var showDatePicker by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf(accounts.firstOrNull()) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = nextDueMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    haptics.click()
                    datePickerState.selectedDateMillis?.let { nextDueMillis = it }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptics.click()
                    showDatePicker = false
                }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add subscription") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Service or merchant name") },
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
                    OutlinedButton(
                        onClick = {
                            haptics.click()
                            showDatePicker = true
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Next due: ${nextDueMillis.asFullDate()}")
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = !accountExpanded },
                ) {
                    OutlinedTextField(
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
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
                                    haptics.tick()
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
                    if (merchant.isBlank()) return@Button
                    haptics.success()
                    onConfirm(
                        SubscriptionDraft(
                            merchant = merchant.trim(),
                            amount = parsedAmount,
                            billingCycleDays = parsedCycle,
                            nextDueAtMillis = nextDueMillis,
                            accountId = selectedAccount?.id,
                        ),
                    )
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) {
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
    val haptics = LocalAppHaptics.current
    FilterChip(
        selected = selected,
        onClick = {
            haptics.selection()
            onClick()
        },
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
                .fillMaxWidth(0.92f)
                .widthIn(max = 440.dp)
                .fillMaxHeight(0.85f)
                .heightIn(max = 680.dp)
                .imePadding()
                .padding(vertical = 16.dp),
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

private const val OtherBankOption = "Other Bank"
private val SupportedBankOptions = listOf(
    "State Bank of India",
    "HDFC Bank",
    "ICICI Bank",
    "Axis Bank",
    "Kotak Bank",
    "Punjab National Bank",
    "Bank of Baroda",
    "Canara Bank",
    "Union Bank of India",
    "IndusInd Bank",
    "IDFC FIRST Bank",
    "Yes Bank",
    "Federal Bank",
    "Indian Bank",
    "Bank of India",
    "Central Bank of India",
    OtherBankOption,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditorDialog(
    initialDraft: AccountDraft,
    onDismiss: () -> Unit,
    onConfirm: (AccountDraft) -> Unit,
    title: String,
    confirmLabel: String,
    dialogKey: Int = 0,
) {
    val haptics = LocalAppHaptics.current
    val initialBankOption = remember(initialDraft.institutionName) {
        bankOptionFor(initialDraft.institutionName)
    }
    var name by rememberSaveable(dialogKey) { mutableStateOf(initialDraft.name) }
    var selectedBankOption by rememberSaveable(dialogKey) { mutableStateOf(initialBankOption) }
    var customBankName by rememberSaveable(dialogKey) {
        mutableStateOf(
            initialDraft.institutionName.takeIf { initialBankOption == OtherBankOption }.orEmpty(),
        )
    }
    var selectedCardType by rememberSaveable(dialogKey) {
        mutableStateOf(initialDraft.cardType ?: CardType.CREDIT)
    }
    var lastFourDigits by rememberSaveable(dialogKey) { mutableStateOf(initialDraft.lastFourDigits.orEmpty()) }
    var isRupayCreditCard by rememberSaveable(dialogKey) {
        mutableStateOf(initialDraft.isRupayCreditCard)
    }
    var balanceText by rememberSaveable(dialogKey) {
        mutableStateOf(if (initialDraft.currentBalance != 0.0) initialDraft.currentBalance.toString() else "")
    }
    val resolvedInstitutionName = resolveInstitutionName(
        selectedOption = selectedBankOption,
        customBankName = customBankName,
    )

    TrackerDialogScaffold(
        eyebrow = when (initialDraft.kind) {
            AccountKind.BANK -> "Bank Profile"
            AccountKind.CARD -> "Card Profile"
            else -> "Account"
        },
        title = title,
        subtitle = when (initialDraft.kind) {
            AccountKind.BANK -> "Keep your bank identity clean so imported SMS land on the right account."
            AccountKind.CARD -> "Match card last four digits so SMS imports attach to the right card every time."
            else -> "Edit the account name and metadata used across the ledger."
        },
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Account name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = balanceText,
            onValueChange = { balanceText = it },
            label = { Text(if (initialDraft.kind == AccountKind.CARD) "Card Balance / Outflow (₹)" else "Available Balance (₹)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )

        if (initialDraft.kind == AccountKind.BANK || initialDraft.kind == AccountKind.CARD) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Bank",
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SupportedBankOptions.forEach { bank ->
                        FilterChip(
                            selected = selectedBankOption == bank,
                            onClick = {
                                haptics.tick()
                                selectedBankOption = bank
                            },
                            label = { Text(bank) },
                        )
                    }
                }
                if (selectedBankOption == OtherBankOption) {
                    OutlinedTextField(
                        value = customBankName,
                        onValueChange = { customBankName = it },
                        label = { Text("Bank name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (initialDraft.kind == AccountKind.CARD) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Card type",
                    style = MaterialTheme.typography.titleMedium,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CardTypeChoice(
                        label = "Credit",
                        selected = selectedCardType == CardType.CREDIT,
                        onClick = { selectedCardType = CardType.CREDIT },
                    )
                    CardTypeChoice(
                        label = "Debit",
                        selected = selectedCardType == CardType.DEBIT,
                        onClick = { selectedCardType = CardType.DEBIT },
                    )
                }
                OutlinedTextField(
                    value = lastFourDigits,
                    onValueChange = { lastFourDigits = it.filter(Char::isDigit).take(4) },
                    label = { Text("Card last 4 digits") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (selectedCardType == CardType.CREDIT) {
                    FilterChip(
                        selected = isRupayCreditCard,
                        onClick = {
                            haptics.toggle()
                            isRupayCreditCard = !isRupayCreditCard
                        },
                        label = { Text("RuPay credit card") },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) return@Button
                    val sanitizedLastFour = lastFourDigits
                        .filter(Char::isDigit)
                        .takeLast(4)
                        .takeIf { initialDraft.kind != AccountKind.CARD || it.length == 4 }
                    if ((initialDraft.kind == AccountKind.BANK || initialDraft.kind == AccountKind.CARD) &&
                        resolvedInstitutionName.isNullOrBlank()
                    ) {
                        return@Button
                    }
                    if (initialDraft.kind == AccountKind.CARD && sanitizedLastFour == null) {
                        return@Button
                    }
                    haptics.success()
                    onConfirm(
                        AccountDraft(
                            name = trimmedName,
                            kind = initialDraft.kind,
                            institutionName = resolvedInstitutionName,
                            cardType = if (initialDraft.kind == AccountKind.CARD) selectedCardType else null,
                            lastFourDigits = sanitizedLastFour,
                            isRupayCreditCard = if (initialDraft.kind == AccountKind.CARD &&
                                selectedCardType == CardType.CREDIT
                            ) {
                                isRupayCreditCard
                            } else {
                                false
                            },
                            currentBalance = balanceText.toDoubleOrNull() ?: 0.0,
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InitialSetupDialog(
    configuredBank: AccountEntity?,
    configuredCards: List<AccountEntity>,
    onDismiss: () -> Unit,
    onSaveBank: (institutionName: String, accountName: String) -> Unit,
    onAddCard: (AccountDraft) -> Unit,
    onFinish: () -> Unit,
) {
    val haptics = LocalAppHaptics.current
    val initialBankOption = remember(configuredBank?.institutionName) {
        bankOptionFor(configuredBank?.institutionName)
    }
    var step by rememberSaveable { mutableStateOf(0) }
    var selectedBankOption by rememberSaveable { mutableStateOf(initialBankOption) }
    var customBankName by rememberSaveable {
        mutableStateOf(
            configuredBank?.institutionName.takeIf { initialBankOption == OtherBankOption }.orEmpty(),
        )
    }
    var bankAccountName by rememberSaveable {
        mutableStateOf(configuredBank?.name.orEmpty())
    }
    var selectedCardType by rememberSaveable { mutableStateOf(CardType.CREDIT) }
    var cardName by rememberSaveable { mutableStateOf("") }
    var cardLastFourDigits by rememberSaveable { mutableStateOf("") }
    var isRupayCreditCard by rememberSaveable { mutableStateOf(false) }
    val resolvedInstitutionName = resolveInstitutionName(
        selectedOption = selectedBankOption,
        customBankName = customBankName,
    )

    TrackerDialogScaffold(
        eyebrow = "Setup",
        title = if (step == 0) "Tell the app your bank" else "Load your cards",
        subtitle = if (step == 0) {
            "Start with the bank that sends the majority of your SMS alerts. You can edit this later from More."
        } else {
            "Add the last 4 digits of each card so SMS imports attach to the exact card instead of a generic placeholder."
        },
        onDismiss = onDismiss,
    ) {
        if (step == 0) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SupportedBankOptions.forEach { bank ->
                        FilterChip(
                            selected = selectedBankOption == bank,
                            onClick = {
                                haptics.tick()
                                selectedBankOption = bank
                                if (bank != OtherBankOption && bankAccountName.isBlank()) {
                                    bankAccountName = bank
                                }
                            },
                            label = { Text(bank) },
                        )
                    }
                }
                if (selectedBankOption == OtherBankOption) {
                    OutlinedTextField(
                        value = customBankName,
                        onValueChange = { customBankName = it },
                        label = { Text("Bank name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = bankAccountName,
                    onValueChange = { bankAccountName = it },
                    label = { Text("Account name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Cards for ${resolvedInstitutionName.orEmpty()}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "RuPay credit-card UPI spends will stay visible under the credit card and still show up in the UPI filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (configuredCards.isEmpty()) {
                    Text(
                        text = "No cards added yet. You can still finish setup and add them later from More.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        configuredCards.forEach { card ->
                            SetupCardSummary(account = card)
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CardTypeChoice(
                                label = "Credit",
                                selected = selectedCardType == CardType.CREDIT,
                                onClick = { selectedCardType = CardType.CREDIT },
                            )
                            CardTypeChoice(
                                label = "Debit",
                                selected = selectedCardType == CardType.DEBIT,
                                onClick = { selectedCardType = CardType.DEBIT },
                            )
                        }
                        OutlinedTextField(
                            value = cardLastFourDigits,
                            onValueChange = { cardLastFourDigits = it.filter(Char::isDigit).take(4) },
                            label = { Text("Card last 4 digits") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = cardName,
                            onValueChange = { cardName = it },
                            label = { Text("Card name") },
                            placeholder = { Text("For example, HDFC Bank Credit Card") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (selectedCardType == CardType.CREDIT) {
                            FilterChip(
                                selected = isRupayCreditCard,
                                onClick = {
                                    haptics.toggle()
                                    isRupayCreditCard = !isRupayCreditCard
                                },
                                label = { Text("RuPay credit card") },
                            )
                        }
                        Button(
                            onClick = {
                                val institutionName = resolvedInstitutionName ?: return@Button
                                val lastFourDigits = cardLastFourDigits.filter(Char::isDigit).takeLast(4)
                                if (lastFourDigits.length != 4) return@Button
                                haptics.success()
                                onAddCard(
                                    AccountDraft(
                                        name = cardName.trim().ifBlank {
                                            buildDefaultCardName(
                                                institutionName = institutionName,
                                                cardType = selectedCardType,
                                                lastFourDigits = lastFourDigits,
                                            )
                                        },
                                        kind = AccountKind.CARD,
                                        institutionName = institutionName,
                                        cardType = selectedCardType,
                                        lastFourDigits = lastFourDigits,
                                        isRupayCreditCard = selectedCardType == CardType.CREDIT && isRupayCreditCard,
                                    ),
                                )
                                cardName = ""
                                cardLastFourDigits = ""
                                isRupayCreditCard = false
                                selectedCardType = CardType.CREDIT
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Add card")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step == 0) {
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Skip")
                }
                Button(
                    onClick = {
                        val institutionName = resolvedInstitutionName ?: return@Button
                        val accountName = bankAccountName.trim().ifBlank { institutionName }
                        haptics.click()
                        onSaveBank(institutionName, accountName)
                        step = 1
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Next")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        haptics.click()
                        step = 0
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Back")
                }
                Button(
                    onClick = {
                        haptics.success()
                        onFinish()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Finish")
                }
            }
        }
    }
}

@Composable
private fun SetupCardSummary(account: AccountEntity) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = listOfNotNull(
                    account.cardType?.label,
                    account.institutionName,
                    account.lastFourDigits?.let { "ending $it" },
                    "RuPay".takeIf { account.isRupayCreditCard },
                ).joinToString(" | "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RowScope.CardTypeChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val haptics = LocalAppHaptics.current
    FilterChip(
        selected = selected,
        onClick = {
            haptics.selection()
            onClick()
        },
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
            selectedLabelColor = MaterialTheme.colorScheme.onBackground,
        ),
        modifier = Modifier.weight(1f),
    )
}

private fun bankOptionFor(institutionName: String?): String {
    if (institutionName.isNullOrBlank()) return SupportedBankOptions.first()
    return institutionName.takeIf { it in SupportedBankOptions } ?: OtherBankOption
}

private fun resolveInstitutionName(
    selectedOption: String,
    customBankName: String,
): String? {
    return when (selectedOption) {
        OtherBankOption -> customBankName.trim().takeIf { it.isNotBlank() }
        else -> selectedOption.takeIf { it.isNotBlank() }
    }
}

private fun buildDefaultCardName(
    institutionName: String,
    cardType: CardType,
    lastFourDigits: String,
): String {
    return "$institutionName ${cardType.label} ending $lastFourDigits"
}

@Composable
fun AddExclusionKeywordDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    var keyword by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Exclusion Rule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Transactions matching this keyword in merchant, subject, or message text will be automatically excluded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    label = { Text("Keyword or Merchant name") },
                    placeholder = { Text("e.g. Steam, Epic Games, PlayStation") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (keyword.isNotBlank()) {
                        haptics.success()
                        onConfirm(keyword.trim())
                    }
                },
                enabled = keyword.isNotBlank(),
            ) {
                Text("Add Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                haptics.click()
                onDismiss()
            }) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun BalanceProofDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onEditBalance: () -> Unit,
    onTrueUpBalance: () -> Unit = {},
) {
    val haptics = LocalAppHaptics.current
    val isVerified = account.isBalanceVerified && !account.balanceProofSnippet.isNullOrBlank()

    TrackerDialogScaffold(
        eyebrow = if (isVerified) "Audited Proof" else "Balance Audit",
        title = "Available Balance Proof",
        subtitle = "Verification evidence and audit trail for this account's stated balance.",
        onDismiss = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = account.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        account.lastFourDigits?.let {
                            Text(
                                text = if (account.kind == AccountKind.CARD) "Card ending in $it" else "Account ending in $it",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (isVerified) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.18f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = Color(0xFF10B981),
                                )
                                Text(
                                    text = "VERIFIED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                                Text(
                                    text = "UNVERIFIED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (account.kind == AccountKind.CARD) "Card Balance / Outflow" else "Available Balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = account.currentBalance.asCurrency(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                account.balanceUpdatedAtMillis?.let {
                    Text(
                        text = "Last updated: ${it.asDateTime()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (isVerified) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF10B981).copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.25f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Official Statement Proof",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981),
                        )
                    }

                    account.balanceProofSource?.let { source ->
                        Text(
                            text = "Proof Source: $source",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = account.balanceProofSnippet.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(12.dp),
                        )
                    }

                    Text(
                        text = "This available balance was extracted directly from an official bank alert. Context verification confirmed this is an authentic balance statement and excluded credit limits, dues, minimum balances, or loan figures.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "No Official Proof Captured Yet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Text(
                        text = "This balance does not yet have a matching official bank statement alert. The number shown was either manually set or estimated. Once your bank sends an SMS or email containing your available balance, the app will automatically verify and lock it with substantial proof.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Close")
            }
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                    onEditBalance()
                },
                modifier = Modifier.weight(1.1f),
            ) {
                Text("Edit Details")
            }
            Button(
                onClick = {
                    haptics.click()
                    onDismiss()
                    onTrueUpBalance()
                },
                modifier = Modifier.weight(1.3f),
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.size(4.dp))
                Text("True-Up")
            }
        }
    }
}

@Composable
fun TrueUpBalanceDialog(
    account: AccountEntity,
    onDismiss: () -> Unit,
    onConfirm: (newBalance: Double, reason: String?) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    var balanceText by rememberSaveable {
        mutableStateOf(if (account.currentBalance > 0.0) "%.2f".format(account.currentBalance) else "")
    }
    var reasonText by rememberSaveable { mutableStateOf("") }
    val parsedBalance = balanceText.toDoubleOrNull()
    val isValid = parsedBalance != null && parsedBalance >= 0.0

    TrackerDialogScaffold(
        eyebrow = "Account Reconciliation",
        title = "Adjust / True-Up Balance",
        subtitle = "Set your account's exact real-world balance. Money Tracker will lock this as the new anchor and calculate all future transactions from here.",
        onDismiss = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Current Tracked Balance: ${account.currentBalance.asCurrency()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                account.lastFourDigits?.let {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Text(
                            text = "••$it",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            value = balanceText,
            onValueChange = { balanceText = it },
            label = { Text("Exact Verified Balance (₹)") },
            placeholder = { Text("e.g. 45250.00") },
            leadingIcon = {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = reasonText,
            onValueChange = { reasonText = it },
            label = { Text("Reconciliation Note (Optional)") },
            placeholder = { Text("e.g. Net-banking sync, ATM cash withdrawal") },
            leadingIcon = {
                Icon(Icons.Outlined.Info, contentDescription = null)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "This sets a new baseline anchor. All subsequent SMS & push transaction alerts will add to or subtract from this amount in real time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (parsedBalance != null && parsedBalance >= 0.0) {
                        haptics.success()
                        onConfirm(parsedBalance, reasonText.takeIf { it.isNotBlank() })
                        onDismiss()
                    }
                },
                enabled = isValid,
                modifier = Modifier.weight(1.3f),
            ) {
                Text("Confirm True-Up")
            }
        }
    }
}

@Composable
fun ExportBackupPassphraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    var passphrase by rememberSaveable { mutableStateOf("") }
    var confirmPassphrase by rememberSaveable { mutableStateOf("") }
    var isPassphraseVisible by rememberSaveable { mutableStateOf(false) }

    val isLengthValid = passphrase.length >= 6
    val isMatch = passphrase == confirmPassphrase
    val canExport = isLengthValid && isMatch

    TrackerDialogScaffold(
        eyebrow = "Security & Encryption",
        title = "Export Encrypted Backup",
        subtitle = "Create a password-protected AES-256-GCM encrypted snapshot of your financial ledger.",
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Backup Passphrase (min 6 characters)") },
            placeholder = { Text("Enter a secure passphrase") },
            visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(Icons.Outlined.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                    Icon(
                        imageVector = if (isPassphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = confirmPassphrase,
            onValueChange = { confirmPassphrase = it },
            label = { Text("Confirm Passphrase") },
            placeholder = { Text("Re-enter passphrase") },
            visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(Icons.Outlined.Lock, contentDescription = null)
            },
            isError = confirmPassphrase.isNotEmpty() && !isMatch,
            supportingText = {
                if (confirmPassphrase.isNotEmpty() && !isMatch) {
                    Text("Passphrases do not match", color = MaterialTheme.colorScheme.error)
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Your data is encrypted locally using AES-256-GCM with PBKDF2 key derivation. Without this passphrase, the backup file cannot be decrypted by anyone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (canExport) {
                        haptics.success()
                        onConfirm(passphrase)
                        onDismiss()
                    }
                },
                enabled = canExport,
                modifier = Modifier.weight(1.3f),
            ) {
                Text("Export Backup")
            }
        }
    }
}

@Composable
fun RestoreBackupPassphraseDialog(
    onDismiss: () -> Unit,
    onConfirm: (passphrase: String) -> Unit,
) {
    val haptics = LocalAppHaptics.current
    var passphrase by rememberSaveable { mutableStateOf("") }
    var isPassphraseVisible by rememberSaveable { mutableStateOf(false) }

    TrackerDialogScaffold(
        eyebrow = "Encrypted Restore",
        title = "Decrypt & Restore",
        subtitle = "Enter the passphrase used when creating this backup file.",
        onDismiss = onDismiss,
    ) {
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Backup Passphrase") },
            placeholder = { Text("Enter decryption passphrase") },
            visualTransformation = if (isPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
            leadingIcon = {
                Icon(Icons.Outlined.Lock, contentDescription = null)
            },
            trailingIcon = {
                IconButton(onClick = { isPassphraseVisible = !isPassphraseVisible }) {
                    Icon(
                        imageVector = if (isPassphraseVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = null,
                    )
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Restoring imports accounts, transactions, and budgets while preserving existing unique records. Ledger balances are automatically audited upon completion.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = {
                    haptics.click()
                    onDismiss()
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Cancel")
            }
            Button(
                onClick = {
                    if (passphrase.isNotBlank()) {
                        haptics.success()
                        onConfirm(passphrase)
                        onDismiss()
                    }
                },
                enabled = passphrase.isNotBlank(),
                modifier = Modifier.weight(1.3f),
            ) {
                Text("Decrypt & Restore")
            }
        }
    }
}


