package com.esper.authapp

import android.app.Application
import com.esper.authapp.config.AppConfig

class AuthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppConfig.init(this)
    }
}
