package com.nook.app.feature.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.nook.app.data.media.VoicePlayer
import com.nook.app.data.media.VoiceRecorder
import com.nook.app.data.model.CallType
import com.nook.app.data.model.Disappearing
import com.nook.app.data.model.Message
import com.nook.app.data.model.MessageType
import com.nook.app.data.security.AppLockManager
import com.nook.app.designsystem.components.Avatar
import com.nook.app.designsystem.components.EmptyState
import com.nook.app.designsystem.components.Hairline
import com.nook.app.designsystem.components.NookButton
import com.nook.app.designsystem.components.NookButtonStyle
import com.nook.app.designsystem.components.NookConfirmDialog
import com.nook.app.designsystem.components.NookIconButton
import com.nook.app.designsystem.components.NookPill
import com.nook.app.designsystem.components.OfflineBanner
import com.nook.app.designsystem.components.SkeletonList
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.designsystem.theme.Spacing
import com.nook.app.feature.calls.CallManager
import com.nook.app.navigation.ChatInfoRoute
import com.nook.app.notifications.Notifier
import com.nook.app.session.ActiveChat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File

@Composable
fun ChatScreen(
    chatId: String,
    nav: NavHostController,
    vm: ChatViewModel = koinViewModel { parametersOf(chatId) },
) {
    val c = NookTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.state.collectAsStateWithLifecycle()
    val replyTo by vm.replyTo.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val player: VoicePlayer = koinInject()
    val playback by player.state.collectAsStateWithLifecycle()
    val lock: AppLockManager = koinInject()
    val callManager: CallManager = koinInject()
    val recorder = remember { VoiceRecorder(context.applicationContext) }

    var text by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    val menuProgress = remember { Animatable(0f) }
    var showAttach by remember { mutableStateOf(false) }
    var showExpressions by remember { mutableStateOf(false) }
    var reactionPickerFor by remember { mutableStateOf<Message?>(null) }
    var forwarding by remember { mutableStateOf<Message?>(null) }
    var infoFor by remember { mutableStateOf<Message?>(null) }
    var confirmUnsend by remember { mutableStateOf<Message?>(null) }
    var viewerUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    // Don't notify for the chat you're looking at; clear its notification on open.
    LifecycleResumeEffect(chatId) {
        ActiveChat.chatId = chatId
        Notifier.clearChat(context, chatId)
        onPauseOrDispose { if (ActiveChat.chatId == chatId) ActiveChat.chatId = null }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel(); player.stop() } }
    LaunchedEffect(toast) { if (toast != null) { delay(1800); toast = null } }

    // ---- launchers ----
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        lock.clearSuppression()
        if (uris.isNotEmpty()) vm.sendImages(uris)
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        lock.clearSuppression()
        val u = cameraUri
        if (ok && u != null) vm.sendImages(listOf(Uri.parse(u)))
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        lock.clearSuppression()
        if (uri != null) vm.sendFile(uri)
    }
    fun launchCamera() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "nook_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        cameraUri = uri.toString()
        lock.suppressNextLock()
        takePicture.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera() else toast = "Camera permission is needed to take photos"
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        toast = if (granted) "Hold the mic to record" else "Microphone permission is needed for voice notes"
    }

    // ---- list behaviour ----
    val listState = rememberLazyListState()
    val atBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 && listState.firstVisibleItemScrollOffset < 120 } }
    var showNewPill by remember { mutableStateOf(false) }
    val newest = (state.items.firstOrNull { it is ChatItem.Msg } as? ChatItem.Msg)?.ui
    LaunchedEffect(newest?.message?.id) {
        val n = newest ?: return@LaunchedEffect
        if (n.mine || atBottom) {
            listState.animateScrollToItem(0)
            showNewPill = false
        } else if (n.isNew) {
            showNewPill = true
        }
    }
    LaunchedEffect(atBottom, newest?.message?.id) {
        if (atBottom) { showNewPill = false; vm.markReadIfNeeded() }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 6
        }.collect { nearTop -> if (nearTop) vm.loadOlder() }
    }

    fun openMenu(target: MenuTarget) {
        menu = target
        scope.launch { menuProgress.snapTo(0f); menuProgress.animateTo(1f, NookMotion.gentle()) }
    }
    fun closeMenu(then: () -> Unit = {}) {
        scope.launch {
            menuProgress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f))
            menu = null
            then()
        }
    }

    val actions = remember(vm) {
        BubbleActions(
            onLongPress = { ui, bounds -> openMenu(MenuTarget(ui, bounds)) },
            onReply = { vm.setReply(it) },
            onReaction = { m, e -> vm.react(m, e) },
            onRetry = { vm.retry(it.id) },
            onOpenMedia = { m ->
                when (m.type) {
                    MessageType.FILE -> m.media?.url?.takeIf { it.isNotBlank() }?.let { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    }
                    else -> viewerUrl = m.localUri ?: m.media?.url
                }
            },
            onTogglePlay = { m -> m.media?.url?.let { player.toggle(m.id, it) } },
            onAnimated = { vm.markAnimated(it) },
        )
    }

    val blurRadius = (24f * menuProgress.value).dp
    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .then(if (menuProgress.value > 0f && Build.VERSION.SDK_INT >= 31) Modifier.blur(blurRadius) else Modifier),
        ) {
            ChatHeader(
                header = state.header,
                disappearing = state.chat?.disappearing ?: Disappearing.OFF,
                onBack = { nav.popBackStack() },
                onInfo = { nav.navigate(ChatInfoRoute(chatId)) },
                onCall = { video ->
                    val other = state.header.otherUid ?: return@ChatHeader
                    callManager.startCall(chatId, other, if (video) CallType.VIDEO else CallType.VOICE)
                },
            )
            OfflineBanner(!online)
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.loading && state.items.isEmpty() -> SkeletonList(rows = 5)
                    state.items.isEmpty() -> EmptyState(
                        Icons.Outlined.WavingHand,
                        "Say hi",
                        if (state.header.isGroup) "Kick things off in ${state.header.title}." else "This is the start of your chat with ${state.header.title}.",
                        modifier = Modifier.align(Alignment.Center),
                    )
                    else -> LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(top = 12.dp, bottom = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "typing", contentType = "typing") {
                            AnimatedVisibility(
                                state.typing.isNotEmpty(),
                                enter = fadeIn() + slideInVertically { it / 2 },
                                exit = fadeOut() + slideOutVertically { it / 2 },
                            ) { TypingBubble(state.typing) }
                        }
                        items(state.items, key = { it.key }, contentType = { if (it is ChatItem.Msg) it.ui.message.type.wire else "sep" }) { item ->
                            when (item) {
                                is ChatItem.Msg -> MessageRow(
                                    ui = item.ui,
                                    isGroup = state.header.isGroup,
                                    lifted = menu?.ui?.message?.id == item.ui.message.id,
                                    playback = playback,
                                    actions = actions,
                                    modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null),
                                )
                                is ChatItem.DateSeparator -> DateSeparator(item.label, Modifier.animateItem())
                            }
                        }
                        if (state.loadingMore) {
                            item(key = "loadingMore") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(22.dp), color = c.textMuted, strokeWidth = 2.dp)
                                }
                            }
                        } else if (!state.canLoadMore) {
                            item(key = "start") { ChatStart(state.header) }
                        }
                    }
                }
                AnimatedVisibility(
                    showNewPill,
                    enter = scaleIn(spring(dampingRatio = 0.6f)) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                ) {
                    NewMessagesPill({ scope.launch { listState.animateScrollToItem(0) }; showNewPill = false })
                }
                AnimatedVisibility(
                    toast != null,
                    enter = fadeIn() + slideInVertically { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                ) {
                    NookPill(toast.orEmpty(), background = c.surfaceRaised)
                }
            }
            Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))) {
                when {
                    state.notMember -> InfoBar("You're no longer in this group.")
                    state.blockedOther -> Column(Modifier.fillMaxWidth().padding(Spacing.gutter)) {
                        Text("You blocked ${state.header.title}.", style = NookTheme.type.body, color = c.textMuted)
                        Spacer(Modifier.height(Spacing.xs))
                        NookButton("Unblock", vm::unblock, style = NookButtonStyle.Secondary)
                    }
                    else -> Composer(
                        text = text,
                        onTextChange = { text = it; vm.onTextChanged(it) },
                        replyTo = replyTo,
                        replyName = replyTo?.let { r -> if (r.senderId == vm.me) "yourself" else (state.items.firstNotNullOfOrNull { (it as? ChatItem.Msg)?.ui?.takeIf { u -> u.message.senderId == r.senderId }?.senderName } ?: state.header.title) },
                        onCancelReply = { vm.setReply(null) },
                        onSend = { vm.sendText(text); text = "" },
                        attachOpen = showAttach,
                        onAttach = { showAttach = true },
                        onExpressions = { showExpressions = true },
                        recorder = recorder,
                        hasMicPermission = { ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED },
                        requestMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        onVoice = { rec -> vm.sendVoice(rec.file, rec.durationMs, rec.waveform) },
                        onHint = { toast = it },
                    )
                }
            }
        }

        menu?.let { target ->
            MessageMenuOverlay(
                target = target,
                progress = menuProgress,
                playback = playback,
                onDismiss = { closeMenu() },
                onReact = { emoji -> vm.react(target.ui.message, emoji) },
                onMoreReactions = { closeMenu { reactionPickerFor = target.ui.message } },
                onAction = { action ->
                    val m = target.ui.message
                    closeMenu {
                        when (action) {
                            MenuAction.Reply -> vm.setReply(m)
                            MenuAction.Copy -> {
                                context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Message", m.text))
                                toast = "Copied"
                            }
                            MenuAction.Forward -> forwarding = m
                            MenuAction.Info -> infoFor = m
                            MenuAction.Unsend -> confirmUnsend = m
                            MenuAction.DeleteForMe -> vm.deleteForMe(m)
                            MenuAction.Retry -> vm.retry(m.id)
                        }
                    }
                },
            )
        }

        viewerUrl?.let { url -> ImageViewer(url, onClose = { viewerUrl = null }) }
    }

    if (showAttach) {
        AttachSheet(
            onDismiss = { showAttach = false },
            onCamera = {
                showAttach = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
                else cameraPermission.launch(Manifest.permission.CAMERA)
            },
            onGallery = {
                showAttach = false
                lock.suppressNextLock()
                pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onFile = {
                showAttach = false
                lock.suppressNextLock()
                pickFile.launch(arrayOf("*/*"))
            },
        )
    }
    if (showExpressions) {
        ExpressionSheet(
            onDismiss = { showExpressions = false },
            onEmoji = { e -> text += e; vm.onTextChanged(text) },
            onGif = { g, sticker -> showExpressions = false; vm.sendGif(g, sticker) },
            onPackSticker = { s -> showExpressions = false; vm.sendSticker(s) },
        )
    }
    reactionPickerFor?.let { m ->
        ExpressionSheet(
            onDismiss = { reactionPickerFor = null },
            onEmoji = { e -> vm.react(m, e); reactionPickerFor = null },
            reactionOnly = true,
        )
    }
    forwarding?.let { m ->
        ForwardSheet(onDismiss = { forwarding = null }, onSend = { ids -> vm.forward(m, ids); forwarding = null; toast = "Forwarded" })
    }
    infoFor?.let { m -> MessageInfoSheet(m, vm.seenBy(m), onDismiss = { infoFor = null }) }
    confirmUnsend?.let { m ->
        NookConfirmDialog(
            title = "Unsend message?",
            message = "It'll be removed for everyone in this chat.",
            confirmLabel = "Unsend",
            destructive = true,
            onConfirm = { vm.unsend(m); confirmUnsend = null },
            onDismiss = { confirmUnsend = null },
        )
    }
}

@Composable
private fun ChatHeader(
    header: ChatHeaderUi,
    disappearing: Disappearing,
    onBack: () -> Unit,
    onInfo: () -> Unit,
    onCall: (video: Boolean) -> Unit,
) {
    val c = NookTheme.colors
    Column(Modifier.fillMaxWidth().background(c.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            NookIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Row(
                Modifier.weight(1f).clip(CircleShape).clickable(onClickLabel = "Chat info", onClick = onInfo).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(header.photoUrl, header.title.ifBlank { "?" }, 40.dp, online = header.online)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(header.title, style = NookTheme.type.titleSans, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    AnimatedContent(header.subtitle, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sub") { sub ->
                        Text(
                            sub,
                            style = NookTheme.type.caption,
                            color = if (header.subtitleActive) c.accent else c.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { contentDescription = sub },
                        )
                    }
                }
            }
            if (disappearing != Disappearing.OFF) {
                NookPill(disappearing.wire, icon = Icons.Outlined.Timer, background = c.surface, onClick = onInfo)
                Spacer(Modifier.width(4.dp))
            }
            if (!header.isGroup) {
                NookIconButton(Icons.Outlined.Call, "Voice call", { onCall(false) })
                NookIconButton(Icons.Outlined.Videocam, "Video call", { onCall(true) })
            }
        }
        Hairline()
    }
}

@Composable
private fun ChatStart(header: ChatHeaderUi) {
    val c = NookTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(header.photoUrl, header.title.ifBlank { "?" }, 72.dp)
        Spacer(Modifier.height(Spacing.sm))
        Text(header.title, style = NookTheme.type.title, color = c.text)
        Text("This is where it all began.", style = NookTheme.type.bodySmall, color = c.textMuted)
    }
}

@Composable
private fun InfoBar(text: String) {
    val c = NookTheme.colors
    Row(Modifier.fillMaxWidth().padding(Spacing.gutter), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Icon(Icons.Outlined.Block, null, tint = c.textMuted)
        Spacer(Modifier.width(Spacing.sm))
        Text(text, style = NookTheme.type.body, color = c.textMuted)
    }
}
