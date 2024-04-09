package com.flarelane.notification

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat.MessagingStyle
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.flarelane.Notification
import com.flarelane.util.FileUtil

internal class NotificationMessaging(
    override val context: Context,
    override val notification: Notification,
    override val notificationStyle: NotificationStyle.Messaging
) : NotificationCreator<NotificationStyle.Messaging>() {
    override val requestContentId = 1
    override val notifyTag = notificationStyle.tag

    override fun notify() {
        val icon =
            IconCompat.createWithBitmap(FileUtil.downloadImageToBitmap(notificationStyle.iconUrl)!!)

        val user = Person.Builder().setName("-").build()
        val person = Person.Builder().setName(notificationStyle.sender).setIcon(icon).build()

        createShortcut(person)
        builder.setShortcutId(notificationStyle.sender)
            .setStyle(
                MessagingStyle(user).also {
                    it.addMessage(
                        notificationStyle.message,
                        System.currentTimeMillis(),
                        person
                    )
                }
            )
            .addPerson(person)

        super.notify()
        removeShortcut()
    }

    private fun createShortcut(person: Person) {
        val shortcut = ShortcutInfoCompat.Builder(context, notificationStyle.tag)
            .setLongLived(true)
            .setShortLabel(notificationStyle.sender)
            .setIcon(person.icon)
            .setPerson(person)
            .setIntent(
                context.packageManager.getLaunchIntentForPackage(context.packageName)!!.also {
                    it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
            )
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }

    private fun removeShortcut() {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(notificationStyle.sender))
    }
}
