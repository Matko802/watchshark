package watchshark.duckdns.org.ui.compose.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import watchshark.duckdns.org.R
import watchshark.duckdns.org.data.Haptics


@Composable
fun WatchPlayer(
    player: Player?,
    thumbnailUrl: String?,
    qualities: List<String> = emptyList(),
    quality: String = "Auto",
    onQuality: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var fullscreen by remember { mutableStateOf(false) }
    if (fullscreen) {
        Dialog(
            onDismissRequest = { fullscreen = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
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
            modifier = modifier,
        )
    }
}

@Composable
private fun PlayerChrome(
    player: Player?,
    thumbnailUrl: String?,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    qualities: List<String>,
    quality: String,
    onQuality: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = rememberPlayerUiState(player)
    var controlsVisible by remember { mutableStateOf(true) }
    var hideTick by remember { mutableIntStateOf(0) }
    var qualityOpen by remember { mutableStateOf(false) }


    LaunchedEffect(ui.isPlaying, hideTick) {
        if (ui.isPlaying) {
            delay(3000)
            controlsVisible = false
        } else {
            controlsVisible = true
        }
    }
    fun poke() {
        controlsVisible = true
        hideTick++
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { poke() })
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
                        painterResource(R.drawable.ic_volume_up),
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
            visible = ui.isReady,
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
                Slider(
                    value = fraction,
                    onValueChange = {
                        if (ui.durationMs > 0) {
                            player?.seekTo((it * ui.durationMs).toLong())
                        }
                        poke()
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
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
                                        if (ui.isPlaying) R.drawable.ic_pause
                                        else R.drawable.ic_play_arrow,
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
                                        if (ui.isMuted) R.drawable.ic_volume_off
                                        else R.drawable.ic_volume_up,
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
                                        painterResource(R.drawable.ic_settings),
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
                                        if (fullscreen) R.drawable.ic_fullscreen_exit
                                        else R.drawable.ic_fullscreen,
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
    val buzzCtx = LocalContext.current
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
