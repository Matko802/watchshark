package org.watchshark.app

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.watchshark.app.data.ApiClient
import org.watchshark.app.ui.AuthFragment
import org.watchshark.app.ui.HomeFragment
import org.watchshark.app.ui.MusicFragment
import org.watchshark.app.ui.ProfileFragment
import org.watchshark.app.ui.WheelsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_WatchShark)
        super.onCreate(savedInstanceState)
        ApiClient.init(this)
        setContentView(R.layout.activity_main)
        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showRoot(HomeFragment(), "home")
                R.id.nav_wheels -> showRoot(WheelsFragment(), "wheels")
                R.id.nav_music -> showRoot(MusicFragment(), "music")
                R.id.nav_profile -> showRoot(ProfileFragment(), "profile")
                else -> false
            }
            true
        }
        if (savedInstanceState == null) {
            if (ApiClient.sessionToken().isNullOrEmpty()) {
                showAuth()
            } else {
                bottomNav.selectedItemId = R.id.nav_home
            }
        }
    }

    fun showAuth() {
        bottomNav.visibility = View.GONE
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, AuthFragment())
            .commit()
    }

    fun showMain() {
        bottomNav.visibility = View.VISIBLE
        bottomNav.selectedItemId = R.id.nav_home
    }

    fun selectTab(id: Int) {
        bottomNav.visibility = View.VISIBLE
        bottomNav.selectedItemId = id
    }

    fun restartToAuth() {
        ApiClient.clearSession()
        supportFragmentManager.popBackStackImmediate(null, 1)
        showAuth()
    }

    private fun showRoot(fragment: Fragment, tag: String): Boolean {
        supportFragmentManager.popBackStackImmediate(null, 1)
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment, tag)
            .commit()
        return true
    }

    /** Push a detail screen (watch, channel, upload, settings, admin, notifications). */
    fun openDetail(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun setBottomNavVisible(visible: Boolean) {
        bottomNav.visibility = if (visible) View.VISIBLE else View.GONE
    }
}
