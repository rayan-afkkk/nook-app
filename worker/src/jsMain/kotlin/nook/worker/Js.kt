package nook.worker

import kotlinx.coroutines.await
import kotlin.js.Promise

// Thin, dynamic bindings to the Workers runtime (fetch, WebCrypto, Response, globals).

actual fun parseIsoMillis(iso: String): Long = (js("Date").parse(iso) as Double).toLong()

fun nowSeconds(): Long = ((js("Date").now() as Double) / 1000).toLong()

fun jsObject(vararg pairs: Pair<String, Any?>): dynamic {
    val o: dynamic = js("({})")
    for ((k, v) in pairs) o[k] = v
    return o
}

/** Service-worker format: vars and secrets are globals. Missing ones read as "". */
fun env(name: String): String = (js("globalThis")[name] as? String).orEmpty()

class HttpResponse(val status: Int, val text: String) {
    val ok get() = status in 200..299
}

suspend fun httpFetch(url: String, method: String = "GET", headers: Map<String, String> = emptyMap(), body: String? = null): HttpResponse {
    val h: dynamic = js("({})")
    headers.forEach { (k, v) -> h[k] = v }
    val init = jsObject("method" to method, "headers" to h)
    if (body != null) init.body = body
    val res: dynamic = (js("fetch")(url, init) as Promise<dynamic>).await()
    val text = (res.text() as Promise<String>).await()
    return HttpResponse(res.status as Int, text)
}

fun formEncode(params: Map<String, String>): String {
    val enc = js("encodeURIComponent")
    return params.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
}

// ---------- bytes ↔ JS ----------

fun ByteArray.toUint8Array(): dynamic {
    val n = size
    val arr: dynamic = js("new Uint8Array(n)")
    for (i in indices) arr[i] = this[i].toInt() and 0xff
    return arr
}

fun bufferToBytes(buf: dynamic): ByteArray {
    val u: dynamic = js("new Uint8Array(buf)")
    val len = u.length as Int
    return ByteArray(len) { (u[it] as Int).toByte() }
}

private val subtle: dynamic get() = js("crypto.subtle")

// ---------- WebCrypto ----------

suspend fun sha1Hex(input: String): String {
    val buf = (subtle.digest("SHA-1", input.encodeToByteArray().toUint8Array()) as Promise<dynamic>).await()
    return hex(bufferToBytes(buf))
}

suspend fun hmacSha256(secret: String, data: String): ByteArray {
    val algo = jsObject("name" to "HMAC", "hash" to "SHA-256")
    val key = (subtle.importKey("raw", secret.encodeToByteArray().toUint8Array(), algo, false, arrayOf("sign")) as Promise<dynamic>).await()
    val sig = (subtle.sign("HMAC", key, data.encodeToByteArray().toUint8Array()) as Promise<dynamic>).await()
    return bufferToBytes(sig)
}

private fun rsaAlgo() = jsObject("name" to "RSASSA-PKCS1-v1_5", "hash" to "SHA-256")

/** RS256 signature with a PEM PKCS#8 private key (service accounts). */
suspend fun rs256Sign(pkcs8Pem: String, data: String): ByteArray {
    val body = pkcs8Pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
        .replace("\\n", "").filterNot { it.isWhitespace() }
    val der = base64Decode(body).toUint8Array()
    val key = (subtle.importKey("pkcs8", der, rsaAlgo(), false, arrayOf("sign")) as Promise<dynamic>).await()
    val sig = (subtle.sign("RSASSA-PKCS1-v1_5", key, data.encodeToByteArray().toUint8Array()) as Promise<dynamic>).await()
    return bufferToBytes(sig)
}

/** RS256 verification with a JWK public key (Firebase ID tokens). */
suspend fun rs256Verify(jwkJson: String, data: String, signature: ByteArray): Boolean {
    val jwk: dynamic = js("JSON").parse(jwkJson)
    val key = (subtle.importKey("jwk", jwk, rsaAlgo(), false, arrayOf("verify")) as Promise<dynamic>).await()
    return (subtle.verify("RSASSA-PKCS1-v1_5", key, signature.toUint8Array(), data.encodeToByteArray().toUint8Array()) as Promise<Boolean>).await()
}
