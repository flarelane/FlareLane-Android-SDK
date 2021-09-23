package com.flarelane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Notification {
    @NonNull
    public String id;
    @Nullable
    public String title;
    @NonNull
    public String body;
    @Nullable
    public String url;

    public Notification(@NonNull String id, @NonNull String body, @Nullable String title, @Nullable String url) {
        this.id = id;
        this.body = body;
        this.title = title;
        this.url = url;
    }

    @Override
    public String toString() {
        return "Notification{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", url='" + url + '\'' +
                '}';
    }
}
