package com.esper.authapp.ui

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.esper.authapp.R
import com.esper.authapp.config.AppConfig
import com.esper.authapp.databinding.ActivityConfigBinding
import com.esper.authapp.esper.EsperApiClient
import com.esper.authapp.esper.EsperDeviceManager
import com.esper.authapp.esper.EsperRuntimeInfo
import com.esper.authapp.util.Logger
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.TextView

class ConfigActivity : AppCompatActivity() {
    private lateinit var binding: ActivityConfigBinding
    private lateinit var esperDeviceManager: EsperDeviceManager
    private lateinit var esperApiClient: EsperApiClient
    private var runtimeInfo: EsperRuntimeInfo? = null
    private lateinit var roleOne: RoleAction
    private lateinit var roleTwo: RoleAction
    private var selectedRoleAction: RoleAction? = null
    private var isTransitionInProgress = false
    private var transitionJob: Job? = null

    // Session management
    private var isAuthenticatedMode = false
    private val shiftTimerHandler = Handler(Looper.getMainLooper())
    private var shiftTimerRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppConfig.init(this)

        // Check for active session first
        if (AppConfig.isSessionActive()) {
            setupAuthenticatedUI()
        } else {
            setupLoginUI()
        }

        esperDeviceManager = EsperDeviceManager(this)
        esperApiClient = EsperApiClient()
        roleOne = RoleAction(
            label = AppConfig.getRoleOneLabel(),
            groupId = AppConfig.getRoleOneGroupId(),
            password = AppConfig.getRoleOnePassword()
        )
        roleTwo = RoleAction(
            label = AppConfig.getRoleTwoLabel(),
            groupId = AppConfig.getRoleTwoGroupId(),
            password = AppConfig.getRoleTwoPassword()
        )

        if (!isAuthenticatedMode) {
            setupLoginButtonListeners()
            startEntryAnimation()
            startHeroAnimation()
        } else {
            setupAuthenticatedButtonListeners()
            startShiftTimer()
        }

        prewarmSdkRuntime()
    }

    private fun setupLoginUI() {
        isAuthenticatedMode = false
        binding = ActivityConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }

    private fun setupAuthenticatedUI() {
        isAuthenticatedMode = true
        setContentView(R.layout.activity_authenticated_state)
        updateAuthenticatedUIContent()
    }

    private fun setupLoginButtonListeners() {
        val configuredRoles = getConfiguredRoles()
        val labels = configuredRoles.map { it.label }

        binding.roleDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        )
        binding.roleDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedRoleAction = configuredRoles.getOrNull(position)
            binding.roleDropdownInputLayout.error = null
        }
        binding.loginButton.setOnClickListener {
            triggerSelectedRoleMove()
        }

        val hasConfiguredRoles = configuredRoles.isNotEmpty()
        binding.roleDropdown.isEnabled = hasConfiguredRoles
        binding.passwordEditText.isEnabled = hasConfiguredRoles
        binding.loginButton.isEnabled = hasConfiguredRoles

        when {
            configuredRoles.size == 1 -> selectRole(configuredRoles.first())
            else -> clearSelectedRole()
        }

        if (!hasConfiguredRoles) {
            updateUiState(
                status = getString(R.string.login_not_available),
                busy = false,
                support = getString(R.string.login_footer_retry)
            )
        }
    }

    private fun triggerSelectedRoleMove() {
        val selectedRole = selectedRoleAction
        if (selectedRole == null) {
            binding.roleDropdownInputLayout.error = getString(R.string.login_role_required)
            updateUiState(
                status = getString(R.string.login_role_required),
                busy = false,
                support = getString(R.string.login_footer)
            )
            return
        }
        triggerRoleMove(selectedRole)
    }

    private fun setupAuthenticatedButtonListeners() {
        try {
            val switchRoleButton = findViewById<Button>(R.id.switchRoleButton)
            val endShiftButton = findViewById<Button>(R.id.endShiftButton)

            Logger.i(TAG, "Setting up authenticated button listeners - switchRoleButton: ${switchRoleButton != null}, endShiftButton: ${endShiftButton != null}")

            switchRoleButton?.setOnClickListener {
                Logger.i(TAG, "Switch user button clicked")
                startSwitchUserFlow()
            } ?: Logger.e(TAG, "switchRoleButton not found!")

            endShiftButton?.setOnClickListener {
                Logger.i(TAG, "Logout button clicked")
                logoutActiveSession()
            } ?: Logger.e(TAG, "endShiftButton not found!")
        } catch (e: Exception) {
            Logger.e(TAG, "Error setting up authenticated button listeners", e)
        }
    }

    override fun onResume() {
        super.onResume()

        try {
            // Force clear loading state if no active transition job
            if (transitionJob?.isActive != true) {
                isTransitionInProgress = false
                renderReadyState()
                setButtonsEnabled(true)

                if (!isAuthenticatedMode && ::binding.isInitialized) {
                    // Force hide loading overlay to prevent stuck loading state
                    binding.loadingOverlay.visibility = View.GONE
                    binding.loadingIndicator.visibility = View.GONE
                    binding.loadingCaptionTextView.visibility = View.GONE
                } else if (isAuthenticatedMode) {
                    // Update authenticated UI content
                    updateAuthenticatedUIContent()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error in onResume", e)
        }
    }

    private fun prewarmSdkRuntime() {
        val apiKey = AppConfig.getEsperApiKey().trim()
        if (apiKey.isBlank()) {
            return
        }

        lifecycleScope.launch {
            runCatching {
                updateUiState(
                    status = getString(R.string.login_preparing_device),
                    busy = true,
                    support = getString(R.string.login_footer)
                )
                val resolved = esperDeviceManager.resolveRuntimeInfo(
                    apiKey = apiKey,
                    fallbackBaseUrl = AppConfig.getEsperBaseUrl(),
                    fallbackEnterpriseId = AppConfig.getEnterpriseId(),
                    fallbackDeviceId = AppConfig.getDeviceIdOverride()
                )
                discoverHomeGroupIfNeeded(resolved)
                resolved
            }.onSuccess { resolved ->
                runtimeInfo = resolved
                renderReadyState()
                Logger.i(TAG, "SDK warm-up complete for device ${resolved.deviceId}")
            }.onFailure { throwable ->
                updateUiState(
                    status = userMessageForState(AppUiState.READY_FAILED, throwable, null),
                    busy = false,
                    support = getString(R.string.login_footer_retry)
                )
                Logger.e(TAG, "SDK warm-up failed", throwable)
            }
        }
    }

    private fun triggerRoleMove(roleAction: RoleAction) {
        val label = roleAction.label
        val targetGroupId = roleAction.groupId
        if (!AppConfig.isApiConfigured()) {
            val message = getString(R.string.login_not_configured)
            updateUiState(status = message, busy = false, support = getString(R.string.login_footer_retry))
            return
        }

        if (targetGroupId.isBlank()) {
            val message = getString(R.string.login_not_available)
            updateUiState(status = message, busy = false, support = getString(R.string.login_footer_retry))
            return
        }

        if (!isPasswordAccepted(roleAction)) {
            return
        }

        isTransitionInProgress = true
        setButtonsEnabled(false)
        updateUiState(
            status = userMessageForState(AppUiState.SIGNING_IN, null, label),
            busy = true,
            support = getString(R.string.login_footer)
        )

        // Add timeout protection to prevent infinite loading
        val timeoutJob = lifecycleScope.launch {
            delay(30000) // 30 second timeout
            if (isTransitionInProgress) {
                isTransitionInProgress = false
                updateUiState(
                    status = "Transition timed out. Please try again.",
                    busy = false,
                    support = getString(R.string.login_footer_retry)
                )
                setButtonsEnabled(true)
                Logger.i(TAG, "Role transition timed out for $label")
            }
        }

        lifecycleScope.launch {
            runCatching {
                val resolvedRuntime = runtimeInfo ?: esperDeviceManager.resolveRuntimeInfo(
                    apiKey = AppConfig.getEsperApiKey(),
                    fallbackBaseUrl = AppConfig.getEsperBaseUrl(),
                    fallbackEnterpriseId = AppConfig.getEnterpriseId(),
                    fallbackDeviceId = AppConfig.getDeviceIdOverride()
                )
                discoverHomeGroupIfNeeded(resolvedRuntime)
                runtimeInfo = resolvedRuntime
                esperApiClient.moveDeviceToGroup(
                    config = resolvedRuntime.tenantConfig,
                    deviceId = resolvedRuntime.deviceId,
                    destinationGroupId = targetGroupId
                )
                resolvedRuntime.deviceId
            }.onSuccess { deviceId ->
                timeoutJob.cancel() // Cancel timeout since we succeeded
                updateUiState(
                    status = userMessageForState(AppUiState.SIGNED_IN, null, label),
                    busy = true,
                    support = getString(R.string.login_footer_transition)
                )
                if (!isAuthenticatedMode) {
                    binding.passwordEditText.text?.clear()
                }
                Logger.i(TAG, "Role move succeeded for $label on device $deviceId to $targetGroupId")

                // Start user session
                AppConfig.startUserSession(
                    role = label,
                    userId = "user_${System.currentTimeMillis()}", // Generate user ID
                    userName = label, // Use role as username for now
                    groupId = targetGroupId
                )

                completeTransitionAfterSuccess()
            }.onFailure { throwable ->
                timeoutJob.cancel() // Cancel timeout since we failed
                isTransitionInProgress = false
                updateUiState(
                    status = userMessageForState(AppUiState.SIGN_IN_FAILED, throwable, label),
                    busy = false,
                    support = getString(R.string.login_footer_retry)
                )
                Logger.e(TAG, "Role move failed for $label", throwable)
                setButtonsEnabled(true)
            }
        }
    }

    private fun isPasswordAccepted(roleAction: RoleAction): Boolean {
        val enteredPassword = binding.passwordEditText.text?.toString().orEmpty()
        if (enteredPassword.isBlank()) {
            val message = getString(R.string.login_auth_required)
            updateUiState(status = message, busy = false, support = getString(R.string.login_footer))
            return false
        }

        val expectedPassword = roleAction.password

        if (expectedPassword.isBlank()) {
            val message = getString(R.string.login_not_available)
            updateUiState(status = message, busy = false, support = getString(R.string.login_footer_retry))
            return false
        }

        if (enteredPassword != expectedPassword) {
            val message = getString(R.string.login_auth_invalid)
            updateUiState(status = message, busy = false, support = getString(R.string.login_footer))
            return false
        }

        return true
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        if (!isAuthenticatedMode && ::binding.isInitialized) {
            val hasConfiguredRoles = getConfiguredRoles().isNotEmpty()
            binding.roleDropdown.isEnabled = enabled && hasConfiguredRoles
            binding.loginButton.isEnabled = enabled && hasConfiguredRoles
            binding.passwordEditText.isEnabled = enabled && hasConfiguredRoles
        } else if (isAuthenticatedMode) {
            // In authenticated mode, safely update buttons
            try {
                findViewById<Button>(R.id.switchRoleButton)?.isEnabled = enabled
                findViewById<Button>(R.id.endShiftButton)?.isEnabled = enabled
            } catch (e: Exception) {
                Logger.e(TAG, "Error updating authenticated buttons", e)
            }
        }
    }

    private suspend fun discoverHomeGroupIfNeeded(resolvedRuntime: EsperRuntimeInfo) {
        if (AppConfig.getHomeGroupId().isNotBlank()) {
            return
        }
        val deviceState = esperApiClient.fetchDeviceState(
            config = resolvedRuntime.tenantConfig,
            deviceId = resolvedRuntime.deviceId
        )
        val homeGroup = deviceState.groups.firstOrNull() ?: return
        AppConfig.saveDiscoveredHomeGroup(homeGroup.id, homeGroup.name)
        Logger.i(TAG, "Discovered home group ${homeGroup.id} (${homeGroup.name})")
    }

    private fun updateUiState(status: String, busy: Boolean, support: String) {
        if (!isAuthenticatedMode && ::binding.isInitialized) {
            try {
                binding.demoStatusTextView.text = status
                binding.loadingIndicator.visibility = if (busy) View.VISIBLE else View.GONE
                binding.loadingCaptionTextView.visibility = if (busy) View.VISIBLE else View.GONE
                binding.supportTextView.text = support
                binding.loadingOverlay.visibility = if (busy) View.VISIBLE else View.GONE
                binding.loadingOverlayTitleTextView.text = status
                binding.loadingOverlayBodyTextView.text = support
            } catch (e: Exception) {
                Logger.e(TAG, "Error updating UI state", e)
            }
        } else if (isAuthenticatedMode) {
            // In authenticated mode, show status in a different way
            Logger.i(TAG, "Authenticated mode status: $status (busy: $busy)")
        }
    }

    private fun renderReadyState() {
        if (!isAuthenticatedMode) {
            updateUiState(
                status = getString(R.string.login_ready),
                busy = false,
                support = if (AppConfig.getHomeGroupName().isNotBlank()) {
                    getString(R.string.login_footer_home_ready, AppConfig.getHomeGroupName())
                } else {
                    getString(R.string.login_footer)
                }
            )
        }
    }

    private fun completeTransitionAfterSuccess() {
        transitionJob?.cancel()
        transitionJob = lifecycleScope.launch {
            delay(2000)  // Show success message for 2 seconds
            isTransitionInProgress = false

            // Transition to authenticated state UI
            transitionToAuthenticatedState()

            Logger.i(TAG, "Transition completed successfully, showing authenticated state")
        }
    }

    private fun transitionToAuthenticatedState() {
        try {
            // Switch to authenticated UI within same activity
            isAuthenticatedMode = true
            setContentView(R.layout.activity_authenticated_state)
            setupAuthenticatedButtonListeners()
            updateAuthenticatedUIContent()
            startShiftTimer()
            Logger.i(TAG, "Transitioned to authenticated state")
        } catch (e: Exception) {
            Logger.e(TAG, "Error transitioning to authenticated state", e)
        }
    }

    private fun startHeroAnimation() {
        val scaleX = ObjectAnimator.ofFloat(binding.brandOrbView, View.SCALE_X, 1f, 1.06f, 1f)
        val scaleY = ObjectAnimator.ofFloat(binding.brandOrbView, View.SCALE_Y, 1f, 1.06f, 1f)
        val alpha = ObjectAnimator.ofFloat(binding.brandOrbView, View.ALPHA, 0.92f, 1f, 0.92f)
        val rotation = ObjectAnimator.ofFloat(binding.brandOrbView, View.ROTATION, 0f, 4f, 0f)
        AnimatorSet().apply {
            duration = 2400L
            interpolator = AccelerateDecelerateInterpolator()
            playTogether(scaleX, scaleY, alpha, rotation)
            startDelay = 300L
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    startHeroAnimation()
                }
            })
            start()
        }
    }

    private fun startEntryAnimation() {
        binding.heroCard.alpha = 0f
        binding.heroCard.translationY = 32f
        binding.heroCard.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(420L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        val buttonAnimation: Animation = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        buttonAnimation.duration = 380L
        buttonAnimation.startOffset = 180L
        binding.loginButton.startAnimation(buttonAnimation)
    }

    private fun userMessageForState(state: AppUiState, throwable: Throwable?, label: String?): String {
        return when (state) {
            AppUiState.SIGNING_IN -> getString(R.string.login_signing_in, label.orEmpty())
            AppUiState.SIGNED_IN -> getString(R.string.login_success)
            AppUiState.SIGN_IN_FAILED -> when {
                throwable?.message?.contains("Network connection", ignoreCase = true) == true -> getString(R.string.login_network_required)
                else -> getString(R.string.login_failed_generic)
            }
            AppUiState.READY_FAILED -> when {
                throwable?.message?.contains("Network connection", ignoreCase = true) == true -> getString(R.string.login_waiting_for_network)
                else -> getString(R.string.login_preparing_retry)
            }
        }
    }

    private fun updateAuthenticatedUIContent() {
        if (!isAuthenticatedMode) return

        try {
            val currentRole = AppConfig.getCurrentUserRole()
            val currentUser = AppConfig.getCurrentUserName()
            val sessionStart = AppConfig.getSessionStartTime()
            val resolvedRole = currentRole.ifBlank { getString(R.string.session_role_unknown) }
            val resolvedUser = currentUser.ifBlank { getString(R.string.session_user_placeholder) }

            // Update user info safely
            findViewById<TextView>(R.id.welcomeTitle)?.apply {
                text = if (currentRole.isNotBlank()) {
                    getString(R.string.session_title_role, currentRole)
                } else {
                    getString(R.string.session_title)
                }
            }

            findViewById<TextView>(R.id.sessionSubtitle)?.apply {
                text = if (currentRole.isNotBlank()) {
                    getString(R.string.session_subtitle_role, currentRole)
                } else {
                    getString(R.string.session_subtitle)
                }
            }

            findViewById<TextView>(R.id.currentRoleText)?.apply {
                text = resolvedRole
            }

            findViewById<TextView>(R.id.currentUserText)?.apply {
                text = resolvedUser
            }

            findViewById<TextView>(R.id.userInitial)?.apply {
                text = resolvedRole.firstOrNull()?.toString()?.uppercase() ?: "U"
            }

            // Update session start time
            if (sessionStart > 0) {
                val startTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(sessionStart))
                findViewById<TextView>(R.id.sessionStartText)?.apply {
                    text = startTime
                }
            }

            // Update device info if available
            val deviceId = runtimeInfo?.deviceId ?: "Unknown Device"
            findViewById<TextView>(R.id.deviceInfoText)?.apply {
                text = getString(R.string.session_device_info, deviceId)
            }

            findViewById<TextView>(R.id.sessionHintText)?.apply {
                text = if (currentRole.isNotBlank()) {
                    getString(R.string.session_hint_role, currentRole)
                } else {
                    getString(R.string.session_hint_default)
                }
            }

            updateShiftTimer()

        } catch (e: Exception) {
            Logger.e(TAG, "Error updating authenticated UI content", e)
        }
    }

    private fun startShiftTimer() {
        shiftTimerRunnable = object : Runnable {
            override fun run() {
                updateShiftTimer()
                shiftTimerHandler.postDelayed(this, 60000) // Update every minute
            }
        }
        shiftTimerHandler.post(shiftTimerRunnable!!)
    }

    private fun stopShiftTimer() {
        shiftTimerRunnable?.let { shiftTimerHandler.removeCallbacks(it) }
    }

    private fun updateShiftTimer() {
        if (!isAuthenticatedMode) return

        try {
            val duration = AppConfig.getSessionDurationFormatted()
            findViewById<TextView>(R.id.shiftDurationText)?.apply {
                text = duration
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Error updating shift timer", e)
        }
    }

    private fun startSwitchUserFlow() {
        transitionToLoginState()
    }

    private fun logoutActiveSession() {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(R.string.session_logout_title)
        builder.setMessage(R.string.session_logout_message)
        builder.setPositiveButton(R.string.session_logout_confirm) { _, _ ->
            performLogout()
        }
        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun performLogout() {
        lifecycleScope.launch {
            runCatching {
                // Move back to home group if configured
                val homeGroupId = AppConfig.getHomeGroupId()
                if (homeGroupId.isNotEmpty()) {
                    val resolvedRuntime = runtimeInfo ?: esperDeviceManager.resolveRuntimeInfo(
                        apiKey = AppConfig.getEsperApiKey(),
                        fallbackBaseUrl = AppConfig.getEsperBaseUrl(),
                        fallbackEnterpriseId = AppConfig.getEnterpriseId(),
                        fallbackDeviceId = AppConfig.getDeviceIdOverride()
                    )
                    esperApiClient.moveDeviceToGroup(
                        config = resolvedRuntime.tenantConfig,
                        deviceId = resolvedRuntime.deviceId,
                        destinationGroupId = homeGroupId
                    )
                }
            }.onSuccess {
                AppConfig.endUserSession()
                Logger.i(TAG, "User logged out and session terminated")
                transitionToLoginState()
            }.onFailure { throwable ->
                Logger.e(TAG, "Failed to move device during logout", throwable)
                AppConfig.endUserSession()
                transitionToLoginState()
            }
        }
    }

    private fun transitionToLoginState(preSelectRole: RoleAction? = null) {
        try {
            // Switch to login UI within same activity
            stopShiftTimer()
            isAuthenticatedMode = false
            binding = ActivityConfigBinding.inflate(layoutInflater)
            setContentView(binding.root)
            setupLoginButtonListeners()
            preSelectRole?.let { preferredRole ->
                getConfiguredRoles().firstOrNull { it == preferredRole }?.let { selectRole(it) }
            }
            startEntryAnimation()
            startHeroAnimation()
            renderReadyState()
            Logger.i(TAG, "Transitioned to login state")
        } catch (e: Exception) {
            Logger.e(TAG, "Error transitioning to login state", e)
        }
    }

    override fun onDestroy() {
        transitionJob?.cancel()
        stopShiftTimer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ConfigActivity"
    }

    private enum class AppUiState {
        SIGNING_IN,
        SIGNED_IN,
        SIGN_IN_FAILED,
        READY_FAILED
    }

    private data class RoleAction(
        val label: String,
        val groupId: String,
        val password: String
    )

    private fun getConfiguredRoles(): List<RoleAction> {
        return listOf(roleOne, roleTwo).filter { role ->
            role.label.isNotBlank() && role.groupId.isNotBlank()
        }
    }

    private fun selectRole(role: RoleAction) {
        selectedRoleAction = role
        binding.roleDropdown.setText(role.label, false)
        binding.roleDropdownInputLayout.error = null
    }

    private fun clearSelectedRole() {
        selectedRoleAction = null
        binding.roleDropdown.setText("", false)
        binding.roleDropdownInputLayout.error = null
    }
}
