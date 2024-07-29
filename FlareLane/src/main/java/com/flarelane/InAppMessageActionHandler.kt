package com.flarelane

interface InAppMessageActionHandler {
    fun onExecute(iam: InAppMessage, actionId: String)
}
