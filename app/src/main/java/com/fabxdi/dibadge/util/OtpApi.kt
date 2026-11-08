package com.fabxdi.dibadge.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the diBadge OTP Worker. The app never sees the real code; the Worker emails and checks it. */
object OtpApi {
    /** The address of the deployed dibadge-otp Worker, no trailing slash. */
    const val BASE_URL = "https://dibadge-otp.dibadge.workers.dev"

    sealed class SendResult {
        object Sent : SendResult()
        data class Failed(val message: String) : SendResult()
    }

    sealed class VerifyResult {
        object Ok : VerifyResult()
        data class Wrong(val triesLeft: Int) : VerifyResult()
        object Locked : VerifyResult()
        object Expired : VerifyResult()
        data class Failed(val message: String) : VerifyResult()
    }

    private const val NO_CONNECTION = "Couldn't reach the server. Check your connection and try again."

    suspend fun send(email: String): SendResult = withContext(Dispatchers.IO) {
        val res = post("/otp/send", JSONObject().put("email", email)) ?: return@withContext SendResult.Failed(NO_CONNECTION)
        if (res.optBoolean("ok")) return@withContext SendResult.Sent
        SendResult.Failed(
            when (res.optString("error")) {
                "too_soon" -> "Please wait a few seconds before asking for another code."
                "too_many" -> "Too many codes requested. Please try again later."
                "bad_email" -> "Enter a valid email address"
                else -> "We couldn't send the email. Please try again."
            }
        )
    }

    suspend fun verify(email: String, code: String): VerifyResult = withContext(Dispatchers.IO) {
        val res = post("/otp/verify", JSONObject().put("email", email).put("code", code))
            ?: return@withContext VerifyResult.Failed(NO_CONNECTION)
        if (res.optBoolean("ok")) return@withContext VerifyResult.Ok
        when (res.optString("error")) {
            "wrong" -> VerifyResult.Wrong(res.optInt("triesLeft", 0))
            "locked" -> VerifyResult.Locked
            "expired" -> VerifyResult.Expired
            else -> VerifyResult.Failed("Couldn't check the code. Please try again.")
        }
    }

    /** Returns the JSON reply (also for 4xx/5xx replies), or null when the server could not be reached. */
    private fun post(path: String, body: JSONObject): JSONObject? {
        return try {
            val conn = URL(BASE_URL + path).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            conn.disconnect()
            if (text.isBlank()) JSONObject() else JSONObject(text)
        } catch (e: Exception) {
            null
        }
    }
}
