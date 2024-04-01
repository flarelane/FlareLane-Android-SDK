package com.flarelane

import android.os.Bundle

interface InteractionClass {
    fun toHashMap(): HashMap<String, Any?>
    fun toBundle(): Bundle
}
