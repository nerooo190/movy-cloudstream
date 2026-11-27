package de.movy.cloudstream

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class MovyPlugin : Plugin() {
    override fun load(context: Context) {
        val server = MovyServer(context.getSharedPreferences("movy_resolver", Context.MODE_PRIVATE))
        openSettings = server::showSettings
        registerMainAPI(MovyProvider(MovyPlayback(context.applicationContext), server))
    }
}
