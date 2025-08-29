package com.nohate.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nohate.app.auth.SessionLoginActivity
import com.nohate.app.data.SecureStore

@Composable
fun AccountsScreen() {
	val context = LocalContext.current
	val store = remember { SecureStore(context) }
	val graphKey = "ig_graph"
	val sessionKey = "ig_session"
	val graphEnabled = remember { mutableStateOf(store.isFeatureEnabled(graphKey)) }
	val sessionEnabled = remember { mutableStateOf(store.isFeatureEnabled(sessionKey)) }

	Column(
		modifier = Modifier.fillMaxSize(),
		verticalArrangement = Arrangement.spacedBy(16.dp)
	) {
		Text("Accounts", style = MaterialTheme.typography.titleLarge)

		Text("Instagram (Business/Creator - official)")
		Switch(checked = graphEnabled.value, onCheckedChange = {
			graphEnabled.value = it
			store.setFeatureEnabled(graphKey, it)
			if (it) {
				// TODO launch Custom Tabs for OAuth
			}
		})

		Text("Instagram (Personal - session, ToS may apply)")
		Switch(checked = sessionEnabled.value, onCheckedChange = {
			sessionEnabled.value = it
			store.setFeatureEnabled(sessionKey, it)
			if (it) {
				context.startActivity(Intent(context, SessionLoginActivity::class.java))
			}
		})

		Button(onClick = {
			store.clearProvider("instagram")
		}) { Text("Disconnect & wipe Instagram credentials") }
	}
}
