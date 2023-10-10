package com.flarelane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class Device {
    @NonNull
    public String id;

    @Nullable
    public String userId;

    @Nullable
    public boolean isSubscribed;


    public Device(@NonNull String id, @Nullable boolean isSubscribed, @Nullable String userId) {
        this.id = id;
        this.userId = userId;
        this.isSubscribed = isSubscribed;
    }
}
