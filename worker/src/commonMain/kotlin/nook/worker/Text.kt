package nook.worker

/**
 * Notification copy. Deliberately content-free: it says WHO and WHAT KIND, never what was said.
 *   DM:    "Ali sent you a message" / "Ali sent you a photo"
 *   Group: "New message in The Boys"
 */
data class NotificationText(val title: String, val body: String)

private val KIND = mapOf(
    "text" to "a message",
    "image" to "a photo",
    "file" to "a file",
    "voice" to "a voice message",
    "gif" to "a GIF",
    "sticker" to "a sticker",
)

fun notificationText(senderName: String, messageType: String, isGroup: Boolean, groupName: String?): NotificationText {
    val sender = firstName(senderName).ifEmpty { "Someone" }
    if (isGroup) {
        val group = groupName?.trim().orEmpty().ifEmpty { "your group" }
        return NotificationText(group, "New message in $group")
    }
    return NotificationText("Nook", "$sender sent you ${KIND[messageType] ?: "a message"}")
}

fun firstName(name: String): String = name.trim().split(Regex("\\s+")).firstOrNull().orEmpty()

val MESSAGE_TYPES = setOf("text", "image", "file", "voice", "gif", "sticker")
