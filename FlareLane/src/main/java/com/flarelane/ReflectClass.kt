package com.flarelane

import android.os.Bundle
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

open class ReflectClass {
    fun toMap(): Map<String, Any?> {
        return this@ReflectClass::class.primaryConstructor?.parameters?.associateWith { param ->
            val property = this@ReflectClass::class.memberProperties.firstOrNull {
                it.name == param.name
            }
            property?.getter?.call(this@ReflectClass)
        }?.mapKeys { (param, _) ->
            param.name!!
        } ?: emptyMap()
    }

    fun toHashMap(): HashMap<String, Any?> {
        return HashMap<String, Any?>().apply {
            this@ReflectClass::class.primaryConstructor?.parameters?.forEach { param ->
                val property = this@ReflectClass::class.memberProperties.firstOrNull {
                    it.name == param.name
                }
                put(param.name!!, property?.getter?.call(this@ReflectClass))
            }
        }
    }

    fun toBundle(): Bundle {
        val bundle = Bundle()
        this@ReflectClass::class.primaryConstructor?.parameters?.forEach { param ->
            val property = this@ReflectClass::class.memberProperties.firstOrNull {
                it.name == param.name
            }
            property?.getter?.call(this@ReflectClass).let {
                when (param.type.classifier) {
                    Boolean::class -> {
                        if (it != null) {
                            bundle.putBoolean(param.name!!, it as Boolean)
                        }
                    }

                    String::class -> {
                        bundle.putString(param.name!!, it as? String)
                    }

                    Int::class -> {
                        if (it != null) {
                            bundle.putInt(param.name!!, it as Int)
                        }
                    }

                    Long::class -> {
                        if (it != null) {
                            bundle.putLong(param.name!!, it as Long)
                        }
                    }

                    Float::class -> {
                        if (it != null) {
                            bundle.putFloat(param.name!!, it as Float)
                        }
                    }

                    Double::class -> {
                        if (it != null) {
                            bundle.putDouble(param.name!!, it as Double)
                        }
                    }
                }
            }
        }
        return bundle
    }
}
