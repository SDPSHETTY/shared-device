package com.esper.authapp.ui

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.esper.authapp.R
import com.esper.authapp.config.AppConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConfigActivityE2ETest {

    @Test
    fun loginSwitchUserAndLogoutFlow() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        AppConfig.init(context)
        AppConfig.endUserSession()

        val configuredRoles = getConfiguredRoles()
        assertTrue("Expected at least one configured role", configuredRoles.isNotEmpty())
        assertTrue("Expected first role to include a password", configuredRoles.first().password.isNotBlank())
        assertTrue("Expected home group to be available for logout", AppConfig.getHomeGroupId().isNotBlank())

        ActivityScenario.launch(ConfigActivity::class.java).use { scenario ->
            signIn(configuredRoles.first())
            waitForCondition(timeoutMs = 90_000) {
                AppConfig.isSessionActive() && AppConfig.getCurrentUserRole() == configuredRoles.first().label
            }
            SystemClock.sleep(2_500)

            onView(withId(R.id.currentRoleText)).check(matches(withText(configuredRoles.first().label)))
            onView(withId(R.id.switchRoleButton)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.endShiftButton)).perform(scrollTo()).check(matches(isDisplayed()))

            onView(withId(R.id.switchRoleButton)).perform(scrollTo(), click())
            onView(withId(R.id.loginButton)).check(matches(isDisplayed()))
            onView(withId(R.id.roleDropdown)).check(matches(isDisplayed()))

            signIn(configuredRoles.first())
            waitForCondition(timeoutMs = 90_000) {
                AppConfig.isSessionActive() && AppConfig.getCurrentUserRole() == configuredRoles.first().label
            }
            SystemClock.sleep(2_500)

            onView(withId(R.id.endShiftButton)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withId(R.id.endShiftButton)).perform(scrollTo(), click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

            waitForCondition(timeoutMs = 90_000) {
                !AppConfig.isSessionActive()
            }
            SystemClock.sleep(1_000)

            onView(withId(R.id.loginButton)).check(matches(isDisplayed()))
            assertFalse("Expected session to be cleared after logout", AppConfig.isSessionActive())
        }
    }

    private fun signIn(role: ManagedRole) {
        onView(withId(R.id.roleDropdown)).perform(click())
        onView(withText(role.label)).inRoot(isPlatformPopup()).perform(click())
        onView(withId(R.id.passwordEditText)).perform(replaceText(role.password), closeSoftKeyboard())
        onView(withId(R.id.loginButton)).perform(click())
    }

    private fun getConfiguredRoles(): List<ManagedRole> {
        return listOf(
            ManagedRole(
                label = AppConfig.getRoleOneLabel(),
                groupId = AppConfig.getRoleOneGroupId(),
                password = AppConfig.getRoleOnePassword()
            ),
            ManagedRole(
                label = AppConfig.getRoleTwoLabel(),
                groupId = AppConfig.getRoleTwoGroupId(),
                password = AppConfig.getRoleTwoPassword()
            )
        ).filter { it.label.isNotBlank() && it.groupId.isNotBlank() }
    }

    private fun waitForCondition(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) {
                return
            }
            SystemClock.sleep(500)
        }
        throw AssertionError("Condition not met within ${timeoutMs}ms")
    }

    private data class ManagedRole(
        val label: String,
        val groupId: String,
        val password: String
    )
}
