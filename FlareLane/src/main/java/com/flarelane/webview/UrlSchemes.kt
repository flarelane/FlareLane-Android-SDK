package com.flarelane.webview

enum class UrlSchemes(val value: String) {
    HTTP("http"),
    HTTPS("https"),
    TEL("tel"),
    MAIL_TO("mailto"),
    MARKET("market"),
    DATA("data"),
    INTENT("intent"),
    CUSTOM("custom");

    companion object {
        fun of(schemeString: String?): UrlSchemes? {
            return schemeString?.let {
                values().find { it.value == schemeString } ?: CUSTOM
            }
        }
    }
}
