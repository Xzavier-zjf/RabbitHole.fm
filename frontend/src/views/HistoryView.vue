<template>
  <div class="page-view">
    <header class="page-head">
      <button class="back-btn" type="button" @click="backToRadio">
        <ChevronLeft :size="18" />
        <span>返回播放</span>
      </button>
      <div class="head-copy">
        <div class="page-kicker">
          <History :size="16" />
          <span>Playback</span>
        </div>
        <h1>播放历史</h1>
        <p>最近听过的歌曲和频道足迹。</p>
        <p v-if="hintText" class="hint">{{ hintText }}</p>
      </div>
      <div class="days-tabs" aria-label="历史天数筛选">
        <button v-for="d in [1, 3, 7, 14]" :key="d" type="button" :class="{ active: days === d }" @click="days = d; fetch()">
          {{ d }}天
        </button>
      </div>
    </header>

    <section class="panel-card">
      <div class="panel-head">
        <div>
          <div class="section-kicker">Recent Tracks</div>
          <h2>最近播放</h2>
        </div>
        <span class="count-badge">{{ list.length }}</span>
      </div>

      <div class="loading-state" v-if="loading">
        <LoaderCircle :size="28" class="spin" />
        <span>加载中...</span>
      </div>

      <div class="list" v-else-if="list.length">
        <article class="track-row" v-for="h in list" :key="h.id || [h.songId, h.playedAt, h.songName].join('-')">
          <div class="track-cover fallback">
            <Music :size="18" />
          </div>
          <div class="track-info">
            <div class="track-title">{{ h.songName || '未知歌曲' }}</div>
            <div class="track-meta">{{ h.artists || '未知歌手' }}</div>
          </div>
          <button class="icon-action" type="button" @click="playHistory(h)" aria-label="播放歌曲" title="播放歌曲">
            <Play :size="17" fill="currentColor" />
          </button>
          <button class="icon-action" type="button" @click="saveToPlaylist(h)" aria-label="加入歌单" title="加入歌单">
            <ListPlus :size="17" />
          </button>
          <div class="track-time">
            <Clock :size="15" />
            <span>{{ formatTime(h.playedAt) }}</span>
          </div>
        </article>
      </div>

      <div class="empty" v-else-if="!loading">
        <CalendarDays :size="32" />
        <span>{{ emptyText }}</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { CalendarDays, ChevronLeft, Clock, History, ListPlus, LoaderCircle, Music, Play } from '@lucide/vue'
import { getHistory } from '../api'
import { useUserStore } from '../stores/user'
import { usePlayerStore } from '../stores/player'
import { usePlaylistsStore } from '../stores/playlists'
import { useNoticeStore } from '../stores/notice'

const LOCAL_HISTORY_KEY = 'rabbithole:play-history'

const userStore = useUserStore()
const playerStore = usePlayerStore()
const playlists = usePlaylistsStore()
const notice = useNoticeStore()
const router = useRouter()

const days = ref(7)
const list = ref([])
const loading = ref(false)
const loadFailed = ref(false)

const hintText = computed(() => {
  if (!userStore.isLoggedIn) {
    return '当前展示这台设备上的本地播放记录，登录后会叠加账号历史。'
  }
  if (loadFailed.value) {
    return '账号历史暂时不可用，下面先展示本地缓存记录。'
  }
  return ''
})

const emptyText = computed(() => {
  if (!userStore.isLoggedIn) {
    return '这台设备上还没有本地播放记录'
  }
  if (loadFailed.value) {
    return '历史加载失败，且本地也没有可展示的记录'
  }
  return '暂无播放记录'
})

onMounted(fetch)

async function fetch() {
  loading.value = true
  loadFailed.value = false
  const local = getLocalHistory(days.value)
  try {
    if (!userStore.isLoggedIn) {
      list.value = local
      return
    }
    const res = await getHistory(days.value)
    list.value = mergeHistory(res.data, local)
  } catch {
    loadFailed.value = true
    list.value = local
  } finally {
    loading.value = false
  }
}

function getLocalHistory(days) {
  try {
    const parsed = JSON.parse(localStorage.getItem(LOCAL_HISTORY_KEY) || '[]')
    if (!Array.isArray(parsed)) return []
    const since = Date.now() - days * 24 * 60 * 60 * 1000
    return parsed.filter((item) => {
      const time = Date.parse(item.playedAt || '')
      return Number.isFinite(time) && time >= since
    })
  } catch {
    return []
  }
}

function mergeHistory(remote, local) {
  const merged = [...(Array.isArray(remote) ? remote : []), ...local]
  const seen = new Set()
  return merged
    .filter((item) => {
      const key = [item.songId || '', item.playedAt || '', item.songName || ''].join('-')
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    .sort((a, b) => Date.parse(b.playedAt || '') - Date.parse(a.playedAt || ''))
}

function formatTime(t) {
  if (!t) return ''
  return t.replace('T', ' ').substring(0, 19)
}

function playHistory(item) {
  const index = playerStore.addToQueue(item)
  if (index < 0) {
    notice.show({
      type: 'warning',
      title: 'History',
      message: '这条历史缺少歌曲 ID，暂时不能播放。',
      duration: 2200,
    })
    return
  }
  playerStore.playItem(index)
  playerStore.setMiniPlayer(true)
}

function saveToPlaylist(item) {
  const added = playlists.addTrackToFirst(item)
  notice.show({
    type: added ? 'success' : 'info',
    title: 'Playlist',
    message: added ? '已加入我的歌单。' : '这首歌已经在歌单里了。',
    duration: 1800,
  })
}

function backToRadio() {
  const viewState = playerStore.getPlaybackViewState()
  playerStore.setPlaybackViewState({ showLyrics: !!viewState.showLyrics })
  router.push({ path: '/', query: { from: 'history' } })
}
</script>

<style scoped>
.page-view {
  min-height: 100vh;
  padding: 24px;
  color: var(--text-primary);
  overflow-y: auto;
}

.page-head,
.panel-card {
  width: min(980px, 100%);
  margin-inline: auto;
}

.page-head {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.head-copy {
  min-width: 0;
  flex: 1;
}

.back-btn {
  min-height: var(--control-height);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 800;
  flex-shrink: 0;
}

.back-btn:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
  background: var(--bg-card-hover);
}

.page-kicker,
.section-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--accent);
  font-size: 0.74rem;
  font-weight: 850;
  text-transform: uppercase;
}

.page-head h1 {
  margin-top: 8px;
  font-size: clamp(2rem, 4vw, 4rem);
  line-height: 1;
}

.page-head p {
  margin-top: 10px;
  color: var(--text-secondary);
}

.page-head .hint {
  color: var(--blue);
}

.days-tabs {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.days-tabs button {
  min-height: 38px;
  padding: 0 13px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 800;
}

.days-tabs button.active {
  border-color: transparent;
  background: var(--accent);
  color: #07100c;
}

.panel-card {
  padding: 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-card) 84%, transparent);
  box-shadow: var(--shadow-soft);
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.panel-head h2 {
  margin-top: 4px;
  font-size: 1.28rem;
}

.count-badge {
  min-width: 34px;
  min-height: 30px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-pill);
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  font-size: 0.8rem;
  font-weight: 850;
}

.list {
  display: grid;
  gap: 9px;
}

.track-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 62px;
  padding: 10px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
}

.track-cover {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
}

.track-cover.fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.track-info {
  min-width: 0;
  flex: 1;
}

.track-title,
.track-meta {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.track-title {
  font-weight: 850;
}

.track-meta {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.track-time {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--text-tertiary);
  font-size: 0.78rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.icon-action {
  width: var(--control-height);
  min-height: var(--control-height);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
}

.icon-action:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
  background: var(--bg-card-hover);
}

.loading-state,
.empty {
  min-height: 260px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

.spin {
  animation: spin 850ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 720px) {
  .page-view {
    padding: 16px;
  }

  .page-head {
    flex-direction: column;
  }

  .days-tabs {
    width: 100%;
    margin-left: 0;
    overflow-x: auto;
  }

  .track-row {
    align-items: flex-start;
  }

  .track-time span {
    display: none;
  }
}

@media (max-width: 520px) {
  .track-row {
    display: grid;
    grid-template-columns: 44px minmax(0, 1fr) auto auto auto;
  }
}
</style>
