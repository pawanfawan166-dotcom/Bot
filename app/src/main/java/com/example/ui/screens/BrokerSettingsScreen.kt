package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.IndianMarketUtils
import com.example.model.BrokerCredentials
import com.example.model.BrokerFundsSnapshot
import com.example.model.DeskConfig
import com.example.model.IndianBrokerId
import com.example.model.LivePnlSummary
import com.example.ui.components.TradingModeBanner
import com.example.ui.theme.LocalDeskColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BrokerSettingsScreen(
    config: DeskConfig,
    livePnl: LivePnlSummary,
    credentialsMap: Map<IndianBrokerId, BrokerCredentials>,
    realFunds: BrokerFundsSnapshot?,
    isTestingConnection: Boolean,
    testMessage: String?,
    onSelectBroker: (IndianBrokerId) -> Unit,
    onToggleTradingMode: (Boolean) -> Unit,
    onSaveCredentials: (BrokerCredentials) -> Unit,
    onClearCredentials: (IndianBrokerId) -> Unit,
    onTestConnection: (BrokerCredentials) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    val selectedBroker = config.selectedBroker
    val savedForSelected = credentialsMap[selectedBroker] ?: BrokerCredentials(
        brokerId = selectedBroker,
        baseUrl = selectedBroker.defaultBaseUrl
    )

    var brokerMenuExpanded by remember { mutableStateOf(false) }
    var apiKey by remember(selectedBroker, savedForSelected) { mutableStateOf(savedForSelected.apiKey) }
    var apiSecret by remember(selectedBroker, savedForSelected) { mutableStateOf(savedForSelected.apiSecret) }
    var accessToken by remember(selectedBroker, savedForSelected) { mutableStateOf(savedForSelected.accessToken) }
    var requestToken by remember(selectedBroker, savedForSelected) { mutableStateOf(savedForSelected.requestToken) }
    var clientId by remember(selectedBroker, savedForSelected) { mutableStateOf(savedForSelected.clientId) }
    var baseUrl by remember(selectedBroker, savedForSelected) {
        mutableStateOf(savedForSelected.baseUrl.ifBlank { selectedBroker.defaultBaseUrl })
    }

    var showSecret by remember { mutableStateOf(false) }
    var showToken by remember { mutableStateOf(false) }

    fun currentFormCredentials(): BrokerCredentials = BrokerCredentials(
        brokerId = selectedBroker,
        apiKey = apiKey.trim(),
        apiSecret = apiSecret.trim(),
        accessToken = accessToken.trim(),
        requestToken = requestToken.trim(),
        clientId = clientId.trim(),
        baseUrl = baseUrl.trim().ifBlank { selectedBroker.defaultBaseUrl }
    )

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("broker_settings_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.broker_settings_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = desk.bullEmerald,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Encrypted Android Keystore Vault (AES-256-GCM)",
                                style = MaterialTheme.typography.labelSmall,
                                color = desk.bullEmerald
                            )
                        }
                    }
                }
            }

            // Live Trading Mode Switcher + P&L Banner
            item {
                TradingModeBanner(
                    config = config,
                    livePnl = livePnl,
                    credentials = credentialsMap,
                    onToggleTradingMode = onToggleTradingMode,
                    onOpenBrokerSettings = {}
                )
            }

            // Broker Adapter Selector Dropdown
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("1. Select Indian Broker Adapter", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Buy, Sell, Get Positions, Get Orders, and Get Funds route uniformly across all 6 adapters when Real Trading is ON. Paper Mode remains separate.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ExposedDropdownMenuBox(
                            expanded = brokerMenuExpanded,
                            onExpandedChange = { brokerMenuExpanded = !brokerMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedBroker.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Active Broker (Real Mode)") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = brokerMenuExpanded)
                                },
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth()
                                    .testTag("broker_dropdown_field")
                            )
                            ExposedDropdownMenu(
                                expanded = brokerMenuExpanded,
                                onDismissRequest = { brokerMenuExpanded = false }
                            ) {
                                IndianBrokerId.entries.forEach { broker ->
                                    val configured = credentialsMap[broker]?.hasUsableAuth() == true
                                    DropdownMenuItem(
                                        text = {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = broker.displayName,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                    Text(
                                                        text = broker.authHint,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (configured) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = "Credentials saved",
                                                        tint = desk.bullEmerald,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        },
                                        onClick = {
                                            brokerMenuExpanded = false
                                            onSelectBroker(broker)
                                        },
                                        modifier = Modifier.testTag("broker_option_${broker.code}")
                                    )
                                }
                            }
                        }

                        // Quick Vault Status Chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IndianBrokerId.entries.forEach { broker ->
                                val ready = credentialsMap[broker]?.hasUsableAuth() == true
                                FilterChip(
                                    selected = selectedBroker == broker,
                                    onClick = { onSelectBroker(broker) },
                                    leadingIcon = if (ready) {
                                        {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Saved",
                                                tint = desk.bullEmerald,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    } else null,
                                    label = {
                                        Text(
                                            text = broker.displayName.substringBefore(" ("),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Dynamic Broker API Credentials Form
            item {
                Card(
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, desk.borderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "2. ${selectedBroker.displayName} API Credentials",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (savedForSelected.hasUsableAuth()) desk.bullContainer else desk.amberContainer
                            ) {
                                Text(
                                    text = if (savedForSelected.hasUsableAuth()) "SAVED" else "NOT SET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (savedForSelected.hasUsableAuth()) desk.bullEmerald else desk.amberSaffron,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = selectedBroker.authHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = desk.dataSky
                        )

                        if (selectedBroker.requiresClientId || selectedBroker == IndianBrokerId.GENERIC) {
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it },
                                label = {
                                    Text(
                                        if (selectedBroker == IndianBrokerId.GENERIC) "Client ID (optional)"
                                        else "${selectedBroker.displayName} Client ID (required)"
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("broker_client_id_input")
                            )
                        }

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = {
                                Text(
                                    when (selectedBroker) {
                                        IndianBrokerId.FYERS -> "App ID / API Key"
                                        IndianBrokerId.UPSTOX -> "API Key (optional with token)"
                                        else -> "API Key"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("broker_api_key_input")
                        )

                        if (selectedBroker.requiresApiSecret) {
                            OutlinedTextField(
                                value = apiSecret,
                                onValueChange = { apiSecret = it },
                                label = { Text("API Secret") },
                                singleLine = true,
                                visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { showSecret = !showSecret }) {
                                        Icon(
                                            imageVector = if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (showSecret) "Hide API Secret" else "Show API Secret"
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("broker_api_secret_input")
                            )
                        }

                        OutlinedTextField(
                            value = accessToken,
                            onValueChange = { accessToken = it },
                            label = { Text("Access Token (or JWT Session Token)") },
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        imageVector = if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showToken) "Hide Access Token" else "Show Access Token"
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("broker_access_token_input")
                        )

                        OutlinedTextField(
                            value = requestToken,
                            onValueChange = { requestToken = it },
                            label = { Text("Request Token (optional / OAuth handshake)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("broker_request_token_input")
                        )

                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            enabled = selectedBroker.supportsCustomBaseUrl,
                            label = {
                                Text(
                                    if (selectedBroker.supportsCustomBaseUrl) {
                                        "Custom Broker Base URL (e.g. https://api.mybroker.in/v1)"
                                    } else {
                                        "REST Endpoint Base URL"
                                    }
                                )
                            },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("broker_base_url_input")
                        )

                        // Save / Test / Clear Buttons (min 48dp height)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onSaveCredentials(currentFormCredentials()) },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("broker_save_creds_button")
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Save Securely")
                            }

                            OutlinedButton(
                                onClick = { onTestConnection(currentFormCredentials()) },
                                enabled = !isTestingConnection,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("broker_test_conn_button")
                            ) {
                                if (isTestingConnection) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.NetworkCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Test API")
                            }
                        }

                        if (savedForSelected.hasUsableAuth() || apiKey.isNotBlank() || accessToken.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    apiKey = ""
                                    apiSecret = ""
                                    accessToken = ""
                                    requestToken = ""
                                    clientId = ""
                                    baseUrl = selectedBroker.defaultBaseUrl
                                    onClearCredentials(selectedBroker)
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = desk.stopCrimson),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp)
                                    .testTag("broker_clear_creds_button")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Clear ${selectedBroker.displayName} Credentials")
                            }
                        }

                        if (!testMessage.isNullOrBlank()) {
                            val ok = testMessage.startsWith("Connected")
                            Surface(
                                color = if (ok) desk.bullContainer else desk.amberContainer,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, (if (ok) desk.bullEmerald else desk.amberSaffron).copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = testMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (ok) desk.bullEmerald else desk.amberSaffron,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        if (realFunds != null && realFunds.brokerId == selectedBroker) {
                            Surface(
                                color = desk.cardElevated,
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, desk.borderSubtle),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Live Available Cash", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(IndianMarketUtils.inr(realFunds.availableCash), style = MaterialTheme.typography.titleMedium, color = desk.bullEmerald)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Used Margin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(IndianMarketUtils.inr(realFunds.usedMargin), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
