package com.nook.app.feature.calls

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallMade
import androidx.compose.material.icons.automirrored.rounded.CallMissed
import androidx.compose.material.icons.automirrored.rounded.CallReceived
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.nook.app.data.model.Call
import com.nook.app.data.model.CallStatus
import com.nook.app.data.model.CallType
import com.nook.app.data.model.User
import com.nook.app.data.repo.AuthRepository
import com.nook.app.data.repo.CallRepository
import com.nook.app.data.repo.UserRepository
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.ErrorState
import com.nook.app.designsystem.components.NookHeader
import com.nook.app.designsystem.components.NookIconButton
import com.nook.app.designsystem.components.SkeletonList
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.util.TimeFormat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.koin.androidx.compose.koinViewModel

data class CallRow(val call: Call, val peer: User?, val outgoing: Boolean, val missed: Boolean)

sealed interface CallsUi {
    data object Loading : CallsUi
    data class Ready(val rows: List<CallRow>) : CallsUi
    data class Error(val message: String) : CallsUi
}

@OptIn(ExperimentalCoroutinesApi::class)
class CallsViewModel(calls: CallRepository, users: UserRepository, auth: AuthRepository, private val manager: CallManager) : ViewModel() {
    private val me = auth.uid.orEmpty()
    val state: StateFlow<CallsUi> = calls.history().flatMapLatest { list ->
        if (list.isEmpty()) flowOf(CallsUi.Ready(emptyList()))
        else {
            val peers = list.map { it.peer(me) }.distinct()
            combine(peers.map { users.observeUser(it) }) { arr ->
                val byId = arr.filterNotNull().associateBy { it.uid }
                CallsUi.Ready(list.map { CallRow(it, byId[it.peer(me)], it.isOutgoing(me), it.isMissedFor(me)) })
            }
        }
    }.map<CallsUi, CallsUi> { it }
        .catch { emit(CallsUi.Error(it.message ?: "Couldn't load calls")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CallsUi.Loading)

    fun callBack(row: CallRow, video: Boolean) = manager.startCall(row.call.chatId, row.call.peer(me), if (video) CallType.VIDEO else CallType.VOICE)
}

@Composable
fun CallsTab(nav: NavHostController, vm: CallsViewModel = koinViewModel()) {
    val c = NookTheme.colors
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        NookHeader("Calls")
        when (val s = state) {
            CallsUi.Loading -> SkeletonList()
            is CallsUi.Error -> ErrorState(s.message, null)
            is CallsUi.Ready -> if (s.rows.isEmpty()) {
                EmptyState(Icons.Outlined.Call, "No calls yet", "Tap the phone or camera in any chat to ring a friend.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = Spacing.xxl)) {
                    items(s.rows, key = { it.call.id }) { row ->
                        val name = row.peer?.name ?: "Nook user"
                        Row(
                            Modifier.fillMaxWidth().clickable { vm.callBack(row, row.call.type == CallType.VIDEO) }
                                .padding(horizontal = Spacing.gutter, vertical = 12.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = "$name, ${if (row.missed) "missed" else if (row.outgoing) "outgoing" else "incoming"} ${row.call.type.wire} call, ${TimeFormat.listTime(row.call.createdAt)}"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Avatar(row.peer?.photoUrl, name, 52.dp)
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(name, style = NookTheme.type.titleSans, color = if (row.missed) c.danger else c.text)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        when { row.missed -> Icons.AutoMirrored.Rounded.CallMissed; row.outgoing -> Icons.AutoMirrored.Rounded.CallMade; else -> Icons.AutoMirrored.Rounded.CallReceived },
                                        null, tint = if (row.missed) c.danger else c.textMuted, modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        buildString {
                                            append(TimeFormat.listTime(row.call.createdAt))
                                            if (row.call.status == CallStatus.ENDED && row.call.durationSec > 0) append(" · ${TimeFormat.duration(row.call.durationSec)}")
                                        },
                                        style = NookTheme.type.bodySmall, color = c.textMuted,
                                    )
                                }
                            }
                            NookIconButton(
                                if (row.call.type == CallType.VIDEO) Icons.Outlined.Videocam else Icons.Outlined.Call,
                                "Call $name back",
                                { vm.callBack(row, row.call.type == CallType.VIDEO) },
                            )
                        }
                    }
                }
            }
        }
    }
}
