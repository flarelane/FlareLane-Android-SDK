package com.flarelane;

import com.flarelane.notification.NotificationClickEvent;

public interface NotificationClickedHandler {
    void onClicked(NotificationClickEvent event);
}
