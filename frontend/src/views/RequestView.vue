<template>
  <div class="request-view">
    <div class="hero">
      <div class="header">
        <button class="back-btn" @click="$router.push('/')">← 返回兔子洞</button>
        <div>
          <h2>点歌留言</h2>
          <p class="desc">把你想听的歌和一句话交给 DJ，小糖会替你说出来。</p>
        </div>
      </div>

      <div class="search-wrap">
        <input
          v-model="keyword"
          placeholder="搜索歌曲..."
          @keydown.enter.prevent="onEnter"
          @compositionstart="onCompStart"
          @compositionend="onCompEnd"
        />
        <button @click="search" :disabled="searching">{{ searching ? '搜索中...' : '搜索' }}</button>
      </div>

      <p v-if="searchError" class="search-err">{{ searchError }}</p>
    </div>

    <div class="request-layout">
      <section class="results-card">
        <div class="section-kicker">Pick A Song</div>
        <h3>搜索结果</h3>

        <div class="results" v-if="results.length">
          <div class="item" v-for="s in results" :key="s.id">
            <div class="item-info">
              <div class="item-name">{{ s.name }}</div>
              <div class="item-artists">{{ (s.artists || []).join(' / ') }}</div>
            </div>
            <button class="pick-btn" @click="pick(s)">选这首</button>
          </div>
        </div>

        <div class="empty-state" v-else>
          搜索一首歌，或者试试“陈奕迅”“周杰伦”“宇多田光”。
        </div>
      </section>

      <section class="draft-card">
        <div class="section-kicker">Leave A Note</div>
        <h3>给 DJ 的话</h3>

        <div class="draft" v-if="pickedSong">
          <div class="picked-info">
            你点的歌：<strong>{{ pickedSong.name }}</strong>
            <span>— {{ (pickedSong.artists || []).join(' / ') }}</span>
          </div>
          <textarea
            v-model="message"
            placeholder="想对谁说什么？DJ 会帮你念出来（可选，最多 80 字）"
            maxlength="80"
          />
          <button class="submit-btn" @click="submitRequest" :disabled="submitting">
            {{ submitting ? '提交中...' : '提交点歌' }}
          </button>
          <p v-if="resultMsg" :class="{ ok: resultOk, err: !resultOk }">{{ resultMsg }}</p>
        </div>

        <div class="empty-state" v-else>
          先从左边选一首歌，再在这里写下你的留言。
        </div>
      </section>

      <section class="draft-card my-requests-card">
        <div class="section-kicker">My Requests</div>
        <h3>我的点歌</h3>

        <div v-if="!userStore.isLoggedIn" class="empty-state">
          登录后可以查看自己的点歌状态，也能在播出前取消。
        </div>

        <div v-else-if="myRequests.length" class="my-requests">
          <div class="my-request-item" v-for="item in myRequests" :key="item.id">
            <div class="my-request-copy">
              <div class="my-request-title">{{ item.songName || '未命名歌曲' }}</div>
              <div class="my-request-meta">{{ item.artists || '未知歌手' }} · {{ requestStatusLabel(item.status) }}</div>
              <div v-if="item.message" class="my-request-message">“{{ item.message }}”</div>
            </div>
            <button
              v-if="item.status === 0"
              class="pick-btn"
              :disabled="cancellingId === item.id"
              @click="cancelMyRequest(item.id)"
            >
              {{ cancellingId === item.id ? '取消中...' : '取消点歌' }}
            </button>
          </div>
          <button v-if="canLoadMoreRequests" class="more-btn" @click="loadMoreRequests">加载更多</button>
        </div>

        <div v-else class="empty-state">
          你还没有留下新的点歌。
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { usePlayerStore } from '../stores/player'
import { useUserStore } from '../stores/user'
import { useRequestFeedStore } from '../stores/request-feed'
import { searchSongs, submitSongRequest, getMyRequests, cancelRequest } from '../api'

const playerStore = usePlayerStore()
const userStore = useUserStore()
const requestFeed = useRequestFeedStore()

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

onMounted(fetchMyRequests)

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
  pickedSong.value = song
  resultMsg.value = ''
}

async function submitRequest() {
  if (!pickedSong.value) return
  const channelId = playerStore.currentChannelId || 32953014
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
    resultMsg.value = '点歌成功，右侧 Other Rabbits 队列已经能看到你的留言。'
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
    resultMsg.value = e.response?.data?.msg || '点歌失败'
    resultOk.value = false
  } finally {
    submitting.value = false
  }
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
  const channelId = existing?.channelId || playerStore.currentChannelId || 32953014
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
  padding: 28px;
  color: var(--text-primary);
}

.hero {
  max-width: 980px;
  margin: 0 auto 22px;
}

.header {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 18px;
}

.back-btn,
.search-wrap button,
.pick-btn,
.submit-btn {
  border: none;
  border-radius: 999px;
  cursor: pointer;
}

.back-btn {
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  color: var(--text-secondary);
  padding: 10px 16px;
}

.desc {
  color: var(--text-secondary);
  margin-top: 6px;
}

.search-wrap {
  display: flex;
  gap: 10px;
}

.search-wrap input,
textarea {
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  color: var(--text-primary);
  outline: none;
}

.search-wrap input {
  flex: 1;
  border-radius: 999px;
  padding: 12px 16px;
}

.search-wrap button,
.submit-btn {
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 60%, var(--mystic)));
  color: #fff7ef;
  padding: 12px 18px;
}

.search-wrap button:disabled,
.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.search-err,
.err {
  color: var(--highlight);
  margin-top: 10px;
}

.request-layout {
  max-width: 980px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1.1fr 0.9fr;
  gap: 18px;
}

.results-card,
.draft-card {
  padding: 20px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  box-shadow: var(--shadow-soft);
}

.section-kicker {
  color: var(--mystic);
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.results-card h3,
.draft-card h3 {
  font-family: var(--font-display);
  font-size: 1.4rem;
  margin-bottom: 14px;
}

.results {
  display: grid;
  gap: 10px;
}

.item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: color-mix(in srgb, var(--bg-elevated) 80%, transparent);
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
}

.item-name {
  font-size: 0.96rem;
  font-weight: 600;
}

.item-artists {
  color: var(--text-secondary);
  font-size: 0.8rem;
  margin-top: 4px;
}

.pick-btn {
  margin-left: auto;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
  padding: 8px 14px;
}

.draft {
  display: grid;
  gap: 12px;
}

.picked-info {
  color: var(--text-secondary);
  line-height: 1.7;
}

.picked-info strong {
  color: var(--text-primary);
}

textarea {
  width: 100%;
  min-height: 120px;
  border-radius: 18px;
  padding: 14px;
  resize: vertical;
}

.ok {
  color: var(--success);
}

.empty-state {
  color: var(--text-tertiary);
  line-height: 1.8;
  padding: 24px 4px;
}

.my-requests-card {
  grid-column: 1 / -1;
}

.my-requests {
  display: grid;
  gap: 10px;
}

.my-request-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 80%, transparent);
}

.my-request-copy {
  min-width: 0;
  flex: 1;
}

.my-request-title {
  color: var(--text-primary);
  font-size: 0.96rem;
  font-weight: 600;
}

.my-request-meta,
.my-request-message {
  color: var(--text-secondary);
  font-size: 0.8rem;
  margin-top: 4px;
}

.more-btn {
  justify-self: center;
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  color: var(--text-secondary);
  border-radius: 999px;
  padding: 10px 16px;
  cursor: pointer;
}

@media (max-width: 900px) {
  .request-layout {
    grid-template-columns: 1fr;
  }

  .header,
  .search-wrap {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
