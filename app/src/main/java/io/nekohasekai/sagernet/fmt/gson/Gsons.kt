package io.nekohasekai.sagernet.fmt.gson

import com.google.gson.GsonBuilder

val gson = GsonBuilder()
    .setPrettyPrinting()
    .registerTypeAdapterFactory(JsonOrAdapterFactory())
    .registerTypeAdapterFactory(JsonLazyFactory())
    .create()