package com.nohate.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nohate.app.data.SecureStore
import java.text.DateFormat
import java.util.Date

@Composable
fun MetricsCard() {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val falsePos = remember { mutableStateOf(store.getMetricFalsePositive()) }
	val hidden = remember { mutableStateOf(store.getMetricHidden()) }
	val deleted = remember { mutableStateOf(store.getMetricDeleted()) }
	val reported = remember { mutableStateOf(store.getMetricReported()) }
	val llmInv = remember { mutableStateOf(store.getMetricLlmInvocations()) }
	val trainedHate = remember { mutableStateOf(store.getMetricTrainedHate()) }
	val trainedSafe = remember { mutableStateOf(store.getMetricTrainedSafe()) }
	val totalProcessed = remember { mutableStateOf(store.getTotalProcessed()) }

	LaunchedEffect(Unit) {
		falsePos.value = store.getMetricFalsePositive()
		hidden.value = store.getMetricHidden()
		deleted.value = store.getMetricDeleted()
		reported.value = store.getMetricReported()
		llmInv.value = store.getMetricLlmInvocations()
		trainedHate.value = store.getMetricTrainedHate()
		trainedSafe.value = store.getMetricTrainedSafe()
		totalProcessed.value = store.getTotalProcessed()
	}

	ElevatedCard(modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
			Text("Model progress", style = MaterialTheme.typography.titleMedium)
			Text("False positives corrected: ${falsePos.value}")
			Text("Hidden: ${hidden.value}, Deleted: ${deleted.value}, Reported: ${reported.value}")
			Text("LLM assists: ${llmInv.value}")
			Text("Total comments processed: ${totalProcessed.value}")
			Text("Trained: hate ${trainedHate.value}, safe ${trainedSafe.value}")
		}
	}
}
