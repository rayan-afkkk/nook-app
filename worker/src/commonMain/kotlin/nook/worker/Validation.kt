package nook.worker

class HttpError(val status: Int, override val message: String) : Exception(message)

private val ID = Regex("^[A-Za-z0-9_-]{1,128}$")

/** Document ids we accept in Firestore paths: no slashes, no traversal. */
fun validId(value: Any?): String {
    val s = value as? String ?: throw HttpError(400, "bad id")
    if (!ID.matches(s)) throw HttpError(400, "bad id")
    return s
}

/** Only Cloudinary assets inside the folder the caller is allowed to touch. */
fun isAllowedAsset(publicId: String, prefix: String): Boolean =
    publicId.startsWith(prefix) && ".." !in publicId

val RESOURCE_TYPES = setOf("image", "video", "raw")

/** Cloudinary signature input: params sorted by key, joined with &, then the API secret appended. */
fun cloudinaryStringToSign(params: Map<String, String>, apiSecret: String): String =
    params.keys.sorted().joinToString("&") { "$it=${params[it]}" } + apiSecret
