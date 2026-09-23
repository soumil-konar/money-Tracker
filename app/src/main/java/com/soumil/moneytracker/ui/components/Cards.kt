package com.soumil.moneytracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.soumil.moneytracker.data.db.AccountEntity
import com.soumil.moneytracker.data.db.ScheduledTransactionRecord
import com.soumil.moneytracker.data.db.SubscriptionRecord
import com.soumil.moneytracker.data.db.TransactionRecord
import com.soumil.moneytracker.data.model.AccountKind
import com.soumil.moneytracker.data.model.TransactionCategory
import com.soumil.moneytracker.data.model.TransactionDirection
import com.soumil.moneytracker.data.model.TransactionStatus
import com.soumil.moneytracker.ui.asCurrency
import com.soumil.moneytracker.ui.asDayMonth
import com.soumil.moneytracker.ui.asFullDate

@Composable
fun SectionCard(
    title: String,
    subtitle: String? = null,
    containerColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedColor = containerColor ?: MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .trackerAnimateContent(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = resolvedColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                content()
            },
        )
    }
}

@Composable
fun InsightBadge(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.trackerAnimateContent(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun ExpressiveHeroCard(
    monthSpent: Double,
    budget: Double?,
    safeDailySpend: Double,
    netSavings: Double,
    onSetBudgetClick: () -> Unit,
    onBudgetClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onBudgetClick)
            .trackerAnimateContent(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        text = "Total Monthly Outflow",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    modifier = Modifier.clickable(onClick = onSetBudgetClick),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit budget",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = monthSpent.asCurrency(),
                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "Verified debits & out-of-pocket expenses",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(10.dp))

            BudgetGauge(
                spent = monthSpent,
                budget = budget,
            )

            if (budget != null && budget > 0.0) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Daily Safe Burn",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "${safeDailySpend.asCurrency()} / day",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Net Cashflow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val isPositive = netSavings >= 0.0
                        Text(
                            text = (if (isPositive) "+" else "") + netSavings.asCurrency(),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isPositive) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BentoMetricCard(
    title: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .trackerAnimateContent(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(17.dp),
                    )
                }

                if (subtitle != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun PermissionBanner(
    smsPermissionGranted: Boolean,
    onRequestPermissions: () -> Unit,
    onImportRecentSms: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (smsPermissionGranted) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .trackerAnimateContent(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Sync active",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }

                    Column {
                        Text(
                            text = "SMS auto-tracking active",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Listening for bank & UPI transaction alerts",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (onImportRecentSms != null) {
                    OutlinedButton(
                        onClick = onImportRecentSms,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(
                            text = "Import SMS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    } else {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .trackerAnimateContent(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsNone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable automated tracking",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Read bank & UPI alerts to auto-categorize expenses with on-device AI.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = onRequestPermissions,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp),
                    ) {
                        Text(
                            text = "Grant SMS access",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: TransactionRecord,
    modifier: Modifier = Modifier,
) {
    val isTransfer = transaction.category == TransactionCategory.TRANSFER
    val accent = when {
        isTransfer -> MaterialTheme.colorScheme.secondary
        transaction.direction == TransactionDirection.CREDIT -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when {
        isTransfer -> Icons.Outlined.CreditCard
        transaction.direction == TransactionDirection.CREDIT -> Icons.Outlined.ArrowDownward
        else -> Icons.Outlined.ArrowUpward
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
            )
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (transaction.confidence >= 0.95 && !transaction.note.isNullOrBlank()) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = "AI Enriched",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
            Text(
                text = listOfNotNull(transaction.category.label, transaction.accountName)
                    .joinToString(" | "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!transaction.countsTowardBudget) {
                Text(
                    text = "Excluded from budget",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
            if (!transaction.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Place,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = transaction.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = buildString {
                    if (isTransfer) {
                        append("")
                    } else if (transaction.direction == TransactionDirection.CREDIT) {
                        append("+ ")
                    } else {
                        append("- ")
                    }
                    append(transaction.amount.asCurrency())
                },
                style = MaterialTheme.typography.titleMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = transaction.occurredAtMillis.asFullDate(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (transaction.status == TransactionStatus.REVIEW) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                ) {
                    Text(
                        text = "Review",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SubscriptionItem(
    subscription: SubscriptionRecord,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .trackerAnimateContent(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = subscription.merchant, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = listOfNotNull(
                        subscription.amount.asCurrency(),
                        "Every ${subscription.billingCycleDays} days",
                        subscription.accountName,
                    ).joinToString(" | "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            trailing?.invoke()
        }
    }
}

@Composable
fun ScheduledTransactionItem(
    scheduledTransaction: ScheduledTransactionRecord,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .trackerAnimateContent(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = scheduledTransaction.merchant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        scheduledTransaction.category.label,
                        scheduledTransaction.accountName,
                        scheduledTransaction.sourceSender,
                    ).joinToString(" | "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = scheduledTransaction.amount.asCurrency(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = scheduledTransaction.scheduledForMillis.asFullDate(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                ) {
                    Text(
                        text = scheduledTransaction.kind.label,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AccountItem(
    account: AccountEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = if (account.kind == AccountKind.CARD) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
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
    }.ifBlank {
        account.kind.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .trackerAnimateContent(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (account.kind == AccountKind.CARD) {
                        Icons.Outlined.CreditCard
                    } else {
                        Icons.Outlined.AccountBalance
                    },
                    contentDescription = null,
                    tint = accent,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (account.kind == AccountKind.CARD) "Balance: ${account.currentBalance.asCurrency()}" else "Avl: ${account.currentBalance.asCurrency()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (account.currentBalance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                    account.balanceUpdatedAtMillis?.let {
                        Text(
                            text = "• ${it.asDayMonth()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactCardActionButton(
                    icon = Icons.Outlined.Edit,
                    contentDescription = "Edit account",
                    tint = MaterialTheme.colorScheme.onBackground,
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f),
                    onClick = onEdit,
                )
                CompactCardActionButton(
                    icon = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete account",
                    tint = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
fun AccountBalanceCard(
    account: AccountEntity,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isCard = account.kind == AccountKind.CARD
    val accent = if (isCard) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier
            .width(185.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isCard) Icons.Outlined.CreditCard else Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = accent,
                    )
                }
                account.lastFourDigits?.let {
                    Text(
                        text = "•••• $it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = account.institutionName ?: account.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = account.currentBalance.asCurrency(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (isCard) "Card Balance" else "Available Balance",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun CompactCardActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    containerColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.16f)),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryLegend(slices: List<Pair<Color, String>>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        slices.forEach { (color, label) ->
            AssistChip(
                onClick = {},
                label = { Text(label) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                },
            )
        }
    }
}
