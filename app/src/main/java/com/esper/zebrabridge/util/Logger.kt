package com.esper.authapp.util

import android.util.Log

object Logger {
    private const val PREFIX = "ZebraBridge"

    fun d(tag: String, message: String) {
        Log.d("$PREFIX:$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$PREFIX:$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$PREFIX:$tag", message, throwable)
    }
}
