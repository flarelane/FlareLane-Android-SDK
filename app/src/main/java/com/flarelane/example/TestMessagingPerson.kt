package com.flarelane.example

abstract class TestMessagingPerson(
    val name: String,
    val iconUrl: String,
    var messageCount: Int = 0,
) {
    companion object {
        val PERSON_LIST = listOf(
            object : TestMessagingPerson(
                "민호",
                "https://cdn-icons-png.flaticon.com/512/4042/4042356.png"
            ) {},
            object : TestMessagingPerson(
                "피터",
                "https://cdn-icons-png.flaticon.com/512/9308/9308310.png"
            ) {},
            object : TestMessagingPerson(
                "셀리",
                "https://cdn-icons-png.flaticon.com/512/206/206872.png"
            ) {}
        )
    }
}
