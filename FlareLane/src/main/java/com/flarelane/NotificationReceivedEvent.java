package com.flarelane;

public class NotificationReceivedEvent {
    private Notification notification;

    public NotificationReceivedEvent(Notification notification) {
        this.notification = notification;
    }

    public Notification getNotification() {
        return notification;
    }
}
