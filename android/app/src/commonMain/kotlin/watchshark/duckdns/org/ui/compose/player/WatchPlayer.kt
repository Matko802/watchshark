package watchshark.duckdns.org.ui.compose.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import watchshark.duckdns.org.ui.PlatformLocal
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import watchshark.duckdns.org.*
import watchshark.duckdns.org.Res
import watchshark.duckdns.org.data.Haptics
import watchshark.duckdns.org.data.PPlayer
import watchshark.duckdns.org.ui.compose.theme.AppMotion


@Composable
fun WatchPlayer(
    player: PPlayer?,
    thumbnailUrl: String?,
    qualities: List<String> = emptyList(),
    quality: String = "Auto",
    onQuality: (String) -> Unit = {},
    onDoubleTapLike: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var fullscreen by remember { mutableStateOf(false) }
    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
            ),
        ) {
            PlayerChrome(
                player = player,
                thumbnailUrl = thumbnailUrl,
                fullscreen = true,
                onToggleFullscreen = { fullscreen = false },
                qualities = qualities,
                quality = quality,
                onQuality = onQuality,
                onDoubleTapLike = onDoubleTapLike,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            )
        }
    } else {
        PlayerChrome(
            player = player,
            thumbnailUrl = thumbnailUrl,
            fullscreen = false,
            onToggleFullscreen = { fullscreen = true },
            qualities = qualities,
            quality = quality,
            onQuality = onQuality,
            onDoubleTapLike = onDoubleTapLike,
            modifier = modifier,
        )
    }
}

@Composable
private fun PlayerChrome(
    player: PPlayer?,
    thumbnailUrl: String?,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    qualities: List<String>,
    quality: String,
    onQuality: (String) -> Unit,
    onDoubleTapLike: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ui = rememberPlayerUiState(player)
    var controlsVisible by remember { mutableStateOf(true) }
    var hideTick by remember { mutableIntStateOf(0) }
    var qualityOpen by remember { mutableStateOf(false) }
    var heartBurst by remember { mutableStateOf(false) }
    val buzzCtx = PlatformLocal.app


    LaunchedEffect(ui.isPlaying, hideTick) {
        if (ui.isPlaying) {
            delay(3000)
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }
    LaunchedEffect(heartBurst) {
        if (heartBurst) {
            delay(800)
            heartBurst = false
        }
    }
    fun poke() {
        controlsVisible = true
        hideTick++
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(onDoubleTapLike) {
                detectTapGestures(
                    onTap = { poke() },
                    onDoubleTap = {
                        if (onDoubleTapLike != null) {
                            Haptics.tick(buzzCtx)
                            heartBurst = true
                            onDoubleTapLike()
                        }
                    },
                )
            },
    ) {
        VideoSurface(player = player, modifier = Modifier.matchParentSize())


        if (!ui.isReady && thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

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

        BufferingSpinner(
            visible = ui.isBuffering,
            modifier = Modifier.align(Alignment.Center),
        )

        CenterPlayButton(
            visible = controlsVisible && !ui.isPlaying && ui.isReady,
            playing = false,
            onToggle = {
                player?.play()
                poke()
            },
            modifier = Modifier.align(Alignment.Center),
        )


        AnimatedVisibility(
            visible = ui.isMuted && ui.isPlaying,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp),
            label = "unmute",
        ) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                player?.volume = 1f
                                poke()
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_volume_up),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        "Tap to unmute",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }


        AnimatedVisibility(
            visible = (ui.isReady || ui.isBuffering) && controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
            label = "progressBar",
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xC8000000)),
                        ),
                    )
                    .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            ) {
                val fraction = if (ui.durationMs > 0) {
                    (ui.positionMs.toFloat() / ui.durationMs).coerceIn(0f, 1f)
                } else 0f
                val bufferedFraction = if (ui.durationMs > 0) {
                    (ui.bufferedMs.toFloat() / ui.durationMs).coerceIn(0f, 1f)
                } else 0f
                ScrubBar(
                    fraction = fraction,
                    bufferedFraction = bufferedFraction,
                    controlsActive = controlsVisible,
                    enabled = ui.durationMs > 0,
                    waveOn = ui.isPlaying,
                    onScrub = { poke() },
                    onSeek = {
                        if (ui.durationMs > 0) {
                            player?.seekTo((it * ui.durationMs).toLong())
                        }
                        poke()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn() + slideInVertically { it / 3 },
                    exit = fadeOut() + slideOutVertically { it / 3 },
                    label = "controls",
                ) {
                    Column {
                        AnimatedVisibility(
                            visible = qualityOpen && qualities.size > 1,
                            enter = fadeIn() + slideInVertically { it / 3 },
                            exit = fadeOut() + slideOutVertically { it / 3 },
                            label = "qualityChips",
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                qualities.forEach { option ->
                                    FilterChip(
                                        selected = quality == option,
                                        onClick = {
                                            onQuality(option)
                                            qualityOpen = false
                                            poke()
                                        },
                                        label = { Text(option) },
                                    )
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            PlayerIconButton(
                                onClick = {
                                    if (ui.isPlaying) player?.pause() else player?.play()
                                    poke()
                                },
                                description = if (ui.isPlaying) "Pause" else "Play",
                            ) {
                                Icon(
                                    painterResource(
                                        if (ui.isPlaying) Res.drawable.ic_pause
                                        else Res.drawable.ic_play_arrow,
                                    ),
                                    contentDescription = null,
                                )
                            }
                            PlayerIconButton(
                                onClick = {
                                    player?.volume = if (ui.isMuted) 1f else 0f
                                    poke()
                                },
                                description = "Mute",
                            ) {
                                Icon(
                                    painterResource(
                                        if (ui.isMuted) Res.drawable.ic_volume_off
                                        else Res.drawable.ic_volume_up,
                                    ),
                                    contentDescription = null,
                                )
                            }
                            Text(
                                "${fmtPlayerTime(ui.positionMs)} / ${fmtPlayerTime(ui.durationMs)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                            Spacer(
                                modifier = Modifier.weight(1f),
                            )
                            if (qualities.size > 1) {
                                PlayerIconButton(
                                    onClick = {
                                        qualityOpen = !qualityOpen
                                        poke()
                                    },
                                    description = "Quality",
                                ) {
                                    Icon(
                                        painterResource(Res.drawable.ic_settings),
                                        contentDescription = null,
                                    )
                                }
                            }
                            PlayerIconButton(
                                onClick = {
                                    onToggleFullscreen()
                                    poke()
                                },
                                description = "Fullscreen",
                            ) {
                                Icon(
                                    painterResource(
                                        if (fullscreen) Res.drawable.ic_fullscreen_exit
                                        else Res.drawable.ic_fullscreen,
                                    ),
                                    contentDescription = null,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun PlayerIconButton(
    onClick: () -> Unit,
    description: String,
    icon: @Composable () -> Unit,
) {
    val buzzCtx = PlatformLocal.app
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    Haptics.tick(buzzCtx)
                    onClick()
                },
            ),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides Color.White,
        ) {
            icon()
        }
    }
}
