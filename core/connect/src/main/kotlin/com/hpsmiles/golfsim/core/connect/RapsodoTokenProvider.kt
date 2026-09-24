package com.hpsmiles.golfsim.core.connect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Result of a token fetch. */
sealed interface TokenResult {
    data class Success(val token: Int) : TokenResult
    data class Failure(val reason: String) : TokenResult
}

/** Fetches the ~3-hour simulator token for a user id using the account Secret. */
interface RapsodoTokenProvider {
    suspend fun fetch(userId: Int, secret: String): TokenResult
}

/**
 * HTTP implementation. Endpoint and header per the reverse-engineered spec
 * (`mlm2pro.md`): GET /api/simulator/user/{id}, header `Secret: <secret>`,
 * response carries a JSON body with the token as a 32-bit integer — the exact
 * response SHAPE is a Phase B verification item; this parser is deliberately
 * tolerant (first "token" JSON field, numeric).
 */
class HttpRapsodoTokenProvider : RapsodoTokenProvider {

    override suspend fun fetch(userId: Int, secret: String): TokenResult =
        withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL("$BASE_URL/user/$userId").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Secret", secret)
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val code = connection.responseCode
                if (code != 200) {
                    return@withContext TokenResult.Failure("HTTP $code")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val token = parseToken(body)
                if (token == null) {
                    // Bench diagnostics: response-shape drift must be visible
                    // in logcat, not a bare "token missing".
                    android.util.Log.w("BenchToken", "unparsed response body: $body")
                    return@withContext TokenResult.Failure("token missing in response")
                }
                TokenResult.Success(token)
            } catch (e: SecurityException) {
                // Bench attempt 4: a missing INTERNET manifest permission used
                // to escape this fetch and crash the whole app — surface it.
                TokenResult.Failure(e.message ?: "no network permission")
            } catch (e: IOException) {
                TokenResult.Failure(e.message ?: "network error")
            } finally {
                connection?.disconnect()
            }
        }

    companion object {
        const val BASE_URL = "https://mlm.rapsodo.com/api/simulator"

        /** Minimal tolerant parse: the first `"token": <int>` occurrence.
         *  The token is an UNSIGNED 32-bit value on the wire (CommandEncoder
         *  emits it LE32); decode via Long and keep the exact bit pattern. */
        fun parseToken(body: String): Int? {
            val idx = body.indexOf("\"token\"")
            if (idx < 0) return null
            val tail = body.substring(idx)
            // The real response ships the token as a QUOTED numeric string
            // ("token":"1043255814") per the reference spec (Duwaynef
            // mlm2pro.md) — accept quoted and unquoted integers, unsigned-32.
            val match = Regex("\"token\"\\s*:\\s*\"?(-?\\d+)\"?").find(tail) ?: return null
            val raw = match.groupValues[1].toLongOrNull() ?: return null
            if (raw < 0L || raw > 0xFFFF_FFFFL) return null
            return raw.toInt()
        }
    }
}
