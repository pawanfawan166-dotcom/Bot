package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.IndianMarketUtils
import com.example.model.AnalystReport
import com.example.model.InsidersReport
import com.example.model.NewsReport
import com.example.ui.theme.LocalDeskColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IntelScreen(
    symbol: String,
    analystBasis: String,
    newsReport: NewsReport?,
    analystReport: AnalystReport?,
    insidersReport: InsidersReport?,
    followedInvestors: List<String>,
    isLoading: Boolean,
    onLoadSymbol: (String) -> Unit,
    onChangeBasis: (String) -> Unit,
    onLoadAllDeals: () -> Unit,
    onAddInvestor: (String) -> Unit,
    onRemoveInvestor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val desk = LocalDeskColors.current
    var symbolInput by remember(symbol) { mutableStateOf(symbol) }
    var activeTab by remember { mutableIntStateOf(0) }
    var importantOnly by remember { mutableStateOf(false) }
    var hideRoutine by remember { mutableStateOf(false) }
    var investorInput by remember { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 860.dp)
        ) {
            // Top Symbol Lookup + Sub-Tabs
            Surface(color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = symbolInput,
                            onValueChange = { symbolInput = it.uppercase() },
                            label = { Text("NSE Symbol") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("intel_symbol_input")
                        )
                        Button(
                            onClick = { onLoadSymbol(symbolInput) },
                            modifier = Modifier.testTag("intel_fetch_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Load")
                        }
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("RELIANCE", "HDFCBANK", "TCS", "INFY").forEach { quick ->
                            FilterChip(
                                selected = symbol == quick,
                                onClick = {
                                    symbolInput = quick
                                    onLoadSymbol(quick)
                                },
                                label = { Text(quick) }
                            )
                        }
                    }

                    TabRow(selectedTabIndex = activeTab) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("News") },
                            icon = { Icon(Icons.Default.Newspaper, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Results XBRL") },
                            icon = { Icon(Icons.Default.QueryStats, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("Insiders & Deals") },
                            icon = { Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            when (activeTab) {
                0 -> {
                    // News & Corporate Filings
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        newsReport?.let { rep ->
                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
                                    border = BorderStroke(1.dp, desk.borderSubtle)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(rep.symbol, style = MaterialTheme.typography.titleLarge)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                rep.changePct?.let { chg ->
                                                    val color = if (chg >= 0) desk.bullEmerald else desk.stopCrimson
                                                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(6.dp)) {
                                                        Text(
                                                            text = "${if (chg >= 0) "+" else ""}${"%.2f".format(chg)}%",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = color,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                                if (rep.movingFlag) {
                                                    Surface(color = desk.amberContainer, shape = RoundedCornerShape(6.dp)) {
                                                        Text(
                                                            text = "MOVING >=3%",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = desk.amberSaffron,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            FilterChip(
                                                selected = importantOnly,
                                                onClick = { importantOnly = !importantOnly },
                                                label = { Text("Important Only (!)") },
                                                modifier = Modifier.testTag("news_important_chip")
                                            )
                                            FilterChip(
                                                selected = hideRoutine,
                                                onClick = { hideRoutine = !hideRoutine },
                                                label = { Text("Hide Routine") },
                                                modifier = Modifier.testTag("news_hide_routine_chip")
                                            )
                                        }
                                    }
                                }
                            }

                            val filtered = rep.items.filter { item ->
                                (!importantOnly || item.important) && (!hideRoutine || !item.routine)
                            }
                            items(filtered, key = { it.id }) { item ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(
                                        1.dp,
                                        if (item.important) desk.amberSaffron.copy(alpha = 0.6f) else desk.borderSubtle
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                if (item.important) {
                                                    Surface(color = desk.amberContainer, shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            text = "!",
                                                            color = desk.amberSaffron,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                item.tags.forEach { tag ->
                                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            text = tag,
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = desk.dataSky,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = item.source,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                                        if (item.summary.isNotBlank() && item.summary != item.title) {
                                            Text(
                                                text = item.summary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 4,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = item.timestamp,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } ?: item {
                            EmptyIntelCard("No news feed loaded for $symbol. Tap Load or switch to Live Public Feeds.")
                        }
                    }
                }

                1 -> {
                    // XBRL Financial Analyst
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("consolidated", "standalone").forEach { b ->
                                    FilterChip(
                                        selected = analystBasis == b,
                                        onClick = { onChangeBasis(b) },
                                        label = { Text(b.replaceFirstChar { it.uppercase() }) }
                                    )
                                }
                            }
                        }

                        analystReport?.let { rep ->
                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
                                    border = BorderStroke(1.dp, desk.borderSubtle)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = rep.entityName.ifBlank { rep.symbol },
                                                style = MaterialTheme.typography.titleLarge,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    text = rep.format.uppercase(),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = desk.dataSky,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        rep.basisNote?.let { note ->
                                            Text(
                                                text = note,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = desk.amberSaffron
                                            )
                                        }

                                        // TTM Summary
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rep.ttmRevenueCr?.let { rev ->
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("TTM Revenue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("₹${"%.0f".format(rev)} Cr", style = MaterialTheme.typography.titleMedium)
                                                }
                                            }
                                            rep.ttmNiiCr?.let { nii ->
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("TTM NII", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("₹${"%.0f".format(nii)} Cr", style = MaterialTheme.typography.titleMedium, color = desk.dataSky)
                                                }
                                            }
                                            rep.ttmProfitCr?.let { pat ->
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("TTM PAT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("₹${"%.0f".format(pat)} Cr", style = MaterialTheme.typography.titleMedium, color = desk.bullEmerald)
                                                }
                                            }
                                            rep.ttmEps?.let { eps ->
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text("TTM EPS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("₹${"%.2f".format(eps)}", style = MaterialTheme.typography.titleMedium)
                                                }
                                            }
                                        }

                                        rep.upcomingEvent?.let { ev ->
                                            Surface(color = desk.amberContainer, shape = MaterialTheme.shapes.small) {
                                                Text(
                                                    text = "Upcoming Board Meeting: $ev",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = desk.amberSaffron,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            if (rep.whatChanged.isNotEmpty()) {
                                item {
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, desk.borderSubtle)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("What Changed (XBRL Insights)", style = MaterialTheme.typography.titleMedium)
                                            rep.whatChanged.forEach { line ->
                                                val warn = line.contains("Other income", true) || line.contains("Exceptional", true)
                                                Row(
                                                    verticalAlignment = Alignment.Top,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = if (warn) desk.amberSaffron else desk.bullEmerald,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = line,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = if (warn) desk.amberSaffron else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Quarter-by-Quarter Table
                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, desk.borderSubtle)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Quarter-by-Quarter (₹ Crore)", style = MaterialTheme.typography.titleMedium)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                        ) {
                                            Column {
                                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                                    TableCell("End", 96.dp, bold = true)
                                                    TableCell("Rev Cr", 84.dp, bold = true)
                                                    TableCell(if (rep.format == "bank") "NII Cr" else "EBITDA", 84.dp, bold = true)
                                                    TableCell(if (rep.format == "bank") "Prov Cr" else "OPM%", 76.dp, bold = true)
                                                    TableCell("PAT Cr", 84.dp, bold = true)
                                                    TableCell("NPM%", 68.dp, bold = true)
                                                    TableCell("EPS", 78.dp, bold = true)
                                                }
                                                HorizontalDivider(color = desk.borderSubtle)
                                                rep.quarters.forEach { q ->
                                                    Row(modifier = Modifier.padding(vertical = 6.dp)) {
                                                        TableCell(q.end, 96.dp)
                                                        TableCell(q.revenueCr?.let { "%.0f".format(it) } ?: "-", 84.dp)
                                                        val col3 = if (rep.format == "bank") q.niiCr else q.ebitdaCr
                                                        TableCell(col3?.let { "%.0f".format(it) } ?: "-", 84.dp)
                                                        val col4 = if (rep.format == "bank") q.provisionsCr?.let { "%.0f".format(it) } else q.operatingMarginPct?.let { "%.1f%%".format(it) }
                                                        TableCell(col4 ?: "-", 76.dp)
                                                        TableCell(q.netProfitCr?.let { "%.0f".format(it) } ?: "-", 84.dp)
                                                        TableCell(q.netMarginPct?.let { "%.1f%%".format(it) } ?: "-", 68.dp)
                                                        val epsVal = q.epsAdjustedTodayShares ?: q.epsDiluted
                                                        val epsText = epsVal?.let { "%.2f%s".format(it, if (q.epsAdjusted) " adj" else "") } ?: "-"
                                                        TableCell(epsText, 78.dp)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } ?: item {
                            EmptyIntelCard("No NSE XBRL filings bundled for $symbol. Offline fixtures cover RELIANCE & HDFCBANK; toggle Live Public Feeds in Settings or Agents Hub.")
                        }
                    }
                }

                else -> {
                    // Insiders & Bulk/Block Deals
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = desk.cardElevated),
                                border = BorderStroke(1.dp, desk.borderSubtle)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Followed Big Investors", style = MaterialTheme.typography.titleMedium)
                                        OutlinedButton(
                                            onClick = onLoadAllDeals,
                                            modifier = Modifier.testTag("insiders_all_deals_button")
                                        ) {
                                            Text("All Today's Deals")
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        followedInvestors.forEach { inv ->
                                            InputChip(
                                                selected = true,
                                                onClick = { onRemoveInvestor(inv) },
                                                label = { Text(inv) },
                                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove $inv", modifier = Modifier.size(14.dp)) }
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = investorInput,
                                            onValueChange = { investorInput = it },
                                            label = { Text("Follow Investor Name") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Button(onClick = {
                                            if (investorInput.isNotBlank()) {
                                                onAddInvestor(investorInput)
                                                investorInput = ""
                                            }
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = "Add Investor")
                                        }
                                    }
                                }
                            }
                        }

                        insidersReport?.let { rep ->
                            if (rep.pitFilings.isNotEmpty()) {
                                item {
                                    Text("SEBI PIT Insider Disclosures (${rep.pitFilings.size})", style = MaterialTheme.typography.titleMedium)
                                }
                                items(rep.pitFilings.take(25)) { f ->
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(1.dp, if (f.isLate) desk.stopCrimson.copy(alpha = 0.6f) else desk.borderSubtle)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${f.symbol} • ${f.transactionType}", style = MaterialTheme.typography.titleMedium)
                                                if (f.isLate) {
                                                    Surface(color = desk.stopContainer, shape = RoundedCornerShape(6.dp)) {
                                                        Text(
                                                            text = "LATE (${f.lagTradingDays}d >4d)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = desk.stopCrimson,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                } else {
                                                    Text(
                                                        text = "Lag ${f.lagTradingDays ?: 0}d",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = desk.bullEmerald
                                                    )
                                                }
                                            }
                                            Text(f.acquirer, style = MaterialTheme.typography.bodyMedium)
                                            Text(
                                                text = "${f.category} • ${"%.0f".format(f.shares)} sh (${IndianMarketUtils.money(f.valueInr)}) • Tx ${f.txDate} -> Intimated ${f.intimationDate}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            if (rep.deals.isNotEmpty()) {
                                item {
                                    Text("NSE Bulk & Block Deals (${rep.deals.size})", style = MaterialTheme.typography.titleMedium)
                                }
                                items(rep.deals.take(40)) { d ->
                                    Card(
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                d.matchedFollowInvestor != null -> desk.bullEmerald
                                                d.sameDayBothSides -> desk.amberSaffron
                                                else -> desk.borderSubtle
                                            }
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${d.symbol} • ${d.dealType} ${d.side}", style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    text = "₹${"%.2f".format(d.valueCr)} Cr",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = if (d.side.equals("BUY", true)) desk.bullEmerald else desk.stopCrimson
                                                )
                                            }
                                            Text(d.clientName, style = MaterialTheme.typography.bodyMedium)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "${d.date} • ${d.quantity} sh @ ₹${d.price}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                d.matchedFollowInvestor?.let { m ->
                                                    Surface(color = desk.bullContainer, shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            text = "MATCHED: $m",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = desk.bullEmerald,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                if (d.sameDayBothSides) {
                                                    Surface(color = desk.amberContainer, shape = RoundedCornerShape(4.dp)) {
                                                        Text(
                                                            text = "CHURN (Buy+Sell)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = desk.amberSaffron,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
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
            }
        }
    }
}

@Composable
private fun TableCell(text: String, width: androidx.compose.ui.unit.Dp, bold: Boolean = false) {
    Text(
        text = text,
        style = if (bold) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.width(width),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun EmptyIntelCard(message: String) {
    val desk = LocalDeskColors.current
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, desk.borderSubtle)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}
