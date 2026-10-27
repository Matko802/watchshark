package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import watchshark.duckdns.org.data.Updater
import watchshark.duckdns.org.ui.Uikit

@Composable
actual fun UpdateScreenBody(
    onDone: () -> Unit,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Version " + Updater.currentVersion(Uikit.ctx),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "The web app always runs the latest version. Just reload the page to update.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onDone) { Text("Back") }
        }
    }
}
