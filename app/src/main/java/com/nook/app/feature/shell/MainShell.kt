package com.nook.app.feature.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.nook.app.designsystem.theme.NookMotion
import com.nook.app.designsystem.theme.NookTheme
import com.nook.app.feature.account.AccountTab
import com.nook.app.feature.calls.CallsTab
import com.nook.app.feature.chats.ChatsTab
import com.nook.app.feature.friends.FriendsTab
import com.nook.app.feature.stickers.StickersTab
import com.nook.app.util.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * Five-tab shell. Swiping is interactive (pager); tapping a tab does a direction-aware
 * fade-through with a short horizontal slide. Tab state survives switches because pages keep
 * their saveable state and ViewModels are scoped to this back-stack entry.
 */
@Composable
fun MainShell(nav: NavHostController) {
    val c = NookTheme.colors
    val reduce = NookTheme.reduceMotion
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState { NookTab.entries.size }
    val density = LocalDensity.current
    val contentAlpha = remember { Animatable(1f) }
    val contentSlide = remember { Animatable(0f) }
    val indicator = remember { Animatable(pager.currentPage.toFloat()) }
    var tapping by remember { mutableStateOf(false) }
    val selected by remember { derivedStateOf { if (tapping) indicator.targetValue.toInt() else pager.currentPage } }

    LaunchedEffect(pager) {
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { pos ->
            if (!tapping) indicator.snapTo(pos)
        }
    }
    LaunchedEffect(pager) {
        var first = true
        snapshotFlow { pager.settledPage }.collect { if (!first && !tapping) haptics.tick(); first = false }
    }

    fun select(target: Int) {
        val current = pager.currentPage
        if (target == current) return
        haptics.tick()
        scope.launch {
            tapping = true
            launch { indicator.animateTo(target.toFloat(), NookMotion.choose(reduce, NookMotion.bouncy())) }
            val dir = sign((target - current).toFloat())
            contentAlpha.animateTo(0f, tween(if (reduce) 80 else 90))
            pager.scrollToPage(target)
            contentSlide.snapTo(if (reduce) 0f else dir * with(density) { 36.dp.toPx() })
            launch { contentSlide.animateTo(0f, NookMotion.standard()) }
            contentAlpha.animateTo(1f, tween(if (reduce) 120 else 200))
            tapping = false
        }
    }

    BackHandler(enabled = pager.currentPage != 0) { select(0) }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .graphicsLayer { alpha = contentAlpha.value; translationX = contentSlide.value },
        ) {
            HorizontalPager(
                state = pager,
                key = { it },
                beyondViewportPageCount = 0,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val o = (pager.currentPage - page) + pager.currentPageOffsetFraction
                        if (!reduce) {
                            // Cancel most of the pager's travel → a short slide, plus fade-through.
                            translationX = o * size.width * 0.8f
                            alpha = (((1f - abs(o)) - 0.35f) / 0.65f).coerceIn(0f, 1f)
                        } else {
                            alpha = 1f - abs(o).coerceIn(0f, 1f)
                        }
                    },
                ) {
                    when (NookTab.entries[page]) {
                        NookTab.Chats -> ChatsTab(nav)
                        NookTab.Friends -> FriendsTab(nav)
                        NookTab.Stickers -> StickersTab(nav)
                        NookTab.Calls -> CallsTab(nav)
                        NookTab.Account -> AccountTab(nav)
                    }
                }
            }
        }
        NookBottomBar(
            indicatorPosition = indicator.value,
            selectedIndex = selected,
            onSelect = ::select,
        )
    }
}
