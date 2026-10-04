package nook.rules

import nook.rules.Emulator.OWNER
import nook.rules.Emulator.clearFirestore
import nook.rules.Emulator.fsCommit
import nook.rules.Emulator.fsDelete
import nook.rules.Emulator.fsGet
import nook.rules.Emulator.fsQuery
import org.junit.Before
import org.junit.Test

const val ALICE = "aliceUid0000000000000000001"
const val BOB = "bobUid000000000000000000002"
const val CAROL = "carolUid0000000000000000003"
fun dmId(a: String, b: String) = "dm_" + listOf(a, b).sorted().joinToString("_")

class FirestoreRulesTest {

    @Before fun reset() = clearFirestore()

    private fun seed(path: String, data: Map<String, Any?>) = assertAllowed(fsCommit(OWNER, setWrite(path, data)), "seed $path")

    private fun seedUsers() {
        for ((uid, name) in listOf(ALICE to "alice", BOB to "bob", CAROL to "carol")) {
            seed("users/$uid", mapOf("uid" to uid, "username" to name, "displayName" to name, "photoUrl" to null))
            seed("usernames/$name", mapOf("uid" to uid))
        }
    }

    private fun seedDm(): String {
        val id = dmId(ALICE, BOB)
        seed("chats/$id", mapOf("type" to "direct", "members" to listOf(ALICE, BOB).sorted(), "createdBy" to ALICE, "disappearing" to "off", "lastRead" to emptyMap<String, Any>()))
        return id
    }

    private fun seedGroup(): String {
        seed("chats/g1", mapOf("type" to "group", "name" to "The Boys", "members" to listOf(ALICE, BOB), "admins" to listOf(ALICE), "createdBy" to ALICE, "disappearing" to "off", "lastRead" to emptyMap<String, Any>()))
        return "g1"
    }

    private fun message(sender: String, vararg extra: Pair<String, Any?>): Map<String, Any?> = mapOf(
        "senderId" to sender, "type" to "text", "text" to "hi", "reactions" to emptyMap<String, Any>(), "hiddenFor" to emptyList<String>(),
        "createdAt" to ServerTime, "clientCreatedAt" to System.currentTimeMillis(), "expireAt" to null, "forwarded" to false,
    ) + extra

    private fun seededMessage(sender: String, vararg extra: Pair<String, Any?>) = message(sender, *extra) + ("createdAt" to Ts(System.currentTimeMillis()))

    /** Profile + username claim in one atomic commit, like the app's transaction. */
    private fun claim(uid: String, name: String) = fsCommit(
        uid,
        setWrite("usernames/$name", mapOf("uid" to uid, "createdAt" to ServerTime)),
        setWrite("users/$uid", mapOf("uid" to uid, "username" to name, "displayName" to "Al", "photoUrl" to null, "createdAt" to ServerTime)),
    )

    // ---------------- users & usernames ----------------

    @Test fun `a user can create a profile while claiming a free username`() = assertAllowed(claim(ALICE, "alice"))

    @Test fun `a taken username cannot be claimed by someone else`() {
        assertAllowed(claim(ALICE, "alice"))
        assertDenied(claim(BOB, "alice"))
    }

    @Test fun `invalid usernames are rejected`() {
        assertDenied(claim(ALICE, "Al"))
        assertDenied(claim(ALICE, ".alice"))
        assertDenied(claim(ALICE, "ALICE"))
    }

    @Test fun `a username cannot be changed or a second one claimed`() {
        assertAllowed(claim(ALICE, "alice"))
        assertDenied(fsCommit(ALICE, updateWrite("users/$ALICE", mapOf("username" to "alice2"))))
        assertDenied(fsCommit(ALICE, setWrite("usernames/alice2", mapOf("uid" to ALICE))))
    }

    @Test fun `users can only edit their own profile, and only safe fields`() {
        seedUsers()
        assertAllowed(fsCommit(ALICE, updateWrite("users/$ALICE", mapOf("displayName" to "Alice A"))))
        assertDenied(fsCommit(BOB, updateWrite("users/$ALICE", mapOf("displayName" to "pwned"))))
        assertDenied(fsCommit(ALICE, updateWrite("users/$ALICE", mapOf("uid" to BOB))))
    }

    @Test fun `profiles need sign-in to read`() {
        seedUsers()
        assertDenied(fsGet(null, "users/$ALICE"))
        assertAllowed(fsGet(BOB, "users/$ALICE"))
    }

    @Test fun `private settings and tokens are owner-only`() {
        assertAllowed(fsCommit(ALICE, setWrite("users/$ALICE/private/settings", mapOf("blocked" to emptyList<String>()))))
        assertDenied(fsGet(BOB, "users/$ALICE/private/settings"))
        assertAllowed(fsCommit(ALICE, setWrite("users/$ALICE/tokens/t1", mapOf("token" to "t1"))))
        assertDenied(fsGet(BOB, "users/$ALICE/tokens/t1"))
    }

    // ---------------- chats ----------------

    @Test fun `a DM can be created only with its deterministic id and by a member`() {
        val data = mapOf("type" to "direct", "members" to listOf(ALICE, BOB).sorted(), "createdBy" to ALICE, "disappearing" to "off", "lastRead" to emptyMap<String, Any>())
        assertDenied(fsCommit(ALICE, setWrite("chats/dm_wrong", data)))
        assertDenied(fsCommit(CAROL, setWrite("chats/${dmId(ALICE, BOB)}", data + ("createdBy" to CAROL))))
        assertAllowed(fsCommit(ALICE, setWrite("chats/${dmId(ALICE, BOB)}", data)))
    }

    @Test fun `you can check whether your own DM exists, but not other people's`() {
        val own = fsGet(ALICE, "chats/${dmId(ALICE, BOB)}")
        assertDenied(fsGet(CAROL, "chats/${dmId(ALICE, BOB)}"))
        // Allowed reads of a missing doc come back as 404, never as permission denied.
        kotlin.test.assertEquals(404, own)
    }

    @Test fun `only members can read a chat and its messages`() {
        val id = seedDm()
        assertAllowed(fsGet(BOB, "chats/$id"))
        assertDenied(fsGet(CAROL, "chats/$id"))
        assertDenied(fsQuery(CAROL, "chats/$id", structuredQuery("messages")))
        assertAllowed(fsQuery(ALICE, "", structuredQuery("chats", where = fieldFilter("members", "ARRAY_CONTAINS", ALICE))))
    }

    @Test fun `members can send messages, outsiders and spoofers cannot`() {
        val id = seedDm()
        assertAllowed(fsCommit(ALICE, setWrite("chats/$id/messages/m1", message(ALICE))))
        assertDenied(fsCommit(CAROL, setWrite("chats/$id/messages/m2", message(CAROL))))
        assertDenied(fsCommit(ALICE, setWrite("chats/$id/messages/m3", message(BOB))))
        assertDenied(fsCommit(ALICE, setWrite("chats/$id/messages/m4", message(ALICE, "reactions" to mapOf(BOB to "❤️")))))
    }

    @Test fun `sending updates the chat preview and read receipt in one batch`() {
        val id = seedDm()
        assertAllowed(
            fsCommit(
                ALICE,
                setWrite("chats/$id/messages/m1", message(ALICE)),
                updateWrite(
                    "chats/$id",
                    mapOf(
                        "lastMessage" to mapOf("id" to "m1", "senderId" to ALICE, "type" to "text", "preview" to "hi"),
                        "lastMessageAt" to ServerTime,
                        "lastRead.$ALICE" to ServerTime,
                    ),
                ),
            ),
        )
    }

    @Test fun `members cannot be swapped out of a DM`() {
        val id = seedDm()
        assertDenied(fsCommit(ALICE, updateWrite("chats/$id", mapOf("members" to listOf(ALICE, CAROL)))))
    }

    @Test fun `reactions only your own key, delete-for-me only yourself`() {
        val id = seedDm()
        seed("chats/$id/messages/m1", seededMessage(ALICE))
        val m = "chats/$id/messages/m1"
        assertAllowed(fsCommit(BOB, updateWrite(m, mapOf("reactions.$BOB" to "😂"))))
        assertDenied(fsCommit(BOB, updateWrite(m, mapOf("reactions.$ALICE" to "😂"))))
        assertAllowed(fsCommit(BOB, updateWrite(m, deletes = listOf("reactions.$BOB"))))
        assertAllowed(fsCommit(BOB, updateWrite(m, arrayUnion = mapOf("hiddenFor" to listOf(BOB)))))
        assertDenied(fsCommit(BOB, updateWrite(m, arrayUnion = mapOf("hiddenFor" to listOf(ALICE)))))
        assertDenied(fsCommit(BOB, updateWrite(m, mapOf("text" to "edited"))))
    }

    @Test fun `unsend only the sender, expired messages any member`() {
        val id = seedDm()
        seed("chats/$id/messages/m1", seededMessage(ALICE))
        seed("chats/$id/messages/old", seededMessage(ALICE, "expireAt" to Ts(System.currentTimeMillis() - 60_000)))
        assertDenied(fsDelete(BOB, "chats/$id/messages/m1"))
        assertAllowed(fsDelete(ALICE, "chats/$id/messages/m1"))
        assertAllowed(fsDelete(BOB, "chats/$id/messages/old"))
        seed("chats/$id/messages/old2", seededMessage(ALICE, "expireAt" to Ts(System.currentTimeMillis() - 60_000)))
        assertDenied(fsDelete(CAROL, "chats/$id/messages/old2"))
    }

    @Test fun `groups members can add people and leave, but not remove others`() {
        val id = seedGroup()
        assertAllowed(fsCommit(BOB, updateWrite("chats/$id", arrayUnion = mapOf("members" to listOf(CAROL)))))
        assertDenied(fsCommit(BOB, updateWrite("chats/$id", arrayRemove = mapOf("members" to listOf(ALICE)))))
        assertAllowed(fsCommit(BOB, updateWrite("chats/$id", deletes = listOf("lastRead.$BOB"), arrayRemove = mapOf("members" to listOf(BOB)))))
        assertDenied(fsGet(BOB, "chats/$id"))
    }

    @Test fun `only your own messages are visible to the account-deletion collection-group query`() {
        val id = seedDm()
        seed("chats/$id/messages/m1", seededMessage(ALICE))
        assertAllowed(fsQuery(ALICE, "", structuredQuery("messages", allDescendants = true, where = fieldFilter("senderId", "EQUAL", ALICE))))
        assertDenied(fsQuery(CAROL, "", structuredQuery("messages", allDescendants = true, where = fieldFilter("senderId", "EQUAL", ALICE))))
    }

    // ---------------- calls & sticker packs ----------------

    @Test fun `calls can only be placed to someone you share a chat with`() {
        val id = seedDm()
        fun call(caller: String, callee: String) = mapOf(
            "chatId" to id, "callerId" to caller, "calleeId" to callee, "members" to listOf(caller, callee),
            "type" to "voice", "status" to "ringing", "createdAt" to ServerTime,
        )
        assertAllowed(fsCommit(ALICE, setWrite("calls/c1", call(ALICE, BOB))))
        assertDenied(fsCommit(CAROL, setWrite("calls/c2", call(CAROL, BOB))))
        assertAllowed(fsCommit(BOB, updateWrite("calls/c1", mapOf("status" to "accepted", "acceptedAt" to ServerTime))))
        assertDenied(fsGet(CAROL, "calls/c1"))
        assertAllowed(fsQuery(ALICE, "", structuredQuery("calls", where = fieldFilter("members", "ARRAY_CONTAINS", ALICE), orderBy = "createdAt")))
    }

    @Test fun `sticker packs are shared with exactly the group members`() {
        val id = seedGroup()
        val pack = mapOf("chatId" to id, "name" to "Us", "createdBy" to ALICE, "members" to listOf(ALICE, BOB), "stickers" to emptyList<Any>(), "createdAt" to ServerTime)
        assertAllowed(fsCommit(ALICE, setWrite("stickerPacks/p1", pack)))
        assertDenied(fsCommit(CAROL, setWrite("stickerPacks/p2", pack + mapOf("createdBy" to CAROL, "members" to listOf(ALICE, BOB, CAROL)))))
        assertAllowed(fsCommit(BOB, updateWrite("stickerPacks/p1", arrayUnion = mapOf("stickers" to listOf(mapOf("url" to "https://x", "publicId" to "p"))))))
        assertDenied(fsGet(CAROL, "stickerPacks/p1"))
    }
}
