<template>
  <div ref="rootRef" class="search-bar" @focusout="onFocusOut">
    <div class="search-input-wrap">
      <Search :size="18" class="search-icon" />
      <input
        ref="inputRef"
        v-model="keywords"
        placeholder="搜索歌曲、歌手或专辑"
        role="combobox"
        aria-autocomplete="list"
        :aria-expanded="results.length > 0"
        aria-controls="search-results"
        :aria-activedescendant="activeResultId"
        @keydown="onSearchKeydown"
        @compositionstart="isComposing = true"
        @compositionend="isComposing = false"
        @input="onInput"
        @focus="onFocus"
      />
      <button
        v-if="keywords"
        class="clear-btn"
        type="button"
        @click="clear"
        aria-label="清空搜索"
        title="清空搜索"
      >
        <X :size="16" />
      </button>
    </div>

    <div v-if="results.length" id="search-results" class="search-results" role="listbox">
      <div class="results-head">
        <span>Search Results</span>
        <span>{{ results.length }}</span>
      </div>
      <button
        v-for="(song, index) in results"
        :id="resultOptionId(index)"
        :key="song.id"
        class="result-item"
        :class="{ active: activeIndex === index }"
        type="button"
        role="option"
        :aria-selected="activeIndex === index"
        @mouseenter="activeIndex = index"
        @focus="activeIndex = index"
        @click="playSong(song)"
      >
        <img v-if="song.coverUrl" :src="proxyCoverUrl(song.coverUrl)" class="result-cover" referrerpolicy="no-referrer" />
        <div v-else class="result-cover fallback">
          <Music :size="18" />
        </div>
        <div class="result-meta">
          <div class="result-name">{{ song.name }}</div>
          <div class="result-artist">{{ (song.artists || []).join(' / ') || '未知歌手' }}</div>
        </div>
        <PlayCircle :size="18" class="result-play" />
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Music, PlayCircle, Search, X } from '@lucide/vue'
import { searchSongs, proxyCoverUrl } from '../api'

const emit = defineEmits(['play'])
const rootRef = ref(null)
const inputRef = ref(null)
const keywords = ref('')
const results = ref([])
const activeIndex = ref(-1)
const isComposing = ref(false)
let debounceTimer = null
const activeResultId = computed(() => {
  return activeIndex.value >= 0 && results.value[activeIndex.value]
    ? resultOptionId(activeIndex.value)
    : undefined
})

function onSearchKeydown(e) {
  if (e.key === 'ArrowDown') {
    e.preventDefault()
    moveActiveResult(1)
    return
  }

  if (e.key === 'ArrowUp') {
    e.preventDefault()
    moveActiveResult(-1)
    return
  }

  if (e.key === 'Escape') {
    results.value = []
    activeIndex.value = -1
    return
  }

  if (e.key === 'Enter' && !isComposing.value) {
    e.preventDefault()
    if (activeIndex.value >= 0 && results.value[activeIndex.value]) {
      playSong(results.value[activeIndex.value])
    } else {
      clearTimeout(debounceTimer)
      doSearch()
    }
  }
}

function onFocus() {
  if (keywords.value.trim()) doSearch()
}

function onFocusOut(e) {
  if (e.relatedTarget && rootRef.value?.contains(e.relatedTarget)) return
  window.setTimeout(() => {
    if (rootRef.value?.contains(document.activeElement)) return
    results.value = []
    activeIndex.value = -1
  }, 0)
}

function onInput() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(doSearch, 300)
}

async function doSearch() {
  if (!keywords.value.trim()) {
    results.value = []
    return
  }
  try {
    const res = await searchSongs(keywords.value.trim(), 8)
    results.value = res.data || []
    activeIndex.value = results.value.length ? 0 : -1
  } catch {
    results.value = []
    activeIndex.value = -1
  }
}

function playSong(song) {
  emit('play', song)
  keywords.value = ''
  results.value = []
  activeIndex.value = -1
}

function clear() {
  keywords.value = ''
  results.value = []
  activeIndex.value = -1
  inputRef.value?.focus()
}

function moveActiveResult(direction) {
  if (!results.value.length) {
    if (keywords.value.trim()) doSearch()
    return
  }
  const nextIndex = activeIndex.value < 0
    ? 0
    : (activeIndex.value + direction + results.value.length) % results.value.length
  activeIndex.value = nextIndex
}

function resultOptionId(index) {
  return `search-result-${index}`
}
</script>

<style scoped>
.search-bar {
  position: relative;
  width: min(100%, 520px);
  flex: 1;
}

.search-input-wrap {
  min-height: var(--control-height);
  display: flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  padding: 0 12px;
  transition:
    border-color var(--transition-fast),
    background var(--transition-fast),
    box-shadow var(--transition-fast);
}

.search-input-wrap:focus-within {
  border-color: color-mix(in srgb, var(--accent) 48%, var(--input-border));
  background: color-mix(in srgb, var(--bg-elevated) 82%, transparent);
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--accent) 10%, transparent);
}

.search-icon {
  color: var(--text-tertiary);
}

.search-input-wrap input {
  flex: 1;
  min-width: 0;
  height: 42px;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-size: 0.92rem;
}

.search-input-wrap input::placeholder {
  color: var(--text-tertiary);
}

.clear-btn {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--text-primary) 8%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
}

.clear-btn:hover {
  color: var(--text-primary);
  background: color-mix(in srgb, var(--text-primary) 12%, transparent);
}

.search-results {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  right: 0;
  z-index: 220;
  padding: 8px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-elevated) 98%, transparent);
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(18px);
  max-height: 420px;
  overflow-y: auto;
}

.results-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px 10px;
  color: var(--text-tertiary);
  font-size: 0.72rem;
  font-weight: 800;
  text-transform: uppercase;
}

.result-item {
  width: 100%;
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    transform var(--transition-fast);
}

.result-item:hover,
.result-item.active,
.result-item:focus-visible {
  transform: translateX(2px);
  border-color: var(--divider);
  background: var(--bg-card-hover);
}

.result-item:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
}

.result-cover {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}

.result-cover.fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-card));
}

.result-meta {
  min-width: 0;
  flex: 1;
}

.result-name,
.result-artist {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-name {
  color: var(--text-primary);
  font-size: 0.92rem;
  font-weight: 800;
}

.result-artist {
  margin-top: 3px;
  color: var(--text-secondary);
  font-size: 0.76rem;
}

.result-play {
  color: var(--accent);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.result-item:hover .result-play,
.result-item.active .result-play,
.result-item:focus-visible .result-play {
  opacity: 1;
}

@media (max-width: 960px) {
  .search-bar {
    width: 100%;
    max-width: none;
  }
}
</style>
