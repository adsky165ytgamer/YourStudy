package com.yourstudy.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yourstudy.app.Store
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogTab(pad: PaddingValues) {
    val log by Store.log.collectAsStateWithLifecycle()
    val fmtDate = SimpleDateFormat("EEE d MMM · HH:mm", Locale.getDefault())
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            20.dp, pad.calculateTopPadding() + 16.dp, 20.dp, pad.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Study log", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black) }
        if (log.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), Alignment.Center) {
                    Text(
                        "No sessions yet.\nFinished and ended-early sessions will appear here.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(log) { e ->
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (e.completed) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                ),
            ) {
                Row(Modifier.padding(20.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${e.focusedMs / 60_000} / ${e.plannedMin} min",
                            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                        )
                        Text(fmtDate.format(Date(e.startedAt)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            if (e.completed) "Completed" else "Ended early",
                            style = MaterialTheme.typography.labelLarge,
                        )
                        if (e.breakUsed) Text("Break used", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
