package com.flarelane;

import static java.lang.Integer.parseInt;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;

public class Notification {
    @NonNull
    public String id;
    @Nullable
    public String title;
    @NonNull
    public String body;
    @Nullable
    public String url;
    @Nullable
    public String imageUrl;
    @NonNull
    public String data;

    public Notification(
            @NonNull String id,
            @NonNull String body,
            @NonNull String data,
            @Nullable String title,
            @Nullable String url,
            @Nullable String imageUrl
    ) {
        this.id = id;
        this.body = body;
        this.data = data;
        this.title = title;
        this.url = url;
        this.imageUrl = imageUrl;
    }

    public Notification(@NonNull JSONObject jsonObject) throws JSONException {
        this(
                jsonObject.getString("notificationId"),
                jsonObject.getString("body"),
                jsonObject.getString("data"),
                jsonObject.has("title") ? jsonObject.getString("title") : null,
                jsonObject.has("url") ? jsonObject.getString("url") : null,
                jsonObject.has("imageUrl") ? jsonObject.getString("imageUrl") : null
        );
    }

    @Override
    public String toString() {
        return "Notification{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", body='" + body + '\'' +
                ", url='" + url + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", data='" + data + '\'' +
                '}';
    }

    public HashMap<String, Object> toHashMap() {
        HashMap<String, Object> hash = new HashMap<>();
        hash.put("id", id);
        hash.put("title", title);
        hash.put("body", body);
        hash.put("url", url);
        hash.put("imageUrl", imageUrl);
        hash.put("data", data);

        return hash;
    }

    public Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("id", id);
        bundle.putString("title", title);
        bundle.putString("body", body);
        bundle.putString("url", url);
        bundle.putString("imageUrl", imageUrl);
        bundle.putString("data", data);

        return bundle;
    }
}
