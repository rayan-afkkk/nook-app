package com.nook.app.feature.stickers

import com.nook.app.AppConfig
import com.nook.app.data.model.GiphyItem
import com.nook.app.data.model.GiphyKind
import com.nook.app.data.remote.GiphyApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GiphyState(
    val kind: GiphyKind = GiphyKind.GIFS,
    val query: String = "",
    val items: List<GiphyItem> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val configured: Boolean = AppConfig.hasGiphy,
    val endReached: Boolean = false,
)

/** Debounced Giphy search + paging, owned by a ViewModel's scope. */
class GiphyBrowser(private val api: GiphyApi, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(GiphyState())
    val state: StateFlow<GiphyState> = _state.asStateFlow()
    private var job: Job? = null

    fun start(kind: GiphyKind = _state.value.kind) {
        if (_state.value.items.isEmpty() || kind != _state.value.kind) setKind(kind)
    }

    fun setKind(kind: GiphyKind) {
        _state.update { it.copy(kind = kind, items = emptyList(), endReached = false) }
        load(debounce = false)
    }

    fun setQuery(q: String) {
        _state.update { it.copy(query = q, endReached = false) }
        load(debounce = true)
    }

    private fun load(debounce: Boolean) {
        if (!AppConfig.hasGiphy) return
        job?.cancel()
        job = scope.launch {
            if (debounce) delay(350)
            _state.update { it.copy(loading = true, error = null) }
            val s = _state.value
            val result = runCatching { api.fetch(s.kind, s.query.ifBlank { null }) }
            _state.update {
                result.fold(
                    onSuccess = { items -> it.copy(items = items, loading = false, endReached = items.isEmpty()) },
                    onFailure = { e -> it.copy(loading = false, error = e.message ?: "Couldn't load") },
                )
            }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached || !AppConfig.hasGiphy) return
        _state.update { it.copy(loadingMore = true) }
        scope.launch {
            val more = runCatching { api.fetch(s.kind, s.query.ifBlank { null }, offset = s.items.size) }.getOrDefault(emptyList())
            _state.update { it.copy(items = (it.items + more).distinctBy { g -> g.id }, loadingMore = false, endReached = more.isEmpty()) }
        }
    }
}
