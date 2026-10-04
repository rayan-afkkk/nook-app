package nook.rules

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import nook.rules.Emulator.OWNER
import nook.rules.Emulator.clearDatabase
import nook.rules.Emulator.dbGet
import nook.rules.Emulator.dbRemove
import nook.rules.Emulator.dbSet
import nook.rules.Emulator.dbUpdate
import nook.rules.Emulator.loadDatabaseRules
import nook.rules.Emulator.serverTimestamp
import org.junit.Before
import org.junit.Test

class DatabaseRulesTest {

    @Before fun reset() {
        loadDatabaseRules()
        clearDatabase()
    }

    private fun presence(online: Boolean) = buildJsonObject { put("online", online); put("lastSeen", serverTimestamp) }
    private fun members(vararg uids: String) = JsonObject(uids.associateWith { JsonPrimitive(true) })

    @Test fun `presence write your own, read anyone signed in`() {
        assertAllowed(dbSet(ALICE, "presence/$ALICE", presence(true)))
        assertDenied(dbSet(BOB, "presence/$ALICE", presence(false)))
        assertAllowed(dbGet(BOB, "presence/$ALICE"))
        assertDenied(dbGet(null, "presence/$ALICE"))
    }

    @Test fun `membership mirror DM ids must include you, members can add, anyone can leave`() {
        val id = dmId(ALICE, BOB)
        assertDenied(dbUpdate(CAROL, "chatMembers/$id", members(CAROL, ALICE)))
        assertAllowed(dbUpdate(ALICE, "chatMembers/$id", members(ALICE, BOB)))
        assertAllowed(dbUpdate(ALICE, "chatMembers/g1", members(ALICE, BOB)))
        assertAllowed(dbUpdate(BOB, "chatMembers/g1", members(CAROL)))
        assertDenied(dbRemove(BOB, "chatMembers/g1/$ALICE"))
        assertAllowed(dbRemove(BOB, "chatMembers/g1/$BOB"))
    }

    @Test fun `typing only chat members can read or write`() {
        val id = dmId(ALICE, BOB)
        assertAllowed(dbSet(OWNER, "chatMembers/$id", members(ALICE, BOB)))
        assertAllowed(dbSet(ALICE, "typing/$id/$ALICE", serverTimestamp))
        assertDenied(dbSet(ALICE, "typing/$id/$BOB", serverTimestamp))
        assertDenied(dbSet(CAROL, "typing/$id/$CAROL", serverTimestamp))
        assertAllowed(dbGet(BOB, "typing/$id"))
        assertDenied(dbGet(CAROL, "typing/$id"))
    }
}
