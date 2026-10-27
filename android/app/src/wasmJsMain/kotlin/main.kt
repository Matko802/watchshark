@file:OptIn(ExperimentalComposeUiApi::class)

package watchshark.duckdns.org

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import org.w3c.dom.HTMLDivElement
import watchshark.duckdns.org.data.WebVideoStage
import watchshark.duckdns.org.ui.compose.WatchSharkApp

fun main() {
    WebVideoStage.preinit()
    val container = document.createElement("div") as HTMLDivElement
    container.style.position = "absolute"
    container.style.top = "0px"
    container.style.left = "0px"
    container.style.width = "100%"
    container.style.height = "100%"
    container.style.setProperty("overflow", "visible")
    document.body?.appendChild(container)
    ComposeViewport(container) {
        WatchSharkApp(startLoggedIn = false)
    }
}
