package com.flarelane

import com.flarelane.HTTPClient.ResponseHandler
import com.flarelane.model.ModelInAppMessage
import org.json.JSONObject

internal object InAppService {
    var isDisplaying = false

    @JvmStatic
    fun getMessage(projectId: String, deviceId: String, group: String, callback: (ModelInAppMessage?) -> Unit) {
        if (isDisplaying) {
            Logger.verbose("IAM is already displaying.")
            return
        }
        isDisplaying = true

        HTTPClient.post(
            "internal/v1/projects/$projectId/devices/$deviceId/in-app-messages?group=$group",
            null,
            object : ResponseHandler() {
                override fun onSuccess(responseCode: Int, response: JSONObject) {
                    try {
                        isDisplaying = false
                        val jsonArray = response.getJSONArray("data")
                        if (jsonArray.length() > 0) {
                            val jsonObject = jsonArray.getJSONObject(0)
                            val model = ModelInAppMessage(
                                id = jsonObject.getString("id"),
                                htmlString = jsonObject.getString("htmlString")
                            )
                            callback.invoke(model)
                        } else {
                            Logger.verbose("There is no displayable IAM")
                        }
                    } catch (e: Exception) {
                        BaseErrorHandler.handle(e)
                    }
                }
            })
    }
}
