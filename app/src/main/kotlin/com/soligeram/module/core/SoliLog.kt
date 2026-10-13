package com.soligeram.module.core

import android.util.Log

object SoliLog {
    private const val TAG = "SoliGeram"

    fun i(tag: String, msg: String) = Log.i("$TAG/$tag", msg)
    fun d(tag: String, msg: String) = Log.d("$TAG/$tag", msg)
    fun w(tag: String, msg: String) = Log.w("$TAG/$tag", msg)
    fun e(tag: String, msg: String) = Log.e("$TAG/$tag", msg)
    fun e(tag: String, msg: String, t: Throwable) = Log.e("$TAG/$tag", msg, t)
}
