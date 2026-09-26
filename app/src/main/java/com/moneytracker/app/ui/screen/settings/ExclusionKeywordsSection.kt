package com.moneytracker.app.ui.screen.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moneytracker.app.ui.components.SectionCard
import com.moneytracker.app.ui.haptics.LocalAppHaptics

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExclusionKeywordsSection(
    isExclusionFilterEnabled: Boolean,
    excludedKeywords: Set<String>,
    onToggleExclusionFilter: (Boolean) -> Unit,
    onRemoveExclusionKeyword: (String) -> Unit,
    onResetExclusionKeywords: () -> Unit,
    onOpenAddKeywordDialog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAppHaptics.current

    SectionCard(
        title = "Smart Keyword Exclusion Rules",
        subtitle = "Filter out investment mandates, stock trading, SIPs, and mutual funds from expense totals",
        modifier = modifier,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Keyword Filtering",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Automatically marks non-expense transactions as Non-Budget so savings and food metrics stay clean.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = isExclusionFilterEnabled,
                    onCheckedChange = {
                        haptics.toggle(it)
                        onToggleExclusionFilter(it)
                    },
                )
            }

            if (isExclusionFilterEnabled) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "Active Filter Keywords (${excludedKeywords.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            TextButton(
                                onClick = {
                                    haptics.click()
                                    onResetExclusionKeywords()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.size(4.dp))
                                Text("Reset Defaults", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            excludedKeywords.forEach { keyword ->
                                ExclusionKeywordChip(
                                    keyword = keyword,
                                    onRemove = onRemoveExclusionKeyword,
                                )
                            }
                        }

                        Button(
                            onClick = {
                                haptics.click()
                                onOpenAddKeywordDialog()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Add Exclusion Keyword")
                        }
                    }
                }
            }
        }
    }
}
