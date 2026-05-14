<template>
  <div class="page-view">
    <div class="header">
      <button class="back-btn" @click="backToRadio">← 返回兔子洞</button>
      <div class="header-copy">
        <h2>播放历史</h2>
        <p>你最近都在往哪些方向坠落。</p>
        <p v-if="hintText" class="hint">{{ hintText }}</p>
      </div>
      <div class="days-tabs">
        <button v-for="d in [1, 3, 7, 14]" :key="d" :class="{ active: days === d }" @click="days = d; fetch()">
          {{ d }}天
        </button>
      </div>
    </div>

    <div class="loading-state" v-if="loading">加载中...</div>
    <div class="list" v-else-if="list.length">
      <div class="item" v-for="h in list" :key="h.id || [h.songId, h.playedAt, h.songName].join('-')">
        <div class="item-info">
          <div class="item-name">{{ h.songName || '未知歌曲' }}</div>
          <div class="item-artists">{{ h.artists || '未知歌手' }}</div>
        </div>
        <div class="item-time">{{ formatTime(h.playedAt) }}</div>
      </div>
    </div>
    <div class="empty" v-else-if="!loading">{{ emptyText }}</div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getHistory } from '../api'
import { useUserStore } from '../stores/user'
import { usePlayerStore } from '../stores/player'

const LOCAL_HISTORY_KEY = 'rabbithole:play-history'

const userStore = useUserStore()
const playerStore = usePlayerStore()
const router = useRouter()

const days = ref(7)
const list = ref([])
const loading = ref(false)
const loadFailed = ref(false)

const hintText = computed(() => {
  if (!userStore.isLoggedIn) {
    return '当前展示的是这台设备上的本地播放记录，登录后会自动叠加账号历史。'
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

function backToRadio() {
  const viewState = playerStore.getPlaybackViewState()
  playerStore.setPlaybackViewState({ showLyrics: !!viewState.showLyrics })
  router.push({ path: '/', query: { from: 'history' } })
}
</script>

<style scoped>
.page-view {
  min-height: 100vh;
  padding: 28px;
  color: var(--text-primary);
  overflow-y: auto;
}

.header {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 28px;
  flex-wrap: wrap;
}

.header-copy p {
  color: var(--text-secondary);
  margin-top: 4px;
}

.hint {
  color: var(--mystic);
}

.back-btn,
.days-tabs button {
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  border-radius: 999px;
  cursor: pointer;
}

.back-btn {
  padding: 10px 16px;
  color: var(--text-secondary);
}

.days-tabs {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

.days-tabs button {
  color: var(--text-secondary);
  padding: 8px 14px;
}

.days-tabs button.active {
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 60%, var(--mystic)));
  color: #fff7ef;
  border-color: transparent;
}

.list {
  max-width: 760px;
  display: grid;
  gap: 10px;
}

.item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  border: 1px solid var(--divider);
}

.item-name {
  font-size: 0.98rem;
  font-weight: 600;
}

.item-artists {
  color: var(--text-secondary);
  font-size: 0.8rem;
  margin-top: 4px;
}

.item-time {
  color: var(--text-tertiary);
  font-size: 0.78rem;
  white-space: nowrap;
}

.empty,
.loading-state {
  color: var(--text-tertiary);
  text-align: center;
  padding: 60px 0;
}
</style>
