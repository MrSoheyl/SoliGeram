package com.soligeram.module

import android.app.Application
import com.soligeram.module.service.PrefManager

class SoliApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PrefManager.init(this)
    }
}
