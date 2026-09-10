package com.beatnova.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AuthHttp {
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    data class SignUpResult(val sessionImported: Boolean)

    private suspend fun post(path: String, email: String, password: String): JSONObject = withContext(Dispatchers.IO) {
        val base = BuildConfig.SUPABASE_URL.trimEnd('/')
        val key = BuildConfig.SUPABASE_ANON_KEY
        require(base.isNotBlank() && key.isNotBlank()) { "پیکربندی Supabase ناقص است." }
        val body = JSONObject().put("email", email).put("password", password).toString().toRequestBody(jsonType)
        val request = Request.Builder()
            .url("$base/auth/v1/$path")
            .addHeader("apikey", key)
            .addHeader("Authorization", "Bearer $key")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val message = json.optString("msg").ifBlank { json.optString("message") }
                val code = json.optString("error_code")
                throw Exception(listOf(code, message).filter { it.isNotBlank() }.joinToString(": "))
            }
            json
        }
    }

    suspend fun signUp(email: String, password: String): SignUpResult {
        val json = post("signup", email, password)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        if (access.isNotBlank() && refresh.isNotBlank()) {
            supabase.auth.importAuthToken(access, refresh)
            return SignUpResult(true)
        }
        return SignUpResult(false)
    }

    suspend fun signIn(email: String, password: String) {
        val json = post("token?grant_type=password", email, password)
        val access = json.optString("access_token")
        val refresh = json.optString("refresh_token")
        require(access.isNotBlank() && refresh.isNotBlank()) { "پاسخ ورود Supabase ناقص است." }
        supabase.auth.importAuthToken(access, refresh)
    }
}
