<template>
  <div class="search-bar">
    <div class="search-input-wrap">
      <svg class="search-icon" viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="11" cy="11" r="8" />
        <path d="m21 21-4.35-4.35" />
      </svg>
      <input
        v-model="keywords"
        placeholder="搜索歌曲，继续往下掉..."
        @keydown.enter.prevent="onEnter"
        @compositionstart="isComposing = true"
        @compositionend="isComposing = false"
        @input="onInput"
        @focus="onFocus"
        @blur="onBlur"
      />
      <button v-if="keywords" class="clear-btn" @click="clear">×</button>
    </div>

    <div v-if="results.length" class="search-results" @mousedown.prevent>
      <div
        v-for="song in results"
        :key="song.id"
        class="result-item"
        @click="playSong(song)"
      >
        <img v-if="song.coverUrl" :src="proxyCoverUrl(song.coverUrl)" class="result-cover" referrerpolicy="no-referrer" />
        <div class="result-meta">
          <div class="result-name">{{ song.name }}</div>
          <div class="result-artist">{{ (song.artists || []).join(' / ') }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { searchSongs, proxyCoverUrl } from '../api'

const emit = defineEmits(['play'])
const keywords = ref('')
const results = ref([])
const isComposing = ref(false)
let debounceTimer = null
let blurTimer = null

function onEnter() {
  if (!isComposing.value) {
    clearTimeout(debounceTimer)
    doSearch()
  }
}

function onFocus() {
  clearTimeout(blurTimer)
  if (keywords.value.trim()) doSearch()
}

function onBlur() {
  blurTimer = setTimeout(() => {
    results.value = []
  }, 200)
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
  } catch {
    results.value = []
  }
}

function playSong(song) {
  clearTimeout(blurTimer)
  emit('play', song)
  keywords.value = ''
  results.value = []
}

function clear() {
  keywords.value = ''
  results.value = []
}
</script>

<style scoped>
.search-bar {
  position: relative;
  max-width: 430px;
  flex: 1;
}

.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: 999px;
  padding: 0 16px;
  transition:
    border-color var(--transition-theme),
    background var(--transition-theme),
    box-shadow var(--transition-theme);
}

.search-input-wrap:focus-within {
  border-color: color-mix(in srgb, var(--accent) 50%, transparent);
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--accent) 10%, transparent);
}

.search-icon {
  color: var(--text-secondary);
  flex-shrink: 0;
}

.search-input-wrap input {
  flex: 1;
  min-width: 0;
  background: transparent;
  border: none;
  color: var(--text-primary);
  font-size: 0.92rem;
  outline: none;
}

.search-input-wrap input::placeholder {
  color: var(--text-tertiary);
}

.clear-btn {
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
  font-size: 1rem;
  cursor: pointer;
  line-height: 1;
}

.search-results {
  position: absolute;
  top: calc(100% + 10px);
  left: 0;
  right: 0;
  padding: 8px;
  background: color-mix(in srgb, var(--bg-elevated) 98%, transparent);
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  max-height: 360px;
  overflow-y: auto;
  z-index: 200;
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(16px);
}

.result-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  cursor: pointer;
  border-radius: 14px;
  transition: background var(--transition-theme), transform var(--transition-theme);
}

.result-item:hover {
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  transform: translateX(2px);
}

.result-cover {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
}

.result-name {
  color: var(--text-primary);
  font-size: 0.92rem;
}

.result-artist {
  color: var(--text-secondary);
  font-size: 0.76rem;
  margin-top: 2px;
}
</style>
