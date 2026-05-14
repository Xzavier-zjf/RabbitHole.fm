<template>
  <div class="radio-view" @keydown="onKeydown" tabindex="0" ref="rootEl">
    <aside class="left-rail">
      <ChannelList
        :width="sidebarWidth"
        :current-channel-id="player.currentChannelId"
        @select="onChannelSelect"
        @open-history="openHistory"
        @open-favorites="openFavorites"
      />
    </aside>

    <div class="resize-handle" @mousedown="onResizeStart"></div>

    <main class="main-area">
      <header class="top-bar">
        <div class="brand-block">
          <div class="brand-mark">
            <span class="brand-core"></span>
            <span class="brand-ear brand-ear-left"></span>
            <span class="brand-ear brand-ear-right"></span>
          </div>
          <div>
            <div class="brand-title">
              <span class="brand-rabbit">RabbitHole</span><span class="brand-fm">.fm</span>
            </div>
            <div class="brand-subtitle">{{ themeLabel }}</div>
          </div>
        </div>

        <div class="top-actions">
          <SearchBar @play="playSearchResult" />
          <button class="theme-toggle" @click="toggleTheme" :aria-label="themeToggleLabel" :title="themeToggleLabel">
            <span class="theme-icon">{{ theme === 'dark' ? '☀' : '☾' }}</span>
            <span class="theme-text">{{ theme === 'dark' ? '草地清晨' : '兔洞之夜' }}</span>
          </button>
        </div>
      </header>

      <div class="content-layout">
        <div class="main-column">
          <transition name="top-status">
            <div
              v-if="topStatusVisible"
              class="top-status-bar"
              :class="['tone-' + topStatusTone, { 'is-compact': isDj }]"
              role="status"
              aria-live="polite"
            >
              <span class="top-status-dot"></span>
              <span v-if="topStatusLabel" class="top-status-chip">{{ topStatusLabel }}</span>
              <span class="top-status-text">{{ topStatusMessage }}</span>
            </div>
          </transition>

          <div class="main-shell" :class="{ 'is-dj': isDj }">
            <div class="empty-state" v-if="!player.currentItem && !player.isLoading">
              <div class="empty-orbit"></div>
              <div class="empty-icon">🐇</div>
              <div class="empty-title" v-if="player.error">{{ player.error }}</div>
              <div class="empty-title" v-else>选择一个频道开始掉进新的兔子洞</div>
              <div class="empty-desc">左侧是兔子洞入口，中央会随着音乐变成深夜洞穴或晨光草地。</div>
              <div class="empty-desc">快捷键：空格播放/暂停，左右切歌，上下调音量。</div>
            </div>

            <div class="loading-state" v-if="player.isLoading">
              <div class="spinner-shell">
                <div class="spinner"></div>
              </div>
              <div class="loading-text">正在坠入新的兔子洞...</div>
            </div>

            <template v-if="player.currentItem">
            <!-- Cover mode -->
            <section v-if="!showLyrics" class="hero-stage" @click="toggleLyrics">
              <div class="cover-orbit"></div>
              <div class="cover-glow"></div>

              <div class="now-playing">
                <div class="np-label-row">
                  <div class="np-label">
                    正在播放
                    <span v-if="player.currentItem.requester" class="np-requester">· 来自 {{ player.currentItem.requester }} 的留言</span>
                  </div>
                  <button
                    v-if="player.currentItem.songId"
                    class="fav-btn"
                    :class="{ favorited: isFavorited }"
                    @click.stop="toggleFav"
                    :title="isFavorited ? '取消收藏' : '收藏歌曲'"
                  >
                    {{ isFavorited ? '❤️' : '🤍' }}
                  </button>
                </div>

                <div class="cover-frame" :class="{ 'is-hidden': isDj }" title="点击查看歌词">
                  <img
                    v-if="showMainCover"
                    :src="proxyCoverUrl(player.currentItem.coverUrl)"
                    class="np-cover"
                    @error="onMainCoverError"
                    referrerpolicy="no-referrer"
                  />
                  <div v-else class="cover-fallback">RH</div>
                  <div class="cover-mask"></div>
                  <div class="cover-hint">点击查看歌词</div>
                </div>

                <div class="np-copy">
                  <div class="np-name">{{ player.currentItem.name }}</div>
                  <div class="np-artist">{{ (player.currentItem.artists || []).join(' / ') || '未知歌手' }}</div>
                </div>
              </div>
            </section>

            <!-- Lyrics mode -->
            <template v-else>
              <section class="lyrics-top-bar" @click="toggleLyrics">
                <button class="lyrics-back" @click.stop="toggleLyrics">←</button>
                <img
                  v-if="showMiniCover"
                  :src="proxyCoverUrl(player.currentItem.coverUrl)"
                  class="lyrics-mini-cover"
                  @error="onMiniCoverError"
                  referrerpolicy="no-referrer"
                />
                <div v-else class="lyrics-mini-cover cover-fallback mini-fallback">RH</div>
                <div class="lyrics-mini-info">
                  <div class="lyrics-mini-name">{{ player.currentItem.name }}</div>
                  <div class="lyrics-mini-artist">{{ (player.currentItem.artists || []).join(' / ') || '未知歌手' }}</div>
                </div>
                <button
                  v-if="player.currentItem.songId"
                  class="fav-btn"
                  :class="{ favorited: isFavorited }"
                  @click.stop="toggleFav"
                  :title="isFavorited ? '取消收藏' : '收藏歌曲'"
                >
                  {{ isFavorited ? '❤️' : '🤍' }}
                </button>
              </section>

              <section class="content-stage lyrics-expanded">
                <DjWaveform v-if="isDj" />
                <LyricsPanel v-else />
              </section>
            </template>
          </template>
          </div>
        </div>

        <RequestQueuePanel :channel-id="player.currentChannelId || 32953014" />
      </div>
    </main>

    <PlayerBar />
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { usePlayerStore } from '../stores/player'
import { useUserStore } from '../stores/user'
import { proxyCoverUrl, addFavorite, removeFavorite, getFavorites } from '../api'
import { useTheme } from '../composables/theme'
import ChannelList from '../components/ChannelList.vue'
import LyricsPanel from '../components/LyricsPanel.vue'
import DjWaveform from '../components/DjWaveform.vue'
import SearchBar from '../components/SearchBar.vue'
import PlayerBar from '../components/PlayerBar.vue'
import RequestQueuePanel from '../components/RequestQueuePanel.vue'

const player = usePlayerStore()
const userStore = useUserStore()
const { theme, toggleTheme } = useTheme()
const router = useRouter()
const route = useRoute()

const isDj = computed(() => player.currentItem?.type === 'dj')
const themeLabel = computed(() => {
  return theme.value === 'dark' ? 'Down the Rabbit Hole · 兔洞之夜' : 'Out in the Meadow · 草地清晨'
})
const themeToggleLabel = computed(() => {
  return theme.value === 'dark' ? '切换到草地清晨主题' : '切换到兔洞之夜主题'
})

const sidebarWidth = ref(284)
const MIN_WIDTH = 220
const MAX_WIDTH = 380
const rootEl = ref(null)
const favIds = ref(new Set())
const volume = ref(0.8)
const mainCoverFailed = ref(false)
const miniCoverFailed = ref(false)
const topStatusMessage = ref('')
const topStatusLabel = ref('')
const topStatusTone = ref('resume')
const topStatusVisible = ref(false)
let topStatusTimer = null

const showLyrics = ref(false)

const showMainCover = computed(() => !!player.currentItem?.coverUrl && !mainCoverFailed.value)
const showMiniCover = computed(() => !!player.currentItem?.coverUrl && !miniCoverFailed.value)

function toggleLyrics() {
  showLyrics.value = !showLyrics.value
}

watch(showLyrics, (value) => {
  player.setPlaybackViewState({ showLyrics: value })
})

watch(() => player.currentChannelId, () => {
  showLyrics.value = false
})

watch(() => player.currentItem?.coverUrl, () => {
  mainCoverFailed.value = false
  miniCoverFailed.value = false
})

const isFavorited = computed(() => {
  const songId = player.currentItem?.songId
  return songId ? favIds.value.has(songId) : false
})

async function loadFavs() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await getFavorites()
    favIds.value = new Set((res.data || []).map(f => f.songId))
  } catch { /* ignore */ }
}

async function toggleFav() {
  if (!userStore.isLoggedIn) return
  const item = player.currentItem
  if (!item?.songId) return
  try {
    if (favIds.value.has(item.songId)) {
      await removeFavorite(item.songId)
      favIds.value.delete(item.songId)
    } else {
      await addFavorite(item.songId, {
        songName: item.name || '',
        artists: (item.artists || []).join(' / '),
        coverUrl: item.coverUrl || '',
      })
      favIds.value.add(item.songId)
    }
  } catch { /* ignore */ }
}

function onKeydown(e) {
  if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') return
  switch (e.key) {
    case ' ':
      e.preventDefault()
      player.togglePlay()
      break
    case 'ArrowLeft':
      e.preventDefault()
      player.prev()
      break
    case 'ArrowRight':
      e.preventDefault()
      player.next()
      break
    case 'ArrowUp':
      e.preventDefault()
      volume.value = Math.min(1, volume.value + 0.05)
      player.setVolume(volume.value)
      break
    case 'ArrowDown':
      e.preventDefault()
      volume.value = Math.max(0, volume.value - 0.05)
      player.setVolume(volume.value)
      break
  }
}

function showTopStatus(message, tone = 'resume', duration = 2400, label = '') {
  if (topStatusTimer) {
    clearTimeout(topStatusTimer)
    topStatusTimer = null
  }
  topStatusMessage.value = message
  topStatusLabel.value = label
  topStatusTone.value = tone
  topStatusVisible.value = true
  topStatusTimer = setTimeout(() => {
    topStatusVisible.value = false
    topStatusTimer = null
  }, duration)
}

function buildRestoreStatusPayload(snapshot) {
  if (!player.currentItem) return {
    message: '',
    tone: 'resume',
  }

  const from = Array.isArray(route.query.from) ? route.query.from[0] : route.query.from
  const trackName = player.currentItem.name || '上一首歌'

  if (from === 'history') {
    return {
      message: snapshot?.showLyrics
        ? '已带着刚刚的频道和歌词位置回来，' + trackName + ' 正接着往下唱'
        : '已从播放历史回来，' + trackName + ' 和刚刚那口洞都还替你留着',
      tone: 'return',
      label: '播放历史',
    }
  }

  if (from === 'favorites') {
    return {
      message: snapshot?.showLyrics
        ? '已带着收藏回到 ' + trackName + ' 的歌词页，继续沿着这一句听下去'
        : '已从收藏回来，' + trackName + '、当前频道和点歌队列都还在原位',
      tone: 'return',
      label: '我的收藏',
    }
  }

  const seconds = Math.max(0, Math.round(snapshot?.currentTime || 0))
  const minute = Math.floor(seconds / 60)
  const second = String(seconds % 60).padStart(2, '0')
  return {
    message: seconds > 0
      ? '已替你接回 ' + trackName + '，从 ' + minute + ':' + second + ' 附近继续播放'
      : '已把 ' + trackName + ' 接回耳边，频道也停在你刚刚离开的地方',
    tone: 'resume',
    label: '继续播放',
  }
}

function consumeReturnHint() {
  if (!('from' in route.query)) return
  const nextQuery = { ...route.query }
  delete nextQuery.from
  router.replace({ path: route.path, query: nextQuery })
}

onMounted(async () => {
  const snapshot = player.getSavedPlaybackContext()
  if (!player.currentItem && player.queue.length === 0) {
    await player.buildQueue(player.currentChannelId || snapshot?.channelId || 19723756, {
      resumeContext: snapshot,
    })
  }
  if (snapshot?.showLyrics && player.currentItem) {
    showLyrics.value = true
  }
  if (snapshot?.channelId && player.currentItem) {
    const restoreStatus = buildRestoreStatusPayload(snapshot)
    if (restoreStatus.message) {
      showTopStatus(restoreStatus.message, restoreStatus.tone, 2800, restoreStatus.label || '')
    }
    consumeReturnHint()
  }
  loadFavs()
  rootEl.value?.focus()
})

const checkLoginInterval = setInterval(() => {
  if (userStore.isLoggedIn && favIds.value.size === 0) loadFavs()
}, 2000)

onBeforeUnmount(() => {
  document.body.style.userSelect = ''
  document.body.style.cursor = ''
  clearInterval(checkLoginInterval)
  if (topStatusTimer) {
    clearTimeout(topStatusTimer)
    topStatusTimer = null
  }
})

let dragging = false

function onResizeStart(e) {
  dragging = true
  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'

  const startX = e.clientX
  const startW = sidebarWidth.value

  function onMove(ev) {
    if (!dragging) return
    const delta = ev.clientX - startX
    sidebarWidth.value = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startW + delta))
  }

  function onUp() {
    dragging = false
    document.body.style.userSelect = ''
    document.body.style.cursor = ''
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
}

function onChannelSelect(channelId) {
  player.buildQueue(channelId)
}

function openHistory() {
  player.setPlaybackViewState({ showLyrics: showLyrics.value })
  router.push('/history')
}

function openFavorites() {
  player.setPlaybackViewState({ showLyrics: showLyrics.value })
  router.push('/favorites')
}

function playSearchResult(song) {
  player.addToQueue(song)
  const idx = player.queue.length - 1
  player.playItem(idx)
}

function onMainCoverError() {
  mainCoverFailed.value = true
}

function onMiniCoverError() {
  miniCoverFailed.value = true
}
</script>

<style scoped>
.radio-view {
  display: flex;
  height: 100vh;
  overflow: hidden;
  color: var(--text-primary);
  outline: none;
}

.left-rail {
  width: v-bind(sidebarWidth + 'px');
  flex-shrink: 0;
  position: relative;
  z-index: 2;
}

.resize-handle {
  width: 8px;
  cursor: col-resize;
  flex-shrink: 0;
  background:
    linear-gradient(180deg, transparent, color-mix(in srgb, var(--mystic) 35%, transparent), transparent);
  opacity: 0.45;
  transition: opacity var(--transition-theme);
}

.resize-handle:hover {
  opacity: 1;
}

.main-area {
  flex: 1;
  min-width: 0;
  padding: 18px 22px 108px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.top-bar {
  min-height: 72px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-lg);
  background: var(--bg-glass);
  backdrop-filter: blur(20px);
  box-shadow: var(--shadow-soft);
}

.top-status-bar {
  align-self: center;
  width: fit-content;
  max-width: min(100%, 720px);
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--mystic) 18%, var(--divider));
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 92%, transparent), color-mix(in srgb, var(--bg-card) 90%, transparent));
  box-shadow: 0 10px 24px color-mix(in srgb, var(--bg-primary) 16%, transparent);
  color: var(--text-secondary);
  backdrop-filter: blur(18px);
}

.top-status-bar.is-compact {
  max-width: min(100%, 520px);
  min-height: 30px;
  padding: 6px 12px;
  gap: 8px;
}

.top-status-bar.is-compact .top-status-chip {
  min-height: 20px;
  padding: 0 8px;
  font-size: 0.68rem;
}

.top-status-bar.is-compact .top-status-text {
  font-size: 0.78rem;
}

.top-status-bar.tone-return {
  border-color: color-mix(in srgb, var(--mystic) 34%, var(--divider));
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--mystic) 10%, var(--bg-elevated)), color-mix(in srgb, var(--bg-card) 92%, transparent));
  box-shadow: 0 12px 28px color-mix(in srgb, var(--mystic) 14%, transparent);
}

.top-status-bar.tone-return .top-status-dot {
  background: linear-gradient(135deg, var(--mystic), color-mix(in srgb, var(--accent) 72%, white 6%));
  box-shadow: 0 0 14px color-mix(in srgb, var(--mystic) 42%, transparent);
}

.top-status-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--mystic));
  box-shadow: 0 0 14px color-mix(in srgb, var(--accent) 42%, transparent);
  transition: background 220ms ease, box-shadow 220ms ease, transform 220ms ease;
}

.top-status-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: color-mix(in srgb, var(--mystic) 14%, transparent);
  color: var(--mystic);
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  transform-origin: center;
}

.top-status-bar.tone-return .top-status-chip {
  border-color: color-mix(in srgb, var(--mystic) 24%, transparent);
  background: color-mix(in srgb, var(--mystic) 20%, transparent);
  color: color-mix(in srgb, var(--mystic) 86%, white 10%);
}

.top-status-bar.tone-resume .top-status-chip {
  border-color: color-mix(in srgb, var(--accent) 22%, transparent);
  background: color-mix(in srgb, var(--accent) 16%, transparent);
  color: color-mix(in srgb, var(--accent) 86%, white 8%);
}

.top-status-text {
  font-size: 0.82rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  opacity: 0.96;
}

.top-status-enter-active,
.top-status-leave-active {
  transition: opacity 220ms ease, transform 220ms ease;
}

.top-status-enter-active .top-status-chip,
.top-status-leave-active .top-status-chip,
.top-status-enter-active .top-status-text,
.top-status-leave-active .top-status-text {
  transition: opacity 220ms ease, transform 220ms ease;
}

.top-status-enter-from,
.top-status-leave-to {
  opacity: 0;
  transform: translateY(-6px) scale(0.985);
}

.top-status-enter-from .top-status-chip,
.top-status-leave-to .top-status-chip {
  opacity: 0;
  transform: translateY(-3px) scale(0.94);
}

.top-status-enter-from .top-status-text,
.top-status-leave-to .top-status-text {
  opacity: 0;
  transform: translateY(2px);
}

.top-status-enter-active .top-status-chip {
  transition-delay: 0ms;
}

.top-status-enter-active .top-status-text {
  transition-delay: 40ms;
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark {
  width: 44px;
  height: 44px;
  position: relative;
  display: grid;
  place-items: center;
}

.brand-core {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: radial-gradient(circle at 36% 34%, var(--accent-soft), var(--accent));
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--accent) 10%, transparent);
}

.brand-ear {
  position: absolute;
  top: -2px;
  width: 10px;
  height: 18px;
  background: color-mix(in srgb, var(--mystic) 70%, white 8%);
  border-radius: 999px 999px 14px 14px;
}

.brand-ear-left {
  left: 10px;
  transform: rotate(-22deg);
}

.brand-ear-right {
  right: 10px;
  transform: rotate(22deg);
}

.brand-title {
  font-family: var(--font-display);
  font-size: 1.5rem;
  letter-spacing: 0.03em;
}

.brand-rabbit {
  color: var(--accent);
}

.brand-fm {
  color: var(--mystic);
  font-size: 0.82em;
  margin-left: 2px;
}

.brand-subtitle {
  color: var(--text-secondary);
  font-size: 0.82rem;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.theme-toggle {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  border: 1px solid var(--divider);
  border-radius: 999px;
  background: color-mix(in srgb, var(--bg-elevated) 88%, transparent);
  color: var(--text-primary);
  padding: 10px 16px;
  cursor: pointer;
  transition:
    transform var(--transition-theme),
    background var(--transition-theme),
    border-color var(--transition-theme);
}

.theme-toggle:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--accent) 40%, var(--divider));
}

.theme-icon {
  width: 28px;
  height: 28px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
  font-size: 0.95rem;
}

.theme-text {
  color: var(--text-secondary);
  font-size: 0.86rem;
  white-space: nowrap;
}

.main-column {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.main-shell {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  position: relative;
}

.content-layout {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 18px;
  align-items: stretch;
}

.hero-stage {
  position: relative;
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 388px;
  padding: 8px 0 0;
  cursor: pointer;
}

.cover-hint {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  color: var(--text-secondary);
  font-size: 0.72rem;
  background: color-mix(in srgb, var(--bg-elevated) 70%, transparent);
  padding: 4px 12px;
  border-radius: 999px;
  opacity: 0;
  transition: opacity var(--transition-theme);
  pointer-events: none;
  white-space: nowrap;
}

.cover-frame:hover .cover-hint {
  opacity: 1;
}

.lyrics-top-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  cursor: pointer;
}

.lyrics-back {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 88%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 0.9rem;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  transition: border-color var(--transition-theme);
}

.lyrics-back:hover {
  border-color: color-mix(in srgb, var(--accent) 40%, transparent);
}

.lyrics-mini-cover {
  width: 44px;
  height: 44px;
  border-radius: 14px;
  object-fit: cover;
  flex-shrink: 0;
}

.mini-fallback {
  display: grid;
  place-items: center;
  font-size: 1rem;
}

.lyrics-mini-info {
  min-width: 0;
  flex: 1;
}

.lyrics-mini-name {
  color: var(--text-primary);
  font-size: 0.92rem;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lyrics-mini-artist {
  color: var(--text-secondary);
  font-size: 0.76rem;
  margin-top: 2px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lyrics-expanded {
  flex: 1;
}

.cover-orbit,
.cover-glow {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.cover-orbit {
  width: min(58vw, 680px);
  height: min(58vw, 680px);
  background: radial-gradient(circle, color-mix(in srgb, var(--accent) 10%, transparent) 0%, transparent 68%);
  opacity: 0.75;
}

.cover-glow {
  width: min(48vw, 520px);
  height: min(48vw, 520px);
  background: radial-gradient(circle, color-mix(in srgb, var(--mystic) 12%, transparent) 0%, transparent 66%);
}

.now-playing {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: min(100%, 760px);
  text-align: center;
}

.np-label-row {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-bottom: 16px;
}

.np-label {
  color: var(--mystic);
  font-size: 0.76rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.np-requester {
  color: var(--text-secondary);
  letter-spacing: 0.03em;
  font-size: 0.78rem;
  text-transform: none;
}

.fav-btn {
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 88%, transparent);
  color: var(--highlight);
  border-radius: 999px;
  padding: 8px 12px;
  cursor: pointer;
  transition: transform var(--transition-theme), border-color var(--transition-theme);
}

.fav-btn:hover {
  transform: translateY(-1px) scale(1.03);
  border-color: color-mix(in srgb, var(--highlight) 45%, var(--divider));
}

.cover-frame {
  width: min(60vw, 360px);
  aspect-ratio: 1 / 1;
  border-radius: 32px;
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  box-shadow: var(--shadow-cover);
  transform: translateZ(0);
  transition: opacity var(--transition-slow), transform var(--transition-slow);
}

.cover-frame.is-hidden {
  opacity: 0.1;
  transform: scale(0.95);
}

.np-cover,
.cover-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 32px;
}

.cover-fallback {
  display: grid;
  place-items: center;
  font-family: var(--font-display);
  font-size: 5rem;
  color: color-mix(in srgb, var(--text-primary) 72%, transparent);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 28%, var(--bg-secondary)), color-mix(in srgb, var(--mystic) 22%, var(--bg-primary)));
}

.cover-mask {
  position: absolute;
  inset: 0;
  background: var(--cover-mask);
}

.np-copy {
  margin-top: 22px;
  display: grid;
  gap: 8px;
}

.np-name {
  font-size: clamp(2rem, 3vw, 3.35rem);
  line-height: 1.02;
  font-weight: 700;
  color: var(--text-primary);
  text-wrap: balance;
}

.np-artist {
  color: var(--text-secondary);
  font-size: 1.08rem;
}

.content-stage {
  min-height: 0;
  border-radius: var(--radius-xl);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 92%, transparent);
  box-shadow: var(--panel-shadow);
  overflow: hidden;
}

.empty-state,
.loading-state {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 86%, transparent);
  box-shadow: var(--shadow-soft);
  position: relative;
  overflow: hidden;
}

.empty-orbit {
  position: absolute;
  width: 280px;
  height: 280px;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--mystic) 10%, transparent), transparent 70%);
}

.empty-icon {
  font-size: 3rem;
  position: relative;
  z-index: 1;
}

.empty-title,
.loading-text {
  color: var(--text-primary);
  font-size: 1.15rem;
  position: relative;
  z-index: 1;
}

.empty-desc {
  color: var(--text-secondary);
  font-size: 0.92rem;
  position: relative;
  z-index: 1;
}

.spinner-shell {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--accent) 10%, transparent);
}

.spinner {
  width: 34px;
  height: 34px;
  border: 3px solid color-mix(in srgb, var(--text-primary) 10%, transparent);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.84s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1080px) {
  .content-layout {
    display: block;
  }

  .top-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .top-actions {
    justify-content: space-between;
    flex-wrap: wrap;
  }

  .theme-text {
    display: none;
  }

  .top-status-bar {
    max-width: 100%;
  }

  .top-status-text {
    white-space: normal;
    text-align: center;
  }
}

@media (max-width: 880px) {
  .left-rail,
  .resize-handle {
    display: none;
  }

  .main-area {
    padding: 14px 14px 108px;
  }

  .hero-stage {
    min-height: 320px;
  }

  .cover-frame {
    width: min(72vw, 320px);
  }

  .np-name {
    font-size: clamp(1.7rem, 7vw, 2.5rem);
  }
}
</style>
