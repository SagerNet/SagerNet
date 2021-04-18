package io.nekohasekai.sagernet.ktx

import kotlinx.coroutines.*

fun runOnIoDispatcher(block: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.IO, block = block)

suspend fun onIoDispatcher(block: suspend CoroutineScope.() -> Unit) =
    withContext(Dispatchers.IO, block = block)

fun runOnMainDispatcher(block: suspend CoroutineScope.() -> Unit) =
    GlobalScope.launch(Dispatchers.Main, block = block)

suspend fun onMainDispatcher(block: suspend CoroutineScope.() -> Unit) =
    withContext(Dispatchers.Main, block = block)