package io.nekohasekai.sagernet.ktx

import kotlinx.coroutines.*

fun runOnDefaultDispatcher(block: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.Default, block = block)

suspend fun onDefaultDispatcher(block: suspend CoroutineScope.() -> Unit) =
    withContext(Dispatchers.Default, block = block)

fun runOnMainDispatcher(block: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.Main, block = block)

suspend fun onMainDispatcher(block: suspend CoroutineScope.() -> Unit) =
    withContext(Dispatchers.Main, block = block)