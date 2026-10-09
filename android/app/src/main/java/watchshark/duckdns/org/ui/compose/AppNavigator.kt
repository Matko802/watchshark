package watchshark.duckdns.org.ui.compose

import androidx.navigation.NavHostController


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
