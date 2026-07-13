package com.flarelane

import com.flarelane.HTTPClient.ResponseHandler
import com.flarelane.model.ModelInAppMessage
import org.json.JSONObject

internal object InAppService {
    var isDisplaying = false

    @JvmStatic
    fun getMessage(projectId: String, deviceId: String, group: String, data: JSONObject, callback: (ModelInAppMessage?) -> Unit) {
        // Every exit path must invoke `callback` exactly once so the TaskQueueManager's
        // completeTask() is called and the queue can advance without waiting for TIMEOUT_MS.
        if (isDisplaying) {
            Logger.verbose("IAM is already displaying.")
            callback.invoke(null)
            return
        }
        isDisplaying = true

        val body = JSONObject()
            .put("group", group)
            .put("data", data)

        HTTPClient.post(
            "internal/v1/projects/$projectId/devices/$deviceId/in-app-messages?group=$group",
            body,
            object : ResponseHandler() {
                override fun onSuccess(responseCode: Int, response: JSONObject) {
                    isDisplaying = false
                    callback.invoke(parseFirstMessage(response))
                }

                override fun onFailure(responseCode: Int, response: JSONObject) {
                    isDisplaying = false
                    Logger.error("getMessage onFailure: code=$responseCode body=$response")
                    callback.invoke(null)
                }
            })
    }

    // Extracted for testability: no I/O, no state mutation. Returns null on empty data
    // or malformed payload — callers use that as the "nothing to show" signal.
    internal fun parseFirstMessage(response: JSONObject): ModelInAppMessage? {
        return try {
            val jsonArray = response.getJSONArray("data")
            if (jsonArray.length() == 0) {
                Logger.verbose("There is no displayable IAM")
                return null
            }
            val jsonObject = jsonArray.getJSONObject(0)
            ModelInAppMessage(
                id = jsonObject.getString("id"),
                htmlString = jsonObject.getString("htmlString")
            )
        } catch (e: Exception) {
            BaseErrorHandler.handle(e)
            null
        }
    }
}
