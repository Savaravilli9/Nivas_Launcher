package com.shreenivas.tvhome.services

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TvNotificationService : NotificationListenerService() {

    data class RealAlert(
        val key: String,
        val appName: String,
        val title: String,
        val text: String,
        val timeStr: String
    )

    companion object {
        private val liveAlerts = ArrayList<RealAlert>()
        @Volatile private var instance: TvNotificationService? = null

        fun isPermissionGranted(ctx: Context): Boolean {
            val component = ComponentName(ctx, TvNotificationService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
                ?: return false
            return enabled.split(':').any { it.equals(component, ignoreCase = true) }
        }

        fun getAlerts(): List<RealAlert> {
            instance?.syncActiveNotifications()
            return synchronized(liveAlerts) { liveAlerts.toList() }
        }

        fun dismissAlert(key: String) {
            try {
                instance?.cancelNotification(key)
            } catch (_: Exception) {
            }
            synchronized(liveAlerts) { liveAlerts.removeAll { it.key == key } }
        }

        fun clearAllAlerts() {
            try {
                instance?.cancelAllNotifications()
            } catch (_: Exception) {
            }
            synchronized(liveAlerts) { liveAlerts.clear() }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        syncActiveNotifications()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        syncActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        syncActiveNotifications()
    }

    fun syncActiveNotifications() {
        try {
            val list = activeNotifications ?: return
            val fmt = SimpleDateFormat("h:mm a", Locale.getDefault())
            val pm = packageManager
            val updated = ArrayList<RealAlert>()

            for (sbn in list) {
                if (sbn.packageName == packageName) continue
                val extras = sbn.notification?.extras ?: continue
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
                val body = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim().orEmpty()
                if (title.isEmpty() && body.isEmpty()) continue

                val appLabel = try {
                    val ai = pm.getApplicationInfo(sbn.packageName, 0)
                    pm.getApplicationLabel(ai).toString()
                } catch (_: Exception) {
                    sbn.packageName.substringAfterLast('.')
                }

                updated.add(
                    RealAlert(
                        key = sbn.key,
                        appName = appLabel,
                        title = title.ifEmpty { appLabel },
                        text = body.ifEmpty { "New notification from $appLabel" },
                        timeStr = fmt.format(Date(sbn.postTime))
                    )
                )
            }
            synchronized(liveAlerts) {
                liveAlerts.clear()
                liveAlerts.addAll(updated)
            }
        } catch (_: Exception) {
        }
    }
}
