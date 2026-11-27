package de.movy.cloudstream

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.text.InputType
import android.widget.EditText
import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException

internal class MovyServer(private val preferences: SharedPreferences) {
    fun showSettings(context: Context) {
        val urlInput = EditText(context).apply {
            hint = "https://…/manifest.json"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setSingleLine()
            setText(preferences.getString(SERVER_URL, null).orEmpty())
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Movy-Server")
            .setMessage("Gib die Manifest-URL deines Movy-Servers ein. Ein leeres Feld nutzt die WebView.")
            .setView(urlInput)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Speichern", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val value = urlInput.text.toString().trim()
                val url = value.toHttpUrlOrNull()
                if (value.isNotEmpty() && (url == null || url.username.isNotEmpty() || url.password.isNotEmpty())) {
                    urlInput.error = "Gib eine gültige HTTP- oder HTTPS-Adresse ein."
                    return@setOnClickListener
                }
                val base = url?.newBuilder()
                    ?.encodedPath(url.encodedPath.trimEnd('/').removeSuffix("/manifest.json").ifEmpty { "/" })
                    ?.query(null)?.fragment(null)?.build()?.toString()
                preferences.edit().putString(SERVER_URL, base).apply()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    suspend fun resolve(path: String): MovyPlayback.Result? {
        val base = preferences.getString(SERVER_URL, null)?.toHttpUrlOrNull() ?: return null
        val url = base.newBuilder()
            .encodedPath(base.encodedPath.trimEnd('/') + "/resolve" + path)
            .build()
        val response = try {
            app.get(url.toString(), timeout = 18)
        } catch (_: IOException) {
            return null
        }
        if (!response.isSuccessful) return null

        val stream = tryParseJson<JsonNode>(response.text)?.path("stream") ?: return null
        val media = stream.path("url").asText().toHttpUrlOrNull() ?: return null
        if (!media.isHttps || stream.path("type").asText() !in setOf("hls", "dash")) return null
        val headers = stream.path("headers")
        val playbackHeaders = listOf("Referer", "Origin", "User-Agent").associateWith { key ->
            headers.path(key).asText().takeIf(String::isNotBlank) ?: return null
        }
        return MovyPlayback.Result(MovyPlayback.MediaRequest(media, playbackHeaders), emptyList())
    }

    private companion object {
        const val SERVER_URL = "url"
    }
}
