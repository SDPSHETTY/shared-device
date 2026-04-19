// FIXED VERSION: Keep app open after successful transition
private fun completeTransitionAfterSuccess() {
    transitionJob?.cancel()
    transitionJob = lifecycleScope.launch {
        delay(2000)  // Show success message for 2 seconds
        isTransitionInProgress = false

        // Instead of closing app, show ready state with logout available
        renderReadyState()
        setButtonsEnabled(true)

        // Clear password field
        binding.passwordEditText.text?.clear()

        Logger.i(TAG, "Transition completed successfully, app ready for logout")
    }
}

// ADDITIONAL: Add timeout mechanism to prevent infinite loading
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

    // ADD TIMEOUT PROTECTION
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
            Logger.w(TAG, "Role transition timed out for $label")
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
            binding.passwordEditText.text?.clear()
            Logger.i(TAG, "Role move succeeded for $label on device $deviceId to $targetGroupId")
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

// ADDITIONAL: Add resume protection in onResume
override fun onResume() {
    super.onResume()

    // Force clear loading state if no active transition job
    if (transitionJob?.isActive != true) {
        isTransitionInProgress = false
        renderReadyState()
        setButtonsEnabled(true)

        // Force hide loading overlay
        binding.loadingOverlay.visibility = View.GONE
        binding.loadingIndicator.visibility = View.GONE
        binding.loadingCaptionTextView.visibility = View.GONE
    }
}