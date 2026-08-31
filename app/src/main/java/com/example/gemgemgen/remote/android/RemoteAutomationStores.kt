package com.example.gemgemgen.remote.android

import android.content.Context
import com.example.gemgemgen.remote.domain.AutomationMode
import java.util.UUID

internal class RemoteAutomationStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun mode(): AutomationMode {
        return AutomationMode.fromStorageValue(preferences.getString(KEY_MODE, null))
    }

    fun saveMode(mode: AutomationMode) {
        preferences.edit().putString(KEY_MODE, mode.storageValue).apply()
    }

    fun installationId(): String {
        val saved = preferences.getString(KEY_INSTALLATION_ID, null)
        if (!saved.isNullOrBlank()) return saved
        return UUID.randomUUID().toString().also { generated ->
            preferences.edit().putString(KEY_INSTALLATION_ID, generated).commit()
        }
    }

    fun pairedSender(): PairedSender? {
        val senderId = preferences.getString(KEY_PAIRED_SENDER_ID, null).orEmpty()
        val token = preferences.getString(KEY_PAIRED_SENDER_TOKEN, null).orEmpty()
        return if (senderId.isBlank() || token.isBlank()) null else PairedSender(senderId, token)
    }

    fun savePairedSender(senderId: String, token: String) {
        preferences.edit()
            .putString(KEY_PAIRED_SENDER_ID, senderId)
            .putString(KEY_PAIRED_SENDER_TOKEN, token)
            .apply()
    }

    fun pairedReceiver(): PairedReceiver? {
        val receiverId = preferences.getString(KEY_PAIRED_RECEIVER_ID, null).orEmpty()
        val receiverName = preferences.getString(KEY_PAIRED_RECEIVER_NAME, null).orEmpty()
        val token = preferences.getString(KEY_PAIRED_RECEIVER_TOKEN, null).orEmpty()
        return if (receiverId.isBlank() || token.isBlank()) {
            null
        } else {
            PairedReceiver(receiverId, receiverName, token)
        }
    }

    fun savePairedReceiver(receiverId: String, receiverName: String, token: String) {
        preferences.edit()
            .putString(KEY_PAIRED_RECEIVER_ID, receiverId)
            .putString(KEY_PAIRED_RECEIVER_NAME, receiverName)
            .putString(KEY_PAIRED_RECEIVER_TOKEN, token)
            .apply()
    }

    data class PairedSender(val senderId: String, val token: String)
    data class PairedReceiver(
        val receiverId: String,
        val receiverName: String,
        val token: String
    )

    companion object {
        private const val PREFERENCES_NAME = "remote_automation"
        private const val KEY_MODE = "mode"
        private const val KEY_INSTALLATION_ID = "installation_id"
        private const val KEY_PAIRED_SENDER_ID = "paired_sender_id"
        private const val KEY_PAIRED_SENDER_TOKEN = "paired_sender_token"
        private const val KEY_PAIRED_RECEIVER_ID = "paired_receiver_id"
        private const val KEY_PAIRED_RECEIVER_NAME = "paired_receiver_name"
        private const val KEY_PAIRED_RECEIVER_TOKEN = "paired_receiver_token"
    }
}
