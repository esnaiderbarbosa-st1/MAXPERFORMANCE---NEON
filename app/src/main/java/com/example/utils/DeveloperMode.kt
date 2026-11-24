package com.example.utils

import com.example.BuildConfig

interface DeveloperModeProvider {
    val isEnabled: Boolean
}

class BuildDeveloperModeProvider : DeveloperModeProvider {
    override val isEnabled = BuildConfig.DEBUG
}

object DeveloperMode {
    val provider: DeveloperModeProvider = BuildDeveloperModeProvider()
    val isEnabled: Boolean get() = provider.isEnabled
}
