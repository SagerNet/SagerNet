package io.nekohasekai.sagernet.ktx

import android.util.Log
import cn.hutool.core.util.StrUtil
import io.nekohasekai.sagernet.BuildConfig

object Logs {

    private fun mkTag(): String {
        val stackTrace = Thread.currentThread().stackTrace
        return StrUtil.subAfter(stackTrace[4].className, ".", true)
    }

    fun v(message: String) {
        if (BuildConfig.DEBUG) {
            Log.v(mkTag(), message)
        }
    }

    fun v(message: String, exception: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.v(mkTag(), message, exception)
        }
    }

    fun d(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(mkTag(), message)
        }
    }

    fun d(message: String, exception: Throwable) {
        if (BuildConfig.DEBUG) {
            Log.d(mkTag(), message, exception)
        }
    }

    fun i(message: String) {
        Log.i(mkTag(), message)
    }

    fun i(message: String, exception: Throwable) {
        Log.i(mkTag(), message, exception)
    }

    fun w(message: String) {
        Log.w(mkTag(), message)
    }

    fun w(message: String, exception: Throwable) {
        Log.w(mkTag(), message, exception)
    }

    fun w(exception: Throwable) {
        Log.w(mkTag(), exception)
    }

    fun e(message: String) {
        Log.e(mkTag(), message)
    }

    fun e(message: String, exception: Throwable) {
        Log.e(mkTag(), message, exception)
    }

}