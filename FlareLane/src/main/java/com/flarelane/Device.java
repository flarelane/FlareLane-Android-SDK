package com.flarelane;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

class Device {
    @NonNull
    public String id;

    @Nullable
    public String userId;

    public Device(@NonNull String id, @Nullable String userId) {
        this.id = id;
        this.userId = userId;
    }
}
