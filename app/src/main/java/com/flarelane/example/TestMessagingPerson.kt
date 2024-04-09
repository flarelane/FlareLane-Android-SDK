package com.flarelane.example

abstract class TestMessagingPerson(
    val tag: String,
    val name: String,
    val iconUrl: String,
    var messageCount: Int = 0,
) {
    companion object {
        val PERSON_LIST = listOf(
            object : TestMessagingPerson(
                "test1",
                "민호",
                "https://cdn-icons-png.flaticon.com/512/4042/4042356.png"
            ) {},
            object : TestMessagingPerson(
                "test2",
                "피터",
                "https://cdn-icons-png.flaticon.com/512/9308/9308310.png"
            ) {},
            object : TestMessagingPerson(
                "test3",
                "셀리",
                "https://cdn-icons-png.flaticon.com/512/206/206872.png"
            ) {}
        )
    }
}
