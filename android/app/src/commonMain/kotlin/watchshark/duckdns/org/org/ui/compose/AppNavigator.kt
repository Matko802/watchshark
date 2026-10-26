package watchshark.duckdns.org.ui.compose

import androidx.navigation.NavHostController

object AppNavigator {
    @Volatile
    var controller: NavHostController? = null

    @Volatile
    var tabHandler: ((String) -> Unit)? = null

    @Volatile
    var screenHandler: ((String) -> Unit)? = null

    @Volatile
    private var pendingRoute: String? = null

    @Volatile
    private var pendingIsTab: Boolean = false

    @Volatile
    var afterLogin: String? = null

    fun navigate(route: String) {
        screenHandler?.let {
            try {
                it(route)
            } catch (_: Exception) {
            }
            return
        }
        val c = controller
        if (c == null) {
            pendingRoute = route
            pendingIsTab = false
            return
        }
        try {
            c.navigate(route) {
                launchSingleTop = true
            }
        } catch (_: Exception) {
        }
    }

    fun goTab(tab: String) {
        tabHandler?.let {
            try {
                it(tab)
            } catch (_: Exception) {
            }
            return
        }
        val c = controller
        if (c == null) {
            pendingRoute = tab
            pendingIsTab = true
            return
        }
        try {
            val stackRoutes = try {
                c.currentBackStack.value.mapNotNull { entry ->
                    try {
                        entry.destination.route
                    } catch (_: Exception) {
                        null
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
            val anchor = listOf("home", "wheels", "messages", "you", "settings", "notifications")
                .firstOrNull { section -> stackRoutes.any { it == section } }
            c.navigate(tab) {
                if (anchor != null) {
                    popUpTo(anchor) { saveState = true }
                }
                launchSingleTop = true
                restoreState = true
            }
        } catch (_: Exception) {
        }
    }

    fun flushPending() {
        val route = pendingRoute ?: return
        pendingRoute = null
        if (pendingIsTab) goTab(route) else navigate(route)
    }
}
