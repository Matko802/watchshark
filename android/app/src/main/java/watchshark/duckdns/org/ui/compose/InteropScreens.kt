package watchshark.duckdns.org.ui.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.os.bundleOf
import androidx.fragment.compose.AndroidFragment
import watchshark.duckdns.org.ui.AdminFragment
import watchshark.duckdns.org.ui.AuthFragment
import watchshark.duckdns.org.ui.SettingsFragment
import watchshark.duckdns.org.ui.UploadFragment
import watchshark.duckdns.org.ui.WheelsFragment

/** Legacy Wheels player (ExoPlayer playlist) hosted in Compose. */
@Composable
fun WheelsInterop(modifier: Modifier = Modifier) {
    AndroidFragment<WheelsFragment>(
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun UploadInterop(kind: String = "video", modifier: Modifier = Modifier) {
    AndroidFragment<UploadFragment>(
        modifier = modifier.fillMaxSize(),
        arguments = bundleOf("kind" to kind),
    )
}

@Composable
fun AuthInterop(modifier: Modifier = Modifier) {
    AndroidFragment<AuthFragment>(
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun SettingsInterop(modifier: Modifier = Modifier) {
    AndroidFragment<SettingsFragment>(
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
fun AdminInterop(modifier: Modifier = Modifier) {
    AndroidFragment<AdminFragment>(
        modifier = modifier.fillMaxSize(),
    )
}
