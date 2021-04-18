package io.nekohasekai.sagernet.fmt.gson

import com.google.gson.GsonBuilder

val gson = GsonBuilder()
    .registerTypeAdapterFactory(JsonOrAdapterFactory())
    .registerTypeAdapterFactory(JsonLazyFactory())
    .create()