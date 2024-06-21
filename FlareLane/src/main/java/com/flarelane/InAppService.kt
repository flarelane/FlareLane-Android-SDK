package com.flarelane

import com.flarelane.HTTPClient.ResponseHandler
import org.json.JSONObject

object InAppService {
    @JvmStatic
    internal fun getMessage(projectId: String, deviceId: String, callback: (String) -> Unit) {
        HTTPClient.get(
            "internal/v1/projects/$projectId/devices/$deviceId/in-app-messages",
            object : ResponseHandler() {
                override fun onSuccess(responseCode: Int, response: JSONObject) {
                    try {
                        val jsonArray = response.getJSONArray("data")
                        val htmlString = jsonArray.getJSONObject(0).getString("htmlString")
                        callback.invoke(htmlString)
                    } catch (e: Exception) {
                        BaseErrorHandler.handle(e)
                    }
                }
            })
    }
}
