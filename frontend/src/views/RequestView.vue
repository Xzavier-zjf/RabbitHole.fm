<template>
  <div class="request-view">
    <header class="page-head">
      <button class="back-btn" type="button" @click="$router.push('/')">
        <ChevronLeft :size="18" />
        <span>返回播放</span>
      </button>
      <div class="page-title-block">
        <div class="page-kicker">
          <MessageSquareText :size="16" />
          <span>Audience Requests</span>
        </div>
        <h1>点歌留言</h1>
        <p>搜索一首歌，写下一句话，加入当前频道的播放队列。</p>
        <div v-if="roomId" class="room-chip">
          <Radio :size="15" />
          <span>房间 {{ roomId }} · 频道 {{ activeChannelId }}</span>
        </div>
      </div>
    </header>

    <section class="request-search-card">
      <div class="search-wrap">
        <Search :size="19" />
        <input
          v-model="keyword"
          placeholder="搜索歌曲、歌手或专辑"
          @keydown.enter.prevent="onEnter"
          @compositionstart="onCompStart"
          @compositionend="onCompEnd"
        />
        <button class="primary-btn" type="button" @click="search" :disabled="searching">
          <LoaderCircle v-if="searching" :size="18" class="spin" />
          <Search v-else :size="18" />
          <span>{{ searching ? '搜索中' : '搜索' }}</span>
        </button>
      </div>
      <p v-if="searchError" class="inline-error">{{ searchError }}</p>
    </section>

    <div class="request-layout">
      <section class="panel-card results-card">
        <div class="section-head">
          <div>
            <div class="section-kicker">Pick A Song</div>
            <h2>搜索结果</h2>
          </div>
          <span class="count-badge">{{ results.length }}</span>
        </div>

        <div class="results" v-if="results.length">
          <article class="track-row" v-for="s in results" :key="`${s.source || 'netease'}-${s.id}`">
            <img v-if="s.coverUrl" :src="proxyCoverUrl(s.coverUrl)" class="track-cover" referrerpolicy="no-referrer" />
            <span v-else class="track-cover fallback">
              <Music :size="18" />
            </span>
            <span class="track-copy">
              <span class="track-title-row">
                <span class="track-title">{{ s.name }}</span>
                <span v-if="sourceLabel(s)" class="source-badge">{{ sourceLabel(s) }}</span>
              </span>
              <span class="track-meta">{{ (s.artists || []).join(' / ') || '未知歌手' }}</span>
            </span>
            <button
              class="pick-icon"
              type="button"
              :disabled="!canRequestSong(s)"
              @click="pick(s)"
              :aria-label="canRequestSong(s) ? '选择点歌' : '外部源暂不支持房间点歌'"
              :title="canRequestSong(s) ? '选择点歌' : '外部源暂不支持房间点歌，可先试听或加入歌单'"
            >
              <Plus :size="18" />
            </button>
            <button class="pick-icon secondary" type="button" @click="saveSong(s)" aria-label="加入歌单" title="加入歌单">
              <ListPlus :size="18" />
            </button>
          </article>
        </div>

        <div class="empty-state" v-else>
          <Music :size="28" />
          <span>搜索一首歌，或试试“陈奕迅”“周杰伦”“宇多田光”。</span>
        </div>
      </section>

      <section class="panel-card draft-card">
        <div class="section-head">
          <div>
            <div class="section-kicker">Leave A Note</div>
            <h2>给 DJ 的话</h2>
          </div>
        </div>

        <div class="draft" v-if="pickedSong">
          <div class="picked-info">
            <div class="picked-label">已选择</div>
            <div class="picked-title">{{ pickedSong.name }}</div>
            <div class="picked-meta">{{ (pickedSong.artists || []).join(' / ') || '未知歌手' }}</div>
          </div>
          <p v-if="pickedSong && !canRequestSong(pickedSong)" class="inline-error">
            这个结果来自 {{ pickedSong.sourceLabel || pickedSong.source }}，当前房间点歌只支持网易云来源。
          </p>
          <textarea
            v-model="message"
            placeholder="想对谁说什么？可选，最多 80 字。"
            maxlength="80"
          />
          <div class="draft-actions">
            <button class="ghost-btn" type="button" @click="pickedSong = null">
              <X :size="18" />
              <span>取消选择</span>
            </button>
            <button class="primary-btn" type="button" @click="submitRequest" :disabled="submitting || !canRequestSong(pickedSong)">
              <LoaderCircle v-if="submitting" :size="18" class="spin" />
              <Send v-else :size="18" />
              <span>{{ submitting ? '提交中' : '提交点歌' }}</span>
            </button>
          </div>
          <p v-if="resultMsg" :class="['result-msg', { ok: resultOk, err: !resultOk }]">{{ resultMsg }}</p>
        </div>

        <div class="empty-state" v-else>
          <MessageSquareText :size="28" />
          <span>先从搜索结果里选一首歌，再在这里写留言。</span>
        </div>
      </section>

      <section class="panel-card my-requests-card">
        <div class="section-head">
          <div>
            <div class="section-kicker">My Requests</div>
            <h2>我的点歌</h2>
          </div>
          <button v-if="userStore.isLoggedIn" class="icon-btn" type="button" @click="fetchMyRequests({ reset: true })" title="刷新我的点歌" aria-label="刷新我的点歌">
            <RefreshCw :size="17" />
          </button>
        </div>

        <div v-if="!userStore.isLoggedIn" class="empty-state">
          <Clock :size="28" />
          <span>登录后可以查看自己的点歌状态，也能在播出前取消。</span>
        </div>

        <div v-else-if="myRequests.length" class="my-requests">
          <article class="my-request-item" v-for="item in myRequests" :key="item.id">
            <div class="my-request-copy">
              <div class="my-request-title">{{ item.songName || '未命名歌曲' }}</div>
              <div class="my-request-meta">
                {{ item.artists || '未知歌手' }} · {{ requestStatusLabel(item.status) }}
              </div>
              <div v-if="item.message" class="my-request-message">“{{ item.message }}”</div>
            </div>
            <button
              v-if="item.status === 0"
              class="danger-btn"
              type="button"
              :disabled="cancellingId === item.id"
              @click="cancelMyRequest(item.id)"
            >
              <LoaderCircle v-if="cancellingId === item.id" :size="17" class="spin" />
              <Trash2 v-else :size="17" />
              <span>{{ cancellingId === item.id ? '取消中' : '取消' }}</span>
            </button>
          </article>
          <button v-if="canLoadMoreRequests" class="more-btn" type="button" @click="loadMoreRequests">
            加载更多
          </button>
        </div>

        <div v-else class="empty-state">
          <Clock :size="28" />
          <span>你还没有留下新的点歌。</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChevronLeft,
  Clock,
  ListPlus,
  LoaderCircle,
  MessageSquareText,
  Music,
  Plus,
  Radio,
  RefreshCw,
  Search,
  Send,
  Trash2,
  X,
} from '@lucide/vue'
import { usePlayerStore } from '../stores/player'
import { useUserStore } from '../stores/user'
import { useRequestFeedStore } from '../stores/request-feed'
import { usePlaylistsStore } from '../stores/playlists'
import { useNoticeStore } from '../stores/notice'
import { searchSongs, submitSongRequest, getMyRequests, cancelRequest, proxyCoverUrl } from '../api'

const route = useRoute()
const playerStore = usePlayerStore()
const userStore = useUserStore()
const requestFeed = useRequestFeedStore()
const playlists = usePlaylistsStore()
const notice = useNoticeStore()

const keyword = ref('')
const results = ref([])
const pickedSong = ref(null)
const message = ref('')
const submitting = ref(false)
const resultMsg = ref('')
const resultOk = ref(true)
const searching = ref(false)
const searchError = ref('')
const isComposing = ref(false)
const myRequests = ref([])
const cancellingId = ref(null)
const requestPage = ref(1)
const requestPageSize = ref(10)
const hasMoreRequests = ref(false)
const canLoadMoreRequests = computed(() => hasMoreRequests.value)
const routeChannelId = computed(() => {
  const raw = Array.isArray(route.query.channelId) ? route.query.channelId[0] : route.query.channelId
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
})
const roomId = computed(() => {
  const raw = Array.isArray(route.query.room) ? route.query.room[0] : route.query.room
  return typeof raw === 'string' ? raw : ''
})
const activeChannelId = computed(() => routeChannelId.value || playerStore.currentChannelId || 32953014)

onMounted(() => {
  if (routeChannelId.value) {
    playerStore.setCurrentChannelId(routeChannelId.value)
  }
  fetchMyRequests()
})

function onCompStart() { isComposing.value = true }
function onCompEnd() { isComposing.value = false }

function onEnter() {
  if (!isComposing.value) search()
}

async function search() {
  if (!keyword.value.trim()) return
  searchError.value = ''
  searching.value = true
  try {
    const res = await searchSongs(keyword.value.trim(), 15)
    results.value = res.data || []
    if (results.value.length === 0) searchError.value = '未找到相关歌曲'
  } catch {
    results.value = []
    searchError.value = '搜索失败，请检查网络连接'
  } finally {
    searching.value = false
  }
}

function pick(song) {
  if (!canRequestSong(song)) {
    notice.show({
      type: 'info',
      title: 'Requests',
      message: '外部音乐源可以试听或加入歌单；房间点歌暂时只支持网易云来源。',
      duration: 2600,
    })
    return
  }
  pickedSong.value = song
  resultMsg.value = ''
}

async function submitRequest() {
  if (!pickedSong.value || !canRequestSong(pickedSong.value)) return
  const channelId = activeChannelId.value
  const tempRequestId = `temp-${Date.now()}`
  const optimisticRequest = {
    id: tempRequestId,
    channelId,
    songId: pickedSong.value.id,
    songName: pickedSong.value.name,
    artists: (pickedSong.value.artists || []).join(' / '),
    message: message.value.trim(),
    status: 0,
    createdAt: new Date().toISOString(),
  }
  const optimisticQueueItem = {
    requestId: tempRequestId,
    channelId,
    type: 'song',
    songId: pickedSong.value.id,
    name: pickedSong.value.name,
    artists: pickedSong.value.artists || [],
    coverUrl: pickedSong.value.coverUrl,
    requester: userStore.profile?.nickname || userStore.profile?.username || 'Rabbit',
    message: optimisticRequest.message,
  }

  if (userStore.isLoggedIn) {
    myRequests.value = [optimisticRequest, ...myRequests.value]
  }
  requestFeed.addRequest(channelId, optimisticQueueItem)
  submitting.value = true
  resultMsg.value = ''
  try {
    const res = await submitSongRequest({
      channelId,
      songId: pickedSong.value.id,
      songName: pickedSong.value.name,
      artists: (pickedSong.value.artists || []).join(' / '),
      message: message.value,
    })
    const data = res.data
    if (data?.code && data.code !== 0) {
      throw new Error(data.msg || '点歌失败')
    }
    if (data.djItem) {
      playerStore.insertAt(data.djItem, playerStore.currentIndex + 3)
    }
    if (data.songItem) {
      playerStore.insertAt(data.songItem, playerStore.currentIndex + 4)
    }
    if (userStore.isLoggedIn) {
      myRequests.value = myRequests.value.map((item) =>
        item.id === tempRequestId
          ? {
              ...item,
              id: data.id,
              createdAt: item.createdAt,
            }
          : item
      )
    }
    if (data.songItem) {
      requestFeed.replaceRequest(channelId, tempRequestId, data.songItem)
    } else {
      requestFeed.removeRequest(channelId, tempRequestId)
    }
    resultMsg.value = '点歌成功，队列已经同步更新。'
    resultOk.value = true
    pickedSong.value = null
    message.value = ''
    results.value = []
    keyword.value = ''
    fetchMyRequests({ reset: true, silent: true })
  } catch (e) {
    if (userStore.isLoggedIn) {
      myRequests.value = myRequests.value.filter((item) => item.id !== tempRequestId)
    }
    requestFeed.removeRequest(channelId, tempRequestId)
    resultMsg.value = e.response?.data?.msg || e.message || '点歌失败'
    resultOk.value = false
  } finally {
    submitting.value = false
  }
}

function saveSong(song) {
  const added = playlists.addTrackToFirst(song)
  notice.show({
    type: added ? 'success' : 'info',
    title: 'Playlist',
    message: added ? '已加入我的歌单。' : '这首歌已经在歌单里了。',
    duration: 1800,
  })
}

function canRequestSong(song) {
  return !song?.source || song.source === 'netease'
}

function sourceLabel(song) {
  const source = song?.source || 'netease'
  if (source === 'netease') return ''
  return song?.sourceLabel || source
}

async function fetchMyRequests(options = {}) {
  const { reset = true, silent = false } = options
  if (!userStore.isLoggedIn) {
    myRequests.value = []
    hasMoreRequests.value = false
    requestPage.value = 1
    return
  }
  try {
    const page = reset ? 1 : requestPage.value + 1
    const res = await getMyRequests({ page, size: requestPageSize.value })
    const payload = res.data || {}
    const items = payload.items || []
    myRequests.value = reset ? items : [...myRequests.value, ...items]
    hasMoreRequests.value = !!payload.hasMore
    requestPage.value = payload.page || page
  } catch {
    if (!silent) {
      myRequests.value = []
      hasMoreRequests.value = false
      requestPage.value = 1
    }
  }
}

async function cancelMyRequest(id) {
  const existing = myRequests.value.find((item) => item.id === id)
  const previousStatus = existing?.status
  const channelId = existing?.channelId || activeChannelId.value
  const previousQueueItem = requestFeed.getQueue(channelId).find((item) => item.requestId === id)
  if (existing) {
    myRequests.value = myRequests.value.map((item) =>
      item.id === id
        ? { ...item, status: 2 }
        : item
    )
  }
  requestFeed.removeRequest(channelId, id)
  cancellingId.value = id
  resultMsg.value = ''
  try {
    await cancelRequest(id)
    resultMsg.value = '点歌已取消，队列会同步更新。'
    resultOk.value = true
    fetchMyRequests({ reset: true, silent: true })
  } catch (e) {
    if (existing) {
      myRequests.value = myRequests.value.map((item) =>
        item.id === id
          ? { ...item, status: previousStatus }
          : item
      )
    }
    if (previousQueueItem) {
      requestFeed.addRequest(channelId, previousQueueItem)
    }
    fetchMyRequests({ reset: true, silent: true })
    resultMsg.value = e.response?.data?.msg || '取消点歌失败'
    resultOk.value = false
  } finally {
    cancellingId.value = null
  }
}

function loadMoreRequests() {
  if (!hasMoreRequests.value) return
  fetchMyRequests({ reset: false })
}

function requestStatusLabel(status) {
  if (status === 1) return '已播出'
  if (status === 2) return '已取消'
  return '待播中'
}
</script>

<style scoped>
.request-view {
  min-height: 100vh;
  padding: 24px;
  color: var(--text-primary);
  overflow-y: auto;
}

.page-head,
.request-search-card,
.request-layout {
  width: min(1180px, 100%);
  margin-inline: auto;
}

.page-head {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;
}

.back-btn,
.icon-btn,
.ghost-btn,
.primary-btn,
.danger-btn,
.more-btn {
  min-height: var(--control-height);
  border-radius: var(--radius-md);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-weight: 800;
}

.back-btn,
.icon-btn,
.ghost-btn,
.more-btn {
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  color: var(--text-secondary);
}

.back-btn {
  padding: 0 14px;
  flex-shrink: 0;
}

.back-btn:hover,
.icon-btn:hover,
.ghost-btn:hover,
.more-btn:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
  background: var(--bg-card-hover);
}

.page-title-block {
  min-width: 0;
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

.page-title-block h1 {
  margin-top: 8px;
  font-size: clamp(2rem, 4vw, 4rem);
  line-height: 1;
}

.page-title-block p {
  margin-top: 10px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.room-chip {
  width: fit-content;
  max-width: 100%;
  min-height: 30px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  margin-top: 10px;
  padding: 0 10px;
  border: 1px solid color-mix(in srgb, var(--blue) 28%, var(--divider));
  border-radius: var(--radius-pill);
  color: var(--blue);
  background: color-mix(in srgb, var(--blue) 10%, transparent);
  font-size: 0.78rem;
  font-weight: 800;
}

.request-search-card {
  padding: 14px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  box-shadow: var(--shadow-soft);
}

.search-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 52px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-lg);
  background: var(--input-bg);
  padding: 0 10px 0 14px;
  color: var(--text-tertiary);
}

.search-wrap:focus-within {
  border-color: color-mix(in srgb, var(--accent) 46%, var(--input-border));
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--accent) 10%, transparent);
}

.search-wrap input {
  flex: 1;
  min-width: 0;
  height: 48px;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
}

.search-wrap input::placeholder,
textarea::placeholder {
  color: var(--text-tertiary);
}

.primary-btn {
  border: none;
  padding: 0 16px;
  background: var(--accent);
  color: #07100c;
}

.primary-btn:disabled,
.danger-btn:disabled {
  opacity: 0.62;
}

.inline-error,
.result-msg.err {
  color: var(--highlight);
}

.inline-error {
  margin-top: 10px;
  font-size: 0.86rem;
}

.request-layout {
  margin-top: 18px;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(320px, 0.9fr);
  gap: 16px;
}

.panel-card {
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-card) 84%, transparent);
  box-shadow: var(--shadow-soft);
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head h2 {
  margin-top: 4px;
  font-size: 1.28rem;
}

.count-badge {
  min-width: 32px;
  min-height: 28px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-pill);
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  font-size: 0.78rem;
  font-weight: 850;
}

.results,
.my-requests {
  display: grid;
  gap: 9px;
}

.track-row {
  width: 100%;
  min-height: 62px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 10px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
  color: inherit;
  text-align: left;
}

.track-row:hover {
  background: var(--bg-card-hover);
  border-color: color-mix(in srgb, var(--accent) 26%, var(--divider));
}

.track-cover {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}

.track-cover.fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.track-copy {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 3px;
}

.track-title-row,
.track-meta {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.track-title-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.track-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-primary);
  font-weight: 850;
}

.source-badge {
  min-height: 20px;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  padding: 0 7px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--blue) 12%, transparent);
  color: var(--blue);
  font-size: 0.66rem;
  font-weight: 850;
}

.track-meta {
  color: var(--text-secondary);
  font-size: 0.78rem;
}

.pick-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: 10px;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  cursor: pointer;
}

.pick-icon.secondary {
  color: var(--blue);
  background: color-mix(in srgb, var(--blue) 10%, transparent);
}

.pick-icon:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}

.draft {
  display: grid;
  gap: 12px;
}

.picked-info {
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--divider));
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--accent) 8%, transparent);
}

.picked-label {
  color: var(--accent);
  font-size: 0.72rem;
  font-weight: 850;
  text-transform: uppercase;
}

.picked-title {
  margin-top: 6px;
  color: var(--text-primary);
  font-weight: 850;
}

.picked-meta {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.82rem;
}

textarea {
  width: 100%;
  min-height: 138px;
  resize: vertical;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-lg);
  background: var(--input-bg);
  color: var(--text-primary);
  padding: 14px;
  outline: none;
  line-height: 1.6;
}

textarea:focus {
  border-color: color-mix(in srgb, var(--accent) 46%, var(--input-border));
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--accent) 10%, transparent);
}

.draft-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

.ghost-btn {
  padding: 0 14px;
}

.result-msg {
  font-size: 0.86rem;
  line-height: 1.6;
}

.result-msg.ok {
  color: var(--success);
}

.my-requests-card {
  grid-column: 1 / -1;
}

.my-request-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 13px 14px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
}

.my-request-copy {
  min-width: 0;
  flex: 1;
}

.my-request-title,
.my-request-meta,
.my-request-message {
  overflow-wrap: anywhere;
}

.my-request-title {
  color: var(--text-primary);
  font-weight: 850;
}

.my-request-meta,
.my-request-message {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.danger-btn {
  border: 1px solid color-mix(in srgb, var(--coral) 28%, var(--divider));
  padding: 0 12px;
  color: var(--coral);
  background: color-mix(in srgb, var(--coral) 9%, transparent);
}

.more-btn {
  justify-self: center;
  padding: 0 16px;
}

.empty-state {
  min-height: 180px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--text-tertiary);
  text-align: center;
  line-height: 1.7;
}

.spin {
  animation: spin 850ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 900px) {
  .request-view {
    padding: 16px;
  }

  .page-head {
    flex-direction: column;
  }

  .request-layout {
    grid-template-columns: 1fr;
  }

  .my-requests-card {
    grid-column: auto;
  }

  .search-wrap {
    flex-wrap: wrap;
    padding: 10px;
  }

  .search-wrap input,
  .primary-btn {
    min-width: 100%;
  }
}

@media (max-width: 560px) {
  .my-request-item {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
