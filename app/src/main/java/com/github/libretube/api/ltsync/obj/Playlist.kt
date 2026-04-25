package com.github.libretube.api.ltsync.obj

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class Playlist (

    @SerialName(value = "account_id")
    val accountId: String,

    @SerialName(value = "description")
    val description: String,

    @SerialName(value = "id")
    val id: String,

    @SerialName(value = "title")
    val title: String,

    @SerialName(value = "thumbnail_url")
    val thumbnailUrl: String? = null

) {


}

