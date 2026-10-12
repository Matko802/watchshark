package watchshark.duckdns.org.ui.compose.common

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.Comment
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.ui.fmtAge
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import watchshark.duckdns.org.R
import kotlin.math.roundToInt

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

private sealed interface ThreadEntry {
    data class Parent(val comment: Comment) : ThreadEntry
    data class Reply(val comment: Comment) : ThreadEntry
    data class More(val parentId: Long, val hidden: Int, val expanded: Boolean) : ThreadEntry
}

fun LazyListScope.threadedComments(
    comments: List<Comment>,
    expanded: MutableMap<Long, Boolean>,
    onReply: (Comment) -> Unit,
    previewCount: Int = 2
) {
    val ids = comments.map { it.id }.toSet()
    val byParent = comments.filter { it.parentId != null }.groupBy { it.parentId }
    val tops = comments
        .filter { it.parentId == null || it.parentId !in ids }
        .sortedByDescending { it.id }
    val entries = buildList {
        for (parent in tops) {
            add(ThreadEntry.Parent(parent))
            val reps = byParent[parent.id].orEmpty().sortedBy { it.id }
            val showAll = expanded[parent.id] == true
            val visible = if (showAll) reps else reps.take(previewCount)
            for (reply in visible) add(ThreadEntry.Reply(reply))
            if (reps.size > previewCount) {
                add(ThreadEntry.More(parent.id, reps.size - previewCount, showAll))
            }
        }
    }
    items(
        entries,
        key = {
            when (it) {
                is ThreadEntry.Parent -> "p-${it.comment.id}"
                is ThreadEntry.Reply -> "r-${it.comment.id}"
                is ThreadEntry.More -> "m-${it.parentId}"
            }
        }
    ) { entry ->
        when (entry) {
            is ThreadEntry.Parent -> SwipeToReply(
                onReply = { onReply(entry.comment) },
                modifier = Modifier.animateItem()
            ) {
                CommentRow(comment = entry.comment)
            }
            is ThreadEntry.Reply -> SwipeToReply(
                onReply = { onReply(entry.comment) },
                modifier = Modifier.animateItem()
            ) {
                NestedReplyRow(reply = entry.comment)
            }
            is ThreadEntry.More -> TextButton(
                onClick = { expanded[entry.parentId] = !entry.expanded },
                modifier = Modifier
                    .animateItem()
                    .padding(start = 48.dp, top = 0.dp, bottom = 4.dp)
            ) {
                Text(
                    if (entry.expanded) "Hide replies"
                    else "View ${entry.hidden} more ${if (entry.hidden == 1) "reply" else "replies"}"
                )
            }
        }
    }
}

@Composable
private fun NestedReplyRow(
    reply: Comment,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 40.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        AvatarImage(url = reply.avatar, label = reply.username, size = 24)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "@${reply.username} • ${fmtAge(reply.created_at)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(reply.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SwipeToReply(
    onReply: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val buzzCtx = LocalContext.current
    val density = LocalDensity.current
    val maxPx = with(density) { 96.dp.toPx() }
    val threshold = with(density) { 56.dp.toPx() }
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { alpha = (offset.value / maxPx).coerceIn(0f, 1f) },
            contentAlignment = Alignment.CenterStart
        ) {
            Icon(
                painterResource(R.drawable.ic_reply),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp).size(24.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offset.value > threshold) {
                                Haptics.tick(buzzCtx)
                                onReply()
                            }
                            scope.launch { offset.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { _, amount ->
                            scope.launch {
                                offset.snapTo((offset.value + amount).coerceIn(0f, maxPx))
                            }
                        }
                    )
                }
                .background(MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}

@Composable
fun ReplyTargetChip(
    username: String,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                "Replying to @$username",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(
                    painterResource(R.drawable.ic_close),
                    contentDescription = "Cancel reply"
                )
            }
        }
    }
}
