package com.beatnova.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray

internal data class BeatNovaSong(val id: String, val title: String, val artist: String, val audioUrl: String)

object SongRepository {
    private val client = OkHttpClient()
    suspend fun load(): Result<List<BeatNovaSong>> = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        if (base.isBlank() || key.isBlank()) return@withContext Result.failure(Exception("SUPABASE_CONFIG"))
        val urls = listOf(
            "$base/rest/v1/songs?select=id,title,artist,audio_url&order=created_at.desc",
            "$base/rest/v1/songs?select=id,title,artist,audio_url"
        )
        var lastError = "HTTP_ERROR"
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).addHeader("apikey", key).addHeader("Accept", "application/json").build()
                client.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) { lastError = "HTTP_${response.code}:" + raw.take(160); return@use }
                    val array = JSONArray(raw)
                    val result = mutableListOf<BeatNovaSong>()
                    for (i in 0 until array.length()) {
                        val o = array.getJSONObject(i)
                        val audio = o.optString("audio_url")
                        if (audio.isNotBlank()) result += BeatNovaSong(o.optString("id", i.toString()), o.optString("title", "بدون نام"), o.optString("artist", "هنرمند ناشناس"), audio)
                    }
                    return@withContext Result.success(result)
                }
            } catch (e: Exception) { lastError = e.message ?: "REQUEST_FAILED" }
        }
        Result.failure(Exception(lastError))
    }
}
