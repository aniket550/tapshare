package com.aniket.tapshare

import android.app.Application
import android.content.Context

class TapShareApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Manual DI: every screen and the service get their objects from here. */
val Context.appContainer: AppContainer
    get() = (applicationContext as TapShareApp).container
