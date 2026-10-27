package watchshark.duckdns.org.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun UpdateScreenBody(
    onDone: () -> Unit,
    modifier: Modifier,
)

@Composable
fun UpdateScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    UpdateScreenBody(onDone, modifier)
}
