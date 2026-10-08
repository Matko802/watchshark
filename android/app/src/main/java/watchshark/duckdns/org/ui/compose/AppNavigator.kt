package watchshark.duckdns.org.ui.compose

import androidx.navigation.NavHostController

/**
 * Bridge from legacy Views fragments (which call
 * `MainActivity.openDetail(...)`) to the Compose NavController.
 * Set by WatchSharkApp once navigation is ready.
 */
object AppNavigator {
    @Volatile
    var controller: NavHostController? = null

    fun navigate(route: String) {
        try {
            controller?.navigate(route) {
                launchSingleTop = true
            }
        } catch (_: Exception) {
        }
    }

    fun goTab(tab: String) {
        try {
            controller?.navigate(tab) {
                popUpTo(controller?.graph?.startDestinationId ?: 0) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        } catch (_: Exception) {
        }
    }
}
