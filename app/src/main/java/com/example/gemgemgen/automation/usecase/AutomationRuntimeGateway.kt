package com.example.gemgemgen.automation.usecase

fun interface GeminiPromptGatewayProvider {
    fun current(): GeminiPromptGateway?
}

fun interface GeminiAppLauncher {
    fun launch(): Boolean
}
