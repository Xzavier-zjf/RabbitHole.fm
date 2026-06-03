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
          <div class="brand-mark" aria-hidden="true">
            <span class="brand-wave"></span>
          </div>
          <div class="brand-copy">
            <div class="brand-title">RabbitHole.fm</div>
            <div class="brand-subtitle">{{ themeLabel }}</div>
          </div>
        </div>

        <div class="top-actions">
          <SearchBar @play="playSearchResult" />
          <button class="icon-btn" type="button" @click="openRoomShare" aria-label="房间二维码分享" title="房间二维码分享">
            <QrCode :size="18" />
          </button>
          <button class="icon-btn" @click="toggleTheme" :aria-label="themeToggleLabel" :title="themeToggleLabel">
            <Sun v-if="theme === 'dark'" :size="18" />
            <Moon v-else :size="18" />
          </button>
        </div>
      </header>

      <div class="mobile-channel-strip">
        <button
          v-for="ch in mobileChannels"
          :key="ch.id"
          :class="{ active: player.currentChannelId === ch.id }"
          @click="onChannelSelect(ch.id)"
        >
          {{ ch.name }}
        </button>
      </div>

      <transition name="top-status">
        <div
          v-if="topStatusVisible"
          class="top-status-bar"
          :class="'tone-' + topStatusTone"
          role="status"
          aria-live="polite"
        >
          <span class="top-status-dot"></span>
          <span v-if="topStatusLabel" class="top-status-chip">{{ topStatusLabel }}</span>
          <span class="top-status-text">{{ topStatusMessage }}</span>
        </div>
      </transition>

      <div class="content-layout">
        <section class="center-stage">
          <div class="stage-shell" :class="{ 'lyrics-mode': showLyrics, 'is-dj': isDj }">
            <div class="stage-empty" v-if="!player.currentItem && !player.isLoading">
              <div class="empty-mark">
                <Radio :size="34" />
              </div>
              <h1>{{ player.error || '选择一个频道开始播放' }}</h1>
              <p>频道、搜索和点歌队列已经准备好，当前播放会在这里展开。</p>
            </div>

            <div class="stage-empty loading-state" v-if="player.isLoading">
              <div class="spinner-shell">
                <LoaderCircle :size="34" />
              </div>
              <h1>正在载入频道</h1>
              <p>正在连接音乐源并准备播放列表。</p>
            </div>

            <template v-if="player.currentItem">
              <section v-if="!showLyrics" class="now-playing-stage">
                <div class="cover-ambient" :style="ambientCoverStyle"></div>

                <div class="cover-column">
                  <button class="cover-frame" @click="toggleLyrics" title="打开歌词" aria-label="打开歌词">
                    <img
                      v-if="showMainCover"
                      :src="proxyCoverUrl(player.currentItem.coverUrl)"
                      class="np-cover"
                      @error="onMainCoverError"
                      referrerpolicy="no-referrer"
                    />
                    <div v-else class="cover-fallback">
                      <Music :size="58" />
                    </div>
                    <div class="cover-mask"></div>
                  </button>
                </div>

                <div class="np-copy">
                  <div class="np-kicker">
                    <span class="live-dot"></span>
                    <span>{{ isDj ? 'DJ Interlude' : 'Now Playing' }}</span>
                    <span v-if="sourceLabel(player.currentItem)" class="source-badge">{{ sourceLabel(player.currentItem) }}</span>
                  </div>
                  <h1>{{ player.currentItem.name }}</h1>
                  <p class="np-artist">{{ (player.currentItem.artists || []).join(' / ') || '未知歌手' }}</p>
                  <p v-if="player.currentItem.requester" class="np-requester">
                    来自 {{ player.currentItem.requester }} 的点歌
                  </p>

                  <div class="np-actions">
                    <button class="primary-btn" @click="toggleLyrics">
                      <Rows3 :size="18" />
                      <span>{{ isDj ? '查看字幕' : '查看歌词' }}</span>
                    </button>
                    <button
                      v-if="player.currentItem.songId"
                      class="ghost-btn"
                      :class="{ active: isFavorited }"
                      @click="toggleFav"
                      :title="isFavorited ? '取消收藏' : '收藏歌曲'"
                    >
                      <Heart :size="18" :fill="isFavorited ? 'currentColor' : 'none'" />
                      <span>{{ isFavorited ? '已收藏' : '收藏' }}</span>
                    </button>
                    <button
                      v-if="player.currentItem.songId"
                      class="ghost-btn"
                      type="button"
                      @click="addCurrentToPlaylist"
                      title="加入我的歌单"
                    >
                      <ListPlus :size="18" />
                      <span>加入歌单</span>
                    </button>
                    <button class="ghost-btn" type="button" @click="openRoomShare" title="打开房间二维码分享">
                      <QrCode :size="18" />
                      <span>分享房间</span>
                    </button>
                    <button
                      class="ghost-btn"
                      type="button"
                      :class="{ active: player.smartContinueEnabled }"
                      @click="player.toggleSmartContinue()"
                      :title="player.smartContinueEnabled ? '关闭智能续播' : '开启智能续播'"
                    >
                      <Repeat2 :size="18" />
                      <span>{{ player.smartContinueEnabled ? '智能续播' : '续播关闭' }}</span>
                    </button>
                    <button
                      class="ghost-btn"
                      type="button"
                      :class="{ active: player.miniPlayerEnabled }"
                      @click="player.toggleMiniPlayer()"
                      :title="player.miniPlayerEnabled ? '关闭迷你播放器' : '打开迷你播放器'"
                    >
                      <PictureInPicture2 :size="18" />
                      <span>迷你</span>
                    </button>
                  </div>
                </div>
              </section>

              <template v-else>
                <section class="lyrics-top-bar">
                  <button class="icon-btn" @click="toggleLyrics" aria-label="返回封面" title="返回封面">
                    <ChevronLeft :size="20" />
                  </button>
                  <img
                    v-if="showMiniCover"
                    :src="proxyCoverUrl(player.currentItem.coverUrl)"
                    class="lyrics-mini-cover"
                    @error="onMiniCoverError"
                    referrerpolicy="no-referrer"
                  />
                  <div v-else class="lyrics-mini-cover cover-fallback mini-fallback">
                    <Music :size="18" />
                  </div>
                  <div class="lyrics-mini-info">
                    <div class="lyrics-mini-name">{{ player.currentItem.name }}</div>
                    <div class="lyrics-mini-artist">
                      <span>{{ (player.currentItem.artists || []).join(' / ') || '未知歌手' }}</span>
                      <span v-if="sourceLabel(player.currentItem)" class="source-badge mini">{{ sourceLabel(player.currentItem) }}</span>
                    </div>
                  </div>
                  <button
                    v-if="player.currentItem.songId"
                    class="icon-btn"
                    :class="{ active: isFavorited }"
                    @click="toggleFav"
                    :title="isFavorited ? '取消收藏' : '收藏歌曲'"
                  >
                    <Heart :size="18" :fill="isFavorited ? 'currentColor' : 'none'" />
                  </button>
                </section>

                <section class="lyrics-expanded">
                  <DjWaveform v-if="isDj" />
                  <LyricsPanel v-else />
                </section>
              </template>
            </template>
          </div>
        </section>

        <RequestQueuePanel :channel-id="player.currentChannelId || 32953014" />
      </div>
    </main>

    <PlayerBar />

    <Teleport to="body">
      <transition name="share-modal">
        <div
          v-if="roomShareOpen"
          class="room-share-overlay"
          role="presentation"
          @click.self="closeRoomShare"
        >
          <section
            ref="roomShareModal"
            class="room-share-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="room-share-title"
            tabindex="-1"
            @keydown.escape="closeRoomShare"
          >
            <div class="room-share-head">
              <div>
                <div class="room-share-kicker">Room Request</div>
                <h2 id="room-share-title">房间二维码分享</h2>
              </div>
              <button class="room-share-close" type="button" @click="closeRoomShare" aria-label="关闭分享面板" title="关闭分享面板">
                <X :size="18" />
              </button>
            </div>

            <div class="room-share-body">
              <div class="qr-frame" :class="{ loading: roomShareLoading }">
                <LoaderCircle v-if="roomShareLoading" :size="30" />
                <img v-else-if="roomShareQrDataUrl" :src="roomShareQrDataUrl" alt="房间点歌二维码" />
                <div v-else class="qr-fallback">
                  <QrCode :size="34" />
                  <span>{{ roomShareError || '二维码暂时无法生成' }}</span>
                </div>
              </div>

              <div class="room-share-copy">
                <div class="share-channel">频道 {{ player.currentChannelId || 32953014 }}</div>
                <p>扫码进入当前频道的点歌房间。</p>
                <label class="share-link-field">
                  <span>房间链接</span>
                  <input :value="roomShareLink" readonly @focus="$event.target.select()" />
                </label>
              </div>
            </div>

            <div class="room-share-actions">
              <button class="primary-btn" type="button" @click="copyRoomLink(roomShareLink)">
                <Copy :size="18" />
                <span>复制链接</span>
              </button>
              <a class="ghost-btn" :href="roomShareLink" target="_blank" rel="noreferrer">
                <ExternalLink :size="18" />
                <span>打开点歌页</span>
              </a>
            </div>
          </section>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import QRCode from 'qrcode'
import {
  ChevronLeft,
  Copy,
  ExternalLink,
  Heart,
  LoaderCircle,
  ListPlus,
  Moon,
  Music,
  PictureInPicture2,
  QrCode,
  Radio,
  Repeat2,
  Rows3,
  Sun,
  X,
} from '@lucide/vue'
import { usePlayerStore } from '../stores/player'
import { useUserStore } from '../stores/user'
import { useNoticeStore } from '../stores/notice'
import { usePlaylistsStore } from '../stores/playlists'
import { proxyCoverUrl, addFavorite, removeFavorite, getFavorites } from '../api'
import { favoritePayload, musicKey, musicSource, sourceSongId } from '../utils/music-source'
import { useTheme } from '../composables/theme'
import ChannelList from '../components/ChannelList.vue'
import LyricsPanel from '../components/LyricsPanel.vue'
import DjWaveform from '../components/DjWaveform.vue'
import SearchBar from '../components/SearchBar.vue'
import PlayerBar from '../components/PlayerBar.vue'
import RequestQueuePanel from '../components/RequestQueuePanel.vue'

const player = usePlayerStore()
const userStore = useUserStore()
const notice = useNoticeStore()
const playlists = usePlaylistsStore()
const { theme, toggleTheme } = useTheme()
const router = useRouter()
const route = useRoute()

const mobileChannels = [
  { id: 32953014, name: '华语热歌' },
  { id: 19723756, name: '飙升榜' },
  { id: 3778678, name: '热歌榜' },
  { id: 3779629, name: '新歌榜' },
]

const isDj = computed(() => player.currentItem?.type === 'dj')
const themeLabel = computed(() => {
  return theme.value === 'dark' ? 'Immersive music console' : 'Clean listening workspace'
})
const themeToggleLabel = computed(() => {
  return theme.value === 'dark' ? '切换到浅色主题' : '切换到深色主题'
})

const sidebarWidth = ref(292)
const MIN_WIDTH = 240
const MAX_WIDTH = 384
const rootEl = ref(null)
const favIds = ref(new Set())
const volume = ref(0.8)
const mainCoverFailed = ref(false)
const miniCoverFailed = ref(false)
const topStatusMessage = ref('')
const topStatusLabel = ref('')
const topStatusTone = ref('resume')
const topStatusVisible = ref(false)
const roomShareOpen = ref(false)
const roomShareLink = ref('')
const roomShareQrDataUrl = ref('')
const roomShareLoading = ref(false)
const roomShareError = ref('')
const roomShareModal = ref(null)
let topStatusTimer = null

const showLyrics = ref(false)

const showMainCover = computed(() => !!player.currentItem?.coverUrl && !mainCoverFailed.value)
const showMiniCover = computed(() => !!player.currentItem?.coverUrl && !miniCoverFailed.value)
const ambientCoverStyle = computed(() => {
  if (!showMainCover.value) return undefined
  return {
    backgroundImage: `url("${proxyCoverUrl(player.currentItem.coverUrl)}")`,
  }
})

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
  return player.currentItem?.songId ? favIds.value.has(musicKey(player.currentItem)) : false
})

async function loadFavs() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await getFavorites()
    favIds.value = new Set((res.data || []).map(musicKey))
  } catch { /* ignore */ }
}

async function toggleFav() {
  if (!userStore.isLoggedIn) return
  const item = player.currentItem
  if (!item?.songId) return
  const key = musicKey(item)
  try {
    if (favIds.value.has(key)) {
      await removeFavorite(item.songId, {
        source: musicSource(item),
        sourceSongId: sourceSongId(item),
      })
      favIds.value.delete(key)
    } else {
      await addFavorite(item.songId, favoritePayload(item))
      favIds.value.add(key)
    }
  } catch { /* ignore */ }
}

function sourceLabel(item) {
  const source = item?.source || 'netease'
  if (source === 'netease') return ''
  return item?.sourceLabel || source
}

function onKeydown(e) {
  if (isInteractiveTarget(e.target)) return
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

function isInteractiveTarget(target) {
  if (!(target instanceof HTMLElement)) return false
  return !!target.closest('input, textarea, select, button, a, [role="button"], [contenteditable="true"]')
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
        ? '已回到歌词视图，继续播放 ' + trackName
        : '已从播放历史回到 ' + trackName,
      tone: 'return',
      label: '播放历史',
    }
  }

  if (from === 'favorites') {
    return {
      message: snapshot?.showLyrics
        ? '已从收藏回到 ' + trackName + ' 的歌词视图'
        : '已从收藏回到当前播放',
      tone: 'return',
      label: '我的收藏',
    }
  }

  const seconds = Math.max(0, Math.round(snapshot?.currentTime || 0))
  const minute = Math.floor(seconds / 60)
  const second = String(seconds % 60).padStart(2, '0')
  return {
    message: seconds > 0
      ? '已从 ' + minute + ':' + second + ' 附近继续播放 ' + trackName
      : '已恢复当前频道与播放队列',
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
  const routeChannelId = readRouteChannelId()
  if (routeChannelId && routeChannelId !== player.currentChannelId) {
    await player.buildQueue(routeChannelId)
  } else if (!player.currentItem && player.queue.length === 0) {
    await player.buildQueue(routeChannelId || player.currentChannelId || snapshot?.channelId || 19723756, {
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

watch(
  () => route.query.channelId,
  async () => {
    const routeChannelId = readRouteChannelId()
    if (!routeChannelId || routeChannelId === player.currentChannelId) return
    showLyrics.value = false
    await player.buildQueue(routeChannelId)
    showTopStatus('已切换到频道 ' + routeChannelId, 'return', 2200, '探索')
  }
)

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
  const idx = player.addToQueue(song)
  if (idx < 0) {
    notice.show({
      type: 'warning',
      title: 'Search',
      message: '这首歌缺少可播放 ID，暂时不能加入队列。',
      duration: 2200,
    })
    return
  }
  player.playItem(idx)
}

function addCurrentToPlaylist() {
  if (!player.currentItem?.songId) return
  const added = playlists.addTrackToFirst(player.currentItem)
  notice.show({
    type: added ? 'success' : 'info',
    title: 'Playlist',
    message: added ? '已加入我的歌单。' : '这首歌已经在歌单里了。',
    duration: 1800,
  })
}

async function openRoomShare() {
  const channelId = player.currentChannelId || 32953014
  const link = buildRoomLink(channelId)
  roomShareOpen.value = true
  roomShareLink.value = link
  roomShareQrDataUrl.value = ''
  roomShareError.value = ''
  roomShareLoading.value = true
  await nextTick()
  roomShareModal.value?.focus()
  try {
    roomShareQrDataUrl.value = await QRCode.toDataURL(link, {
      width: 264,
      margin: 1,
      errorCorrectionLevel: 'M',
      color: {
        dark: '#101417',
        light: '#ffffff',
      },
    })
  } catch {
    roomShareError.value = '请使用下方链接分享。'
  } finally {
    roomShareLoading.value = false
  }
}

function closeRoomShare() {
  roomShareOpen.value = false
}

async function copyRoomLink(link = '') {
  const roomLink = link || buildRoomLink(player.currentChannelId || 32953014)
  const copied = await copyText(roomLink)
  notice.show({
    type: copied ? 'success' : 'info',
    title: 'Room',
    message: copied ? '房间点歌链接已复制。' : roomLink,
    duration: copied ? 2200 : 5000,
  })
}

function buildRoomLink(channelId) {
  const origin = typeof window !== 'undefined' ? window.location.origin : ''
  const url = new URL('/request', origin || 'http://localhost')
  url.searchParams.set('channelId', String(channelId))
  url.searchParams.set('room', `channel-${channelId}`)
  return url.toString()
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}

function readRouteChannelId() {
  const raw = Array.isArray(route.query.channelId) ? route.query.channelId[0] : route.query.channelId
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
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
  z-index: 3;
}

.resize-handle {
  width: 6px;
  cursor: col-resize;
  flex-shrink: 0;
  background: linear-gradient(180deg, transparent, var(--divider-strong), transparent);
  opacity: 0.6;
  transition: opacity var(--transition-fast);
}

.resize-handle:hover {
  opacity: 1;
}

.main-area {
  flex: 1;
  min-width: 0;
  height: 100vh;
  padding: 16px 16px calc(var(--player-height) + 24px);
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.top-bar {
  min-height: 66px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 12px 14px 12px 16px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-lg);
  background: var(--bg-glass);
  backdrop-filter: blur(18px);
  box-shadow: var(--shadow-soft);
}

.brand-block {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 220px;
}

.brand-mark {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 22%, transparent), color-mix(in srgb, var(--blue) 20%, transparent)),
    var(--bg-card);
  border: 1px solid var(--divider);
}

.brand-wave {
  width: 22px;
  height: 18px;
  border-radius: 999px;
  border: 2px solid var(--accent);
  border-left-color: transparent;
  border-right-color: var(--blue);
  transform: rotate(-16deg);
}

.brand-copy {
  min-width: 0;
}

.brand-title {
  font-size: 1.05rem;
  font-weight: 760;
  letter-spacing: 0;
}

.brand-subtitle {
  margin-top: 2px;
  color: var(--text-secondary);
  font-size: 0.78rem;
}

.top-actions {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 12px;
}

.icon-btn,
.primary-btn,
.ghost-btn {
  min-width: var(--control-height);
  min-height: var(--control-height);
  border-radius: var(--radius-md);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 88%, transparent);
  color: var(--text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  transition:
    transform var(--transition-fast),
    border-color var(--transition-fast),
    background var(--transition-fast),
    color var(--transition-fast);
}

.icon-btn:hover,
.ghost-btn:hover {
  transform: translateY(-1px);
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 38%, var(--divider));
  background: var(--bg-card-hover);
}

.icon-btn.active,
.ghost-btn.active {
  color: var(--coral);
  border-color: color-mix(in srgb, var(--coral) 36%, var(--divider));
}

.primary-btn,
.ghost-btn {
  padding: 0 16px;
  font-weight: 700;
}

.primary-btn {
  border-color: transparent;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 58%, var(--blue)));
  color: #07100c;
}

.primary-btn:hover {
  transform: translateY(-1px);
  filter: saturate(1.08);
}

.mobile-channel-strip {
  display: none;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.mobile-channel-strip button {
  min-height: 38px;
  flex: 0 0 auto;
  padding: 0 14px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--divider);
  background: var(--bg-card);
  color: var(--text-secondary);
}

.mobile-channel-strip button.active {
  color: #07100c;
  border-color: transparent;
  background: var(--accent);
}

.top-status-bar {
  align-self: center;
  max-width: min(100%, 720px);
  min-height: 36px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 90%, transparent);
  box-shadow: var(--shadow-soft);
  color: var(--text-secondary);
}

.top-status-bar.tone-return {
  border-color: color-mix(in srgb, var(--blue) 34%, var(--divider));
}

.top-status-dot {
  width: 7px;
  height: 7px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--accent) 14%, transparent);
}

.top-status-chip {
  flex-shrink: 0;
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--blue) 16%, transparent);
  color: var(--blue);
  font-size: 0.72rem;
  font-weight: 700;
}

.top-status-text {
  min-width: 0;
  color: var(--text-secondary);
  font-size: 0.82rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.top-status-enter-active,
.top-status-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.top-status-enter-from,
.top-status-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

.content-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: stretch;
}

.center-stage,
.stage-shell {
  min-width: 0;
  min-height: 0;
}

.stage-shell {
  position: relative;
  height: 100%;
  overflow: hidden;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 86%, transparent), color-mix(in srgb, var(--bg-card) 94%, transparent));
  box-shadow: var(--panel-shadow);
}

.stage-empty {
  height: 100%;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  padding: 28px;
  text-align: center;
}

.empty-mark,
.spinner-shell {
  width: 72px;
  height: 72px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-card));
  color: var(--accent);
}

.spinner-shell svg {
  animation: spin 900ms linear infinite;
}

.stage-empty h1 {
  max-width: 560px;
  font-size: clamp(1.6rem, 3vw, 2.8rem);
  line-height: 1.08;
  letter-spacing: 0;
}

.stage-empty p {
  max-width: 520px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.now-playing-stage {
  position: relative;
  z-index: 1;
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(260px, 0.82fr) minmax(320px, 1fr);
  align-items: center;
  gap: clamp(24px, 4vw, 58px);
  padding: clamp(28px, 4vw, 58px);
  overflow: hidden;
}

.cover-ambient {
  position: absolute;
  inset: -20%;
  z-index: -1;
  background-position: center;
  background-size: cover;
  opacity: 0.16;
  filter: blur(44px) saturate(1.2);
  transform: scale(1.05);
  mask-image: radial-gradient(circle at 38% 50%, black, transparent 62%);
}

.cover-column {
  display: grid;
  place-items: center;
}

.cover-frame {
  width: min(100%, 390px);
  aspect-ratio: 1 / 1;
  position: relative;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid var(--divider-strong);
  border-radius: 22px;
  background: var(--bg-card);
  box-shadow: var(--shadow-cover);
  cursor: pointer;
}

.np-cover,
.cover-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  display: grid;
  place-items: center;
  color: color-mix(in srgb, var(--text-primary) 70%, transparent);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--accent) 22%, var(--bg-card)), color-mix(in srgb, var(--blue) 18%, var(--bg-primary)));
}

.cover-mask {
  position: absolute;
  inset: 0;
  background: var(--cover-mask);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.cover-frame:hover .cover-mask {
  opacity: 1;
}

.np-copy {
  min-width: 0;
  display: grid;
  gap: 14px;
}

.np-kicker {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--accent);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0;
  text-transform: uppercase;
}

.source-badge {
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--blue) 13%, transparent);
  color: var(--blue);
  font-size: 0.66rem;
  font-weight: 850;
  text-transform: none;
}

.source-badge.mini {
  min-height: 18px;
  padding: 0 7px;
  font-size: 0.64rem;
  flex-shrink: 0;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--accent) 14%, transparent);
}

.np-copy h1 {
  max-width: 820px;
  max-height: min(48vh, 430px);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 5;
  overflow: hidden;
  font-size: clamp(2.45rem, 4.9vw, 5.4rem);
  line-height: 1.02;
  letter-spacing: 0;
  overflow-wrap: anywhere;
}

.np-artist {
  color: var(--text-secondary);
  font-size: clamp(1rem, 1.5vw, 1.25rem);
  line-height: 1.5;
}

.np-requester {
  width: fit-content;
  max-width: 100%;
  color: var(--blue);
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--blue) 28%, var(--divider));
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--blue) 10%, transparent);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.np-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.lyrics-top-bar {
  min-height: 68px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 72%, transparent);
}

.lyrics-mini-cover {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}

.mini-fallback {
  display: grid;
  place-items: center;
}

.lyrics-mini-info {
  min-width: 0;
  flex: 1;
}

.lyrics-mini-name,
.lyrics-mini-artist {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lyrics-mini-name {
  color: var(--text-primary);
  font-size: 0.96rem;
  font-weight: 700;
}

.lyrics-mini-artist {
  display: flex;
  align-items: center;
  gap: 7px;
  color: var(--text-secondary);
  font-size: 0.8rem;
  margin-top: 3px;
}

.lyrics-expanded {
  height: calc(100% - 68px);
  min-height: 0;
  overflow: hidden;
}

.room-share-overlay {
  position: fixed;
  inset: 0;
  z-index: 1300;
  display: grid;
  place-items: center;
  padding: 22px;
  background: var(--bg-overlay);
  backdrop-filter: blur(18px);
}

.room-share-modal {
  width: min(620px, 100%);
  max-height: min(720px, calc(100vh - 44px));
  display: grid;
  gap: 18px;
  padding: 20px;
  overflow-y: auto;
  border: 1px solid var(--divider-strong);
  border-radius: var(--radius-xl);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 96%, transparent), var(--bg-card));
  box-shadow: var(--panel-shadow);
  outline: none;
}

.room-share-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.room-share-kicker {
  color: var(--accent);
  font-size: 0.74rem;
  font-weight: 850;
  text-transform: uppercase;
}

.room-share-head h2 {
  margin-top: 4px;
  color: var(--text-primary);
  font-size: clamp(1.35rem, 3vw, 2rem);
  line-height: 1.1;
}

.room-share-close {
  width: var(--control-height);
  height: var(--control-height);
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
}

.room-share-close:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 32%, var(--divider));
  background: var(--bg-card-hover);
}

.room-share-body {
  display: grid;
  grid-template-columns: 244px minmax(0, 1fr);
  gap: 18px;
  align-items: center;
}

.qr-frame {
  width: 244px;
  aspect-ratio: 1 / 1;
  display: grid;
  place-items: center;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--accent) 24%, var(--divider));
  border-radius: 18px;
  background: #ffffff;
  box-shadow: 0 18px 50px color-mix(in srgb, var(--bg-primary) 28%, transparent);
}

.qr-frame.loading {
  color: #101417;
}

.qr-frame.loading svg {
  animation: spin 900ms linear infinite;
}

.qr-frame img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.qr-fallback {
  display: grid;
  place-items: center;
  gap: 10px;
  color: #101417;
  text-align: center;
  font-size: 0.84rem;
  font-weight: 800;
}

.room-share-copy {
  min-width: 0;
}

.share-channel {
  width: fit-content;
  max-width: 100%;
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  padding: 0 10px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--blue) 12%, transparent);
  color: var(--blue);
  font-size: 0.78rem;
  font-weight: 850;
}

.room-share-copy p {
  margin-top: 12px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.share-link-field {
  display: grid;
  gap: 8px;
  margin-top: 16px;
}

.share-link-field span {
  color: var(--text-tertiary);
  font-size: 0.72rem;
  font-weight: 850;
  text-transform: uppercase;
}

.share-link-field input {
  width: 100%;
  height: var(--control-height);
  min-width: 0;
  padding: 0 12px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  color: var(--text-primary);
  outline: none;
}

.share-link-field input:focus {
  border-color: color-mix(in srgb, var(--accent) 46%, var(--input-border));
}

.room-share-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.share-modal-enter-active,
.share-modal-leave-active {
  transition: opacity 180ms ease;
}

.share-modal-enter-active .room-share-modal,
.share-modal-leave-active .room-share-modal {
  transition: opacity 180ms ease, transform 180ms ease;
}

.share-modal-enter-from,
.share-modal-leave-to {
  opacity: 0;
}

.share-modal-enter-from .room-share-modal,
.share-modal-leave-to .room-share-modal {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1360px) {
  .content-layout {
    grid-template-columns: minmax(0, 1fr) 320px;
  }

  .now-playing-stage {
    grid-template-columns: minmax(230px, 0.78fr) minmax(280px, 1fr);
  }
}

@media (max-width: 1200px) {
  .content-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (max-width: 960px) {
  .top-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .top-actions {
    justify-content: stretch;
  }

  .brand-block {
    min-width: 0;
  }

  .now-playing-stage {
    grid-template-columns: 1fr;
    align-content: center;
    gap: 24px;
    padding: 28px;
  }

  .cover-frame {
    width: min(64vw, 310px);
  }

  .np-copy {
    text-align: center;
    justify-items: center;
  }

  .np-copy h1 {
    max-height: 34vh;
    -webkit-line-clamp: 4;
    font-size: clamp(1.8rem, 7.6vw, 3.2rem);
  }
}

@media (max-width: 880px) {
  .left-rail,
  .resize-handle {
    display: none;
  }

  .main-area {
    padding: 12px 12px calc(var(--player-height) + 38px);
  }

  .mobile-channel-strip {
    display: flex;
  }

  .top-status-text {
    white-space: normal;
  }

  .stage-shell {
    border-radius: var(--radius-lg);
  }
}

@media (max-width: 520px) {
  .top-actions {
    gap: 8px;
  }

  .now-playing-stage {
    padding: 22px 18px;
  }

  .cover-frame {
    width: min(76vw, 280px);
  }

  .room-share-overlay {
    padding: 12px;
    align-items: end;
  }

  .room-share-modal {
    max-height: calc(100vh - 24px);
    padding: 16px;
    border-radius: var(--radius-lg);
  }

  .room-share-body {
    grid-template-columns: 1fr;
    justify-items: center;
  }

  .qr-frame {
    width: min(100%, 244px);
  }

  .room-share-copy {
    width: 100%;
  }

  .room-share-actions {
    justify-content: stretch;
  }

  .room-share-actions .primary-btn,
  .room-share-actions .ghost-btn {
    flex: 1 1 160px;
  }
}
</style>
