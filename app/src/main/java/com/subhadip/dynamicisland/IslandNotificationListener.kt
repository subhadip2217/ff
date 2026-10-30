package com.subhadip.dynamicisland

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.content.Intent

class IslandNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val n = sbn?.notification ?: return
        val extras = n.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = (extras.getCharSequence("android.text") ?: extras.getCharSequence("android.bigText"))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        sendBroadcast(Intent(IslandOverlayService.ACTION_NOTIFICATION).apply {
            setPackage(packageName)
            putExtra("title", title.take(80))
            putExtra("text", text.take(140))
        })
    }
    override fun onListenerConnected() {
        super.onListenerConnected()
        sendBroadcast(Intent(IslandOverlayService.ACTION_LISTENER_READY).setPackage(packageName))
    }
}
