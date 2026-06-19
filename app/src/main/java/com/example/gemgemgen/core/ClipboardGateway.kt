package com.example.gemgemgen.core

interface ClipboardGateway {
    fun readText(): String
    fun writeText(text: String)
}
