package watchshark.duckdns.org.ui.compose

import watchshark.duckdns.org.ui.BackHandlerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import watchshark.duckdns.org.ui.PlatformLocal
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import watchshark.duckdns.org.*
import watchshark.duckdns.org.Res
import watchshark.duckdns.org.data.ApiClient
import watchshark.duckdns.org.data.AutoQuality
import watchshark.duckdns.org.data.Comment
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.data.PlayerManager
import watchshark.duckdns.org.data.PPlayer
import watchshark.duckdns.org.data.Video
import watchshark.duckdns.org.ui.compose.player.BufferingSpinner
import watchshark.duckdns.org.ui.compose.player.CenterPlayButton
import watchshark.duckdns.org.ui.compose.player.ScrubBar
import watchshark.duckdns.org.ui.compose.player.VideoSurface
import watchshark.duckdns.org.ui.compose.common.ReplyTargetChip
import watchshark.duckdns.org.ui.compose.common.threadedComments
import watchshark.duckdns.org.ui.compose.player.dynRenditionUrl
import watchshark.duckdns.org.ui.compose.player.fmtPlayerTime
import watchshark.duckdns.org.ui.compose.player.rememberPlayerUiState
import watchshark.duckdns.org.ui.compose.theme.AppMotion
import watchshark.duckdns.org.ui.fmtNum
import watchshark.duckdns.org.ui.httpErrorMessage
import watchshark.duckdns.org.ui.optLong
import watchshark.duckdns.org.ui.optStr
import watchshark.duckdns.org.ui.toast

private fun parseWheelVideo(o: JsonObject): Video? {
    return try {
        val id = o.optLong("id") ?: 0
        if (id == 0L) return null
        Video(
            id = id,
            title = o.optStr("title") ?: "",
            username = o.optStr("username") ?: "",
            userId = (o.optLong("user_id") ?: 0L),
            src = o.optStr("src") ?: "",
            thumbnail = o.optStr("thumbnail"),
            views = (o.optLong("views") ?: 0L),
            likes = o.optLong("likes") ?: 0L,
            liked = o.get("liked")?.let { (it as? JsonPrimitive)?.content?.toBooleanStrictOrNull() } == true,
            followers = o.optLong("followers") ?: 0L,
            following = o.get("following")?.let { (it as? JsonPrimitive)?.content?.toBooleanStrictOrNull() } == true,
            created_at = o.optStr("created_at") ?: "",
            avatar = o.optStr("avatar"),
            kind = o.optStr("kind") ?: "wheel",
            renditions = (o["renditions"] as? JsonObject)?.let { ro ->
                val m = mutableMapOf<String, String>()
                for ((k, v) in ro) {
                    (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { m[k] = it.content }
                }
                if (m.isEmpty()) null else m
            },
            orientation = run {
                (o["orientation"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content
            },
        )
    } catch (_: Exception) {
        null
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WheelsScreen(
    onOpenVideo: (Long) -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    reselectTick: Int = 0,
    onOverlayChange: (Boolean) -> Unit = {},
) {
    val context = PlatformLocal.app
    val scope = rememberCoroutineScope()
    val videos = remember { mutableStateListOf<Video>() }
    val seen = remember { mutableSetOf<Long>() }
    val qualityOverride = remember { mutableStateMapOf<Long, String>() }
    val autoKeys = remember { mutableMapOf<Long, String>() }
    val readyMap = remember { mutableStateMapOf<Int, Boolean>() }
    var loading by remember { mutableStateOf(false) }
    var exhausted by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf(false) }
    var netError by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var qualityFor by remember { mutableStateOf<Video?>(null) }
    var meId by remember { mutableStateOf<Long?>(null) }
    var commentsFor by remember { mutableStateOf<Video?>(null) }
    val commentCounts = remember { mutableStateMapOf<Long, Int>() }
    var lastCommentsId by remember { mutableStateOf(0L) }

    fun loadCommentCount(id: Long, force: Boolean = false) {
        if (!force && commentCounts.containsKey(id)) return
        scope.launch {
            try {
                commentCounts[id] = ApiClient.api.videoDetail(id).comments.orEmpty().size
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            meId = ApiClient.api.me().user?.id
        } catch (_: Exception) {
        }
    }

    fun fullUrl(path: String?): String? = ApiClient.fullUrl(path)

    fun srcFor(v: Video): String? {
        val override = qualityOverride[v.id]
        val url = when {
            override != null -> v.renditions?.get(override)
                ?: override.let { dynRenditionUrl(v.src, it) } ?: v.src
            else -> {
                val key = AutoQuality.pickReadyKey(v)
                autoKeys[v.id] = key
                AutoQuality.readyUrl(v, key)
                    ?: v.renditions?.get("720p")
                    ?: v.renditions?.get("480p")
                    ?: v.renditions?.get("360p")
                    ?: v.src
            }
        }
        return fullUrl(url)
    }

    val player = remember { PlayerManager.get(context) }

    remember {
        player.repeatOne = true
        player.listener = object : watchshark.duckdns.org.data.PPlayerListener {
            override fun onTransition(index: Int) {
                readyMap.remove(index)
            }

            override fun onStateChanged(
                index: Int,
                ready: Boolean,
                buffering: Boolean,
                playWhenReady: Boolean,
            ) {
                if (ready) {
                    readyMap[index] = true
                } else if (buffering && playWhenReady && readyMap[index] == true) {
                    val vid = videos.getOrNull(index) ?: return
                    if (qualityOverride.containsKey(vid.id)) return
                    val want = AutoQuality.lowerReadyKey(vid, autoKeys[vid.id]) ?: return
                    val url = AutoQuality.readyUrl(vid, want) ?: return
                    if (!AutoQuality.tryBeginSwitch(AutoQuality.DOWNGRADE_GAP_MS)) return
                    autoKeys[vid.id] = want
                    readyMap.remove(index)
                    val time = player.positionMs.coerceAtLeast(0)
                    val playing = player.playing
                    player.replace(index, url)
                    player.seekToItem(index, time)
                    if (playing) player.play()
                }
            }
        }
        true
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, player) {
        val obs = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> player.playWhenReady = false
                Lifecycle.Event.ON_RESUME -> player.playWhenReady = true
                else -> {}
            }
        }
        lifecycle.addObserver(obs)
        onDispose {
            lifecycle.removeObserver(obs)
            try {
                player.stop()
                player.clear()
                player.listener = null
            } catch (_: Exception) {
            }
        }
    }

    fun loadMore() {
        if (loading || exhausted) return
        loading = true
        netError = false
        error = null
        scope.launch {
            try {
                var added = 0
                var failed = 0
                repeat(5) {
                    if (exhausted) return@repeat
                    try {
                        val q = if (seen.isEmpty()) null
                        else seen.toList().takeLast(128).joinToString(",")
                        val res = ApiClient.api.wheels(q)
                        val vid = (res["video"] as? JsonObject)?.let { parseWheelVideo(it) }
                        if (vid == null || seen.contains(vid.id)) return@repeat
                        seen.add(vid.id)
                        val url = srcFor(vid) ?: return@repeat
                        videos.add(vid)
                        player.append(url)
                        added++
                    } catch (_: Exception) {
                        failed++
                    }
                }
                if (added == 0) {
                    if (failed > 0 && !exhausted) {
                        netError = true
                    } else {
                        exhausted = true
                        if (videos.isEmpty()) error = "No wheels yet"
                    }
                } else if (!prepared) {
                    player.seekToItem(0, 0)
                    prepared = true
                    player.playWhenReady = true
                }
            } finally {
                loading = false
            }
        }
    }

    fun autoUpgradeCurrent(position: Int) {
        val vid = videos.getOrNull(position) ?: return
        if (qualityOverride.containsKey(vid.id)) return
        if (position >= player.itemCount) return
        val want = AutoQuality.pickReadyKey(vid)
        if (AutoQuality.rungIndex(want) <= AutoQuality.rungIndex(autoKeys[vid.id])) return
        val url = AutoQuality.readyUrl(vid, want) ?: return
        if (!AutoQuality.tryBeginSwitch(AutoQuality.UPGRADE_GAP_MS)) return
        autoKeys[vid.id] = want
        val wasIndex = player.currentIndex
        val time = player.positionMs.coerceAtLeast(0)
        val playing = player.playing
        player.replace(position, url)
        if (wasIndex == position) {
            player.seekToItem(position, time)
            if (playing) player.play()
        }
    }

    fun watchAgain() {
        seen.clear()
        videos.clear()
        autoKeys.clear()
        readyMap.clear()
        exhausted = false
        prepared = false
        player.stop()
        player.clear()
        player.repeatOne = true
        loadMore()
    }

    LaunchedEffect(Unit) { loadMore() }


    if (videos.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator()
                netError -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Couldn't load wheels",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Button(
                        onClick = { watchAgain() },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Retry") }
                }
                else -> Text(error ?: "No wheels yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val pageCount = videos.size + if (exhausted && videos.isNotEmpty()) 1 else 0
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }
    val currentPage = pagerState.currentPage

    LaunchedEffect(reselectTick) {
        if (reselectTick == 0) return@LaunchedEffect
        try {
            pagerState.scrollToPage(0)
        } catch (_: Exception) {
        }
    }

    val ui = rememberPlayerUiState(player)

    LaunchedEffect(currentPage) {
        if (currentPage < videos.size && currentPage < player.itemCount) {
            if (player.currentIndex != currentPage) {
                player.seekToItem(currentPage, 0)
            }
            player.playWhenReady = true
            autoUpgradeCurrent(currentPage)
        }
        videos.getOrNull(currentPage)?.let { loadCommentCount(it.id) }
        if (currentPage >= videos.size - 3) loadMore()
    }


    VerticalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentPadding = PaddingValues(bottom = 100.dp),
        beyondViewportPageCount = 1,
    ) { page ->
        if (page >= videos.size) {

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("You're all caught up", style = MaterialTheme.typography.titleLarge)
                    Button(
                        onClick = {
                            watchAgain()
                            scope.launch { pagerState.scrollToPage(0) }
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Watch again") }
                }
            }
            return@VerticalPager
        }
        val vid = videos[page]
        val isCurrent = page == currentPage

        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
        val offsetAbs = pageOffset.coerceIn(-1f, 1f).let { kotlin.math.abs(it) }
        var heartBurst by remember(vid.id) { mutableStateOf(false) }
        fun doLike() {


            val idx = videos.indexOfFirst { it.id == vid.id }
            if (idx < 0) return
            val cur = videos[idx]
            videos[idx] = cur.copy(
                liked = !cur.liked,
                likes = (cur.likes + if (cur.liked) -1 else 1).coerceAtLeast(0),
            )
            scope.launch {
                try {
                    ApiClient.api.like(vid.id)
                } catch (e: Exception) {
                    val i = videos.indexOfFirst { it.id == vid.id }
                    if (i >= 0) videos[i] = cur
                    context.toast(httpErrorMessage(e))
                }
            }
        }
        fun doFollow() {

            if (vid.userId == 0L) return
            val myId = meId
            if (myId != null && myId == vid.userId) return
            val idx = videos.indexOfFirst { it.id == vid.id }
            if (idx < 0) return
            val cur = videos[idx]
            videos[idx] = cur.copy(following = !cur.following)
            scope.launch {
                try {
                    ApiClient.api.follow(vid.userId)
                } catch (e: Exception) {
                    val i = videos.indexOfFirst { it.id == vid.id }
                    if (i >= 0) videos[i] = cur
                    context.toast(httpErrorMessage(e))
                }
            }
        }
        LaunchedEffect(heartBurst) {
            if (heartBurst) {
                delay(800)
                heartBurst = false
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val scale = (1f - 0.12f * offsetAbs).coerceIn(0.88f, 1f)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - 0.35f * offsetAbs).coerceIn(0.65f, 1f)
                }
                .background(Color.Black)
                .pointerInput(vid.id) {
                    detectTapGestures(
                        onTap = { if (player.playing) player.pause() else player.play() },
                        onDoubleTap = {
                            heartBurst = true
                            if (!vid.liked) doLike()
                        },
                    )
                },
        ) {
            VideoSurface(
                player = if (isCurrent) player else null,
                modifier = Modifier.fillMaxSize(),
            )
            if (readyMap[page] != true) {
                AsyncImage(
                    model = fullUrl(vid.thumbnail),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC000000)),
                        ),
                    )
                    .padding(top = 96.dp),
            )

            AnimatedVisibility(
                visible = heartBurst,
                enter = scaleIn(AppMotion.fastSpatial, initialScale = 0.4f) + fadeIn(),
                exit = scaleOut(targetScale = 1.4f) + fadeOut(),
                modifier = Modifier.align(Alignment.Center),
                label = "heartBurst",
            ) {
                Icon(
                    painterResource(Res.drawable.ic_favorite_fill),
                    contentDescription = null,
                    tint = Color(0xFFFF5C5C),
                    modifier = Modifier.size(96.dp),
                )
            }

            CenterPlayButton(
                visible = isCurrent && !ui.isPlaying && readyMap[page] == true,
                playing = false,
                onToggle = { player.play() },
                modifier = Modifier.align(Alignment.Center),
            )
            BufferingSpinner(
                visible = isCurrent && readyMap[page] != true,
                modifier = Modifier.align(Alignment.Center),
            )

            AnimatedVisibility(
                visible = isCurrent,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 4 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomStart),
                label = "captions",
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .padding(start = 12.dp, bottom = 36.dp, end = 8.dp),
            ) {
                Text(
                    vid.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                )
                Text(
                    "@${vid.username} • ${fmtNum(vid.views)} views",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFDDDDDD),
                    modifier = Modifier.clickable { onOpenChannel(vid.username) },
                )
            }
            }

            AnimatedVisibility(
                visible = isCurrent,
                enter = fadeIn() + androidx.compose.animation.slideInVertically { it / 4 },
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomEnd),
                label = "actions",
            ) {
            Column(
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {

                RailPillButton(onClick = { doLike() }, description = "Like") {
                    Icon(
                        painterResource(
                            if (vid.liked) Res.drawable.ic_favorite_fill
                            else Res.drawable.ic_favorite_outline,
                        ),
                        contentDescription = "Like",
                    )
                }
                Text(fmtNum(vid.likes), color = Color.White, style = MaterialTheme.typography.labelSmall)

                val myId = meId
                if (myId == null || myId != vid.userId) {
                RailPillButton(onClick = { doFollow() }, description = "Follow") {
                    Icon(
                        painterResource(
                            if (vid.following) Res.drawable.ic_how_to_reg
                            else Res.drawable.ic_person_add,
                        ),
                        contentDescription = "Follow",
                    )
                }
                }
                RailPillButton(onClick = { commentsFor = vid }, description = "Comments") {
                    Icon(
                        painterResource(Res.drawable.ic_chat),
                        contentDescription = "Comments",
                    )
                }
                Text(
                    fmtNum((commentCounts[vid.id] ?: 0).toLong()),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )

                RailPillButton(onClick = { qualityFor = vid }, description = "Quality") {
                    Icon(
                        painterResource(Res.drawable.ic_settings),
                        contentDescription = "Quality",
                    )
                }
                Text(
                    qualityOverride[vid.id] ?: "Auto",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )

                Text(
                    "${fmtPlayerTime(ui.positionMs)} / ${fmtPlayerTime(ui.durationMs)}",
                    color = Color(0xFFDDDDDD),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            }
            if (isCurrent) {
                val wheelFraction = if (ui.durationMs > 0) {
                    (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f)
                } else 0f
                val wheelBuffered = if (ui.durationMs > 0) {
                    (ui.bufferedMs.toFloat() / ui.durationMs).coerceIn(0f, 1f)
                } else 0f
                ScrubBar(
                    fraction = wheelFraction,
                    bufferedFraction = wheelBuffered,
                    controlsActive = false,
                    enabled = ui.durationMs > 0,
                    waveOn = ui.isPlaying,
                    onScrub = {},
                    onSeek = {
                        if (ui.durationMs > 0) {
                            player.seekToItem(
                                player.currentIndex,
                                (it * ui.durationMs).toLong()
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                )
            }
        }
    }


    qualityFor?.let { v ->
        val options = buildList {
            add("Auto")
            if (v.renditions?.containsKey("720p") == true || dynRenditionUrl(v.src, "720p") != null) add("720p")
            if (v.renditions?.containsKey("480p") == true || dynRenditionUrl(v.src, "480p") != null) add("480p")
            if (v.renditions?.containsKey("360p") == true || dynRenditionUrl(v.src, "360p") != null) add("360p")
            add("Source")
        }
        DropdownMenu(expanded = true, onDismissRequest = { qualityFor = null }) {
            options.forEach { label ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        qualityFor = null
                        val key = when {
                            label.startsWith("720p") -> "720p"
                            label.startsWith("480p") -> "480p"
                            label.startsWith("360p") -> "360p"
                            else -> null
                        }
                        if (key == null) qualityOverride.remove(v.id)
                        else qualityOverride[v.id] = key
                        val pos = videos.indexOfFirst { it.id == v.id }
                        if (pos < 0) return@DropdownMenuItem
                        val url = fullUrl(srcFor(v) ?: v.src) ?: return@DropdownMenuItem
                        val wasIndex = player.currentIndex
                        val time = player.positionMs
                        val playing = player.playing
                        player.replace(pos, url)
                        if (wasIndex == pos) {
                            player.seekToItem(pos, time)
                            if (playing) player.play()
                        }
                    },
                )
            }
        }
    }

    val currentUnready = currentPage < videos.size && readyMap[currentPage] != true
    if (loading && videos.isNotEmpty() && !currentUnready) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        }
    }

    BackHandlerCompat(enabled = commentsFor != null) { commentsFor = null }
    LaunchedEffect(commentsFor) {
        onOverlayChange(commentsFor != null)
        val open = commentsFor
        if (open != null) {
            lastCommentsId = open.id
            loadCommentCount(open.id)
        } else if (lastCommentsId > 0L) {
            loadCommentCount(lastCommentsId, force = true)
        }
    }
    AnimatedVisibility(
        visible = commentsFor != null,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
        label = "commentsDrawer",
    ) {
        commentsFor?.let { v ->
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { commentsFor = null },
                        ),
                )
                WheelCommentsPanel(
                    video = v,
                    onClose = { commentsFor = null },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.88f),
                )
            }
        }
    }
}


@Composable
private fun RailPillButton(
    onClick: () -> Unit,
    description: String,
    icon: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val buzzCtx = PlatformLocal.app
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AppMotion.pressSpring,
        label = "railPress",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = {
                    Haptics.tick(buzzCtx)
                    onClick()
                },
            ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            icon()
        }
    }
}

@Composable
private fun WheelCommentsPanel(
    video: Video,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var list by remember(video.id) { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember(video.id) { mutableStateOf(true) }
    var failed by remember(video.id) { mutableStateOf(false) }
    var draft by remember(video.id) { mutableStateOf("") }
    var posting by remember(video.id) { mutableStateOf(false) }
    var replyTo by remember(video.id) { mutableStateOf<Comment?>(null) }
    var postError by remember(video.id) { mutableStateOf<String?>(null) }
    val expandedThreads = remember { mutableStateMapOf<Long, Boolean>() }

    fun reload() {
        scope.launch {
            loading = true
            failed = false
            try {
                list = ApiClient.api.videoDetail(video.id).comments.orEmpty()
            } catch (_: Exception) {
                failed = true
            } finally {
                loading = false
            }
        }
    }
    LaunchedEffect(video.id) { reload() }

    fun post() {
        val body = draft.trim()
        if (body.isEmpty() || posting) return
        posting = true
        postError = null
        scope.launch {
            try {
                val payload = buildMap<String, Any> {
                    put("body", body)
                    replyTo?.id?.let { put("parent_id", it) }
                }
                ApiClient.api.comment(video.id, payload)
                draft = ""
                replyTo = null
                reload()
            } catch (e: Exception) {
                postError = httpErrorMessage(e)
            } finally {
                posting = false
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
            ) {
                Text(
                    if (loading && list.isEmpty()) "Comments" else "Comments (${list.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClose,
                        ),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_close),
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (loading && list.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            } else if (failed && list.isEmpty()) {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Couldn't load comments", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { reload() }) { Text("Retry") }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    threadedComments(
                        comments = list,
                        expanded = expandedThreads,
                        onReply = { replyTo = it }
                    )
                }
            }
            replyTo?.let { target ->
                ReplyTargetChip(
                    username = target.username,
                    onClear = { replyTo = null },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it; postError = null },
                    placeholder = {
                        Text(
                            replyTo?.let { "Reply to @${it.username}…" }
                                ?: "Add a comment…"
                        )
                    },
                    maxLines = 3,
                    shape = CircleShape,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (draft.isBlank()) MaterialTheme.colorScheme.surfaceContainerHigh
                            else MaterialTheme.colorScheme.primaryContainer,
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { post() },
                        ),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_send),
                        contentDescription = "Send",
                        tint = if (draft.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            postError?.let { msg ->
                Text(
                    msg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}
