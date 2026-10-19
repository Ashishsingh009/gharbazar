package com.ashish.gharbazaar

import android.app.Application
import com.ashish.gharbazaar.shell.AppGraph

class GharBazaarApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
