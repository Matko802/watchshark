package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import watchshark.duckdns.org.data.Video

@Composable
fun MusicScreen(
    onOpenVideo: (Video) -> Unit,
    onOpenChannel: (String) -> Unit,
    query: String = "",
    reselectTick: Int = 0,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxSize()) {
        Text(
            "Music",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        HomeScreen(
            onOpenVideo = onOpenVideo,
            onOpenChannel = onOpenChannel,
            query = query,
            reselectTick = reselectTick,
            kind = "music"
        )
    }
}
