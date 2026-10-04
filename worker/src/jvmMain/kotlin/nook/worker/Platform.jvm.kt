package nook.worker

actual fun parseIsoMillis(iso: String): Long = java.time.Instant.parse(iso).toEpochMilli()
