package com.xyz.clipcutter

import android.app.Application

class ClipCutterApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.install(this)
    }
}
