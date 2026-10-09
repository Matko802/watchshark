package watchshark.duckdns.org.ui.compose.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.Comment
import watchshark.duckdns.org.ui.fmtAge
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import watchshark.duckdns.org.R

@Composable
fun AvatarImage(
    url: String?,
    label: String,
    size: Int = 36,
    modifier: Modifier = Modifier
) {
    val full = ApiClient.fullUrl(url)
    if (full != null) {
        AsyncImage(
            model = full,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size.dp).clip(CircleShape)
        )
    } else {
        Icon(
            painterResource(R.drawable.ic_person),
            contentDescription = label,
            modifier = modifier.size(size.dp)
        )
    }
}

@Composable
fun ErrorRetry(
    title: String,
    detail: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(24.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (!detail.isNullOrEmpty()) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
fun CommentRow(
    comment: Comment,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AvatarImage(url = comment.avatar, label = comment.username, size = 32)
        Column {
            Text(
                "@${comment.username} • ${fmtAge(comment.created_at)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

object Dimens {
    val BottomBar = 100.dp
    val GridMin = 320.dp
    val PillMax = 360.dp
}
