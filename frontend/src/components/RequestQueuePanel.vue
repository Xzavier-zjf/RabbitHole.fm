<template>
  <aside class="queue-panel">
    <section class="now-card">
      <div class="panel-head">
        <div>
          <div class="panel-kicker">Now Playing</div>
          <h3 class="panel-title">当前播放</h3>
        </div>
        <Volume2 :size="18" class="panel-live" />
      </div>

      <div v-if="player.currentItem" class="now-track">
        <img
          v-if="player.currentItem.coverUrl"
          :src="proxyCoverUrl(player.currentItem.coverUrl)"
          class="now-cover"
          referrerpolicy="no-referrer"
        />
        <div v-else class="now-cover fallback">
          <Music :size="24" />
        </div>
        <div class="now-copy">
          <div class="now-name">{{ player.currentItem.name || '未知曲目' }}</div>
          <div class="now-artist">{{ (player.currentItem.artists || []).join(' / ') || currentTypeLabel }}</div>
          <div v-if="player.currentItem.requester" class="now-requester">
            {{ player.currentItem.requester }} 的点歌
          </div>
        </div>
      </div>

      <div v-else class="now-empty">
        <Radio :size="22" />
        <span>等待频道开始播放</span>
      </div>
    </section>

    <section class="queue-card">
      <div class="panel-head">
        <div>
          <div class="panel-kicker">Requests</div>
          <h3 class="panel-title">点歌队列</h3>
        </div>
        <button class="refresh-btn" type="button" @click="fetchQueue" :disabled="loading" aria-label="刷新队列" title="刷新队列">
          <RefreshCw :size="17" :class="{ spinning: loading }" />
        </button>
      </div>

      <p class="panel-copy">听众留下的歌曲和留言会出现在这里。</p>
      <div v-if="list.length" class="queue-summary">
        <span>{{ list.length }} 首待播</span>
        <span>预计 {{ estimateTotalWait(list.length) }} 分钟</span>
      </div>

      <div v-if="list.length" class="queue-list">
        <article
          v-for="(item, index) in list"
          :key="`${item.requestId || 'no-id'}-${item.type}-${item.songId || item.djUrl || index}`"
          class="queue-item"
          :class="{ mine: isMine(item), dj: item.type === 'dj' }"
        >
          <img
            v-if="item.coverUrl"
            :src="proxyCoverUrl(item.coverUrl)"
            class="queue-cover"
            referrerpolicy="no-referrer"
          />
          <div v-else class="queue-cover fallback">
            <Mic2 v-if="item.type === 'dj'" :size="18" />
            <Music v-else :size="18" />
          </div>

          <div class="queue-body">
            <div class="queue-title-row">
              <span class="queue-position">#{{ index + 1 }}</span>
              <div class="queue-title">{{ queueTitle(item) }}</div>
              <span v-if="sourceLabel(item)" class="source-badge">{{ sourceLabel(item) }}</span>
            </div>
            <div class="queue-meta">
              <span class="queue-requester">
                <UserRound :size="13" />
                {{ requesterLabel(item) }}
              </span>
              <span v-if="item.artists?.length" class="queue-artist">{{ item.artists.join(' / ') }}</span>
              <span class="queue-wait">约 {{ estimateWait(index) }} 分钟后</span>
            </div>
            <div v-if="queueMessage(item)" class="queue-message">“{{ queueMessage(item) }}”</div>
          </div>

          <div class="queue-actions" aria-label="队列排序操作">
            <button
              class="queue-order-btn"
              type="button"
              :disabled="index === 0"
              @click="promoteQueueItem(item)"
              aria-label="置顶播放"
              title="置顶播放"
            >
              <ChevronsUp :size="15" />
            </button>
            <button
              class="queue-order-btn"
              type="button"
              :disabled="index === list.length - 1"
              @click="delayQueueItem(item)"
              aria-label="稍后播放"
              title="稍后播放"
            >
              <Clock3 :size="15" />
            </button>
          </div>

          <button
            v-if="isMine(item) && item.requestId"
            class="queue-cancel"
            type="button"
            :disabled="cancellingId === item.requestId"
            @click="cancelQueueItem(item)"
            aria-label="撤回点歌"
            title="撤回点歌"
          >
            <LoaderCircle v-if="cancellingId === item.requestId" :size="16" class="spinning" />
            <X v-else :size="16" />
          </button>
        </article>
      </div>

      <div v-else class="empty">
        <ListMusic :size="24" />
        <span>还没有新的点歌留言。</span>
      </div>
    </section>
  </aside>
</template>

<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import {
  ChevronsUp,
  Clock3,
  ListMusic,
  LoaderCircle,
  Mic2,
  Music,
  Radio,
  RefreshCw,
  UserRound,
  Volume2,
  X,
} from '@lucide/vue'
import { useUserStore } from '../stores/user'
import { usePlayerStore } from '../stores/player'
import { useRequestFeedStore } from '../stores/request-feed'
import { cancelRequest, getRequestQueue, proxyCoverUrl } from '../api'
import { useNoticeStore } from '../stores/notice'
import { musicSourceLabel } from '../utils/music-source'

const props = defineProps({
  channelId: {
    type: Number,
    default: 32953014,
  },
})

const userStore = useUserStore()
const player = usePlayerStore()
const requestFeed = useRequestFeedStore()
const notice = useNoticeStore()
const loading = ref(false)
const cancellingId = ref(null)
const list = computed(() => requestFeed.getQueue(props.channelId))
const currentTypeLabel = computed(() => player.currentItem?.type === 'dj' ? '点歌口播' : 'RabbitHole.fm')
const QUEUE_PANEL_QUERY = '(max-width: 1200px)'
const isPanelVisible = ref(
  typeof window === 'undefined' ? true : !window.matchMedia(QUEUE_PANEL_QUERY).matches
)
let panelMediaQuery = null
let pollTimer = null

watch(
  () => props.channelId,
  () => {
    if (isPanelVisible.value) fetchQueue()
  },
  { immediate: true }
)

onMounted(() => {
  panelMediaQuery = window.matchMedia(QUEUE_PANEL_QUERY)
  syncPanelVisibility(panelMediaQuery)
  panelMediaQuery.addEventListener('change', syncPanelVisibility)
  if (isPanelVisible.value) startPolling()
})

onBeforeUnmount(() => {
  stopPolling()
  panelMediaQuery?.removeEventListener('change', syncPanelVisibility)
})

async function fetchQueue() {
  if (!props.channelId || !isPanelVisible.value) return
  loading.value = true
  try {
    const res = await getRequestQueue(props.channelId)
    requestFeed.setQueue(props.channelId, res.data || [])
  } catch {
    if (!list.value.length) {
      requestFeed.setQueue(props.channelId, [])
    }
  } finally {
    loading.value = false
  }
}

function syncPanelVisibility(e) {
  const wasVisible = isPanelVisible.value
  isPanelVisible.value = !e.matches
  if (isPanelVisible.value) {
    if (!wasVisible) fetchQueue()
    startPolling()
  } else {
    stopPolling()
    loading.value = false
  }
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(fetchQueue, 10000)
}

function stopPolling() {
  if (!pollTimer) return
  clearInterval(pollTimer)
  pollTimer = null
}

function isMine(item) {
  const nickname = userStore.profile?.nickname
  const username = userStore.profile?.username
  return !!item.requester && [nickname, username].filter(Boolean).includes(item.requester)
}

function requesterLabel(item) {
  if (item.requester) return item.requester
  return item.type === 'dj' ? '点歌增强' : 'RabbitHole.fm'
}

function queueTitle(item) {
  if (item.type === 'dj') return item.name || '点歌口播（可跳过）'
  return item.name || '未命名歌曲'
}

function sourceLabel(item) {
  if (item.type === 'dj') return ''
  return musicSourceLabel(item)
}

function queueMessage(item) {
  return item.djSubtitle || item.message || ''
}

function estimateWait(index) {
  return Math.max(1, (index + 1) * 4)
}

function estimateTotalWait(count) {
  return Math.max(1, count * 4)
}

function promoteQueueItem(item) {
  const moved = requestFeed.promoteRequest(props.channelId, item)
  if (!moved) return
  notice.show({
    type: 'success',
    title: 'Requests',
    message: '已将这首歌置顶到待播队列。',
    duration: 1800,
  })
}

function delayQueueItem(item) {
  const moved = requestFeed.delayRequest(props.channelId, item)
  if (!moved) return
  notice.show({
    type: 'info',
    title: 'Requests',
    message: '已将这首歌移到队列稍后播放。',
    duration: 1800,
  })
}

async function cancelQueueItem(item) {
  if (!item?.requestId) return
  cancellingId.value = item.requestId
  const previousItem = item
  requestFeed.removeRequest(props.channelId, item.requestId)
  try {
    await cancelRequest(item.requestId)
    notice.show({
      type: 'success',
      title: 'Requests',
      message: '已撤回你的点歌。',
      duration: 1800,
    })
  } catch (e) {
    requestFeed.addRequest(props.channelId, previousItem)
    notice.show({
      type: 'error',
      title: 'Requests',
      message: e.response?.data?.msg || '撤回点歌失败。',
      duration: 2400,
    })
  } finally {
    cancellingId.value = null
    fetchQueue()
  }
}
</script>

<style scoped>
.queue-panel {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.now-card,
.queue-card {
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  box-shadow: var(--shadow-soft);
}

.now-card {
  padding: 16px;
}

.queue-card {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.panel-kicker {
  color: var(--accent);
  font-size: 0.72rem;
  font-weight: 800;
  text-transform: uppercase;
}

.panel-title {
  margin-top: 3px;
  color: var(--text-primary);
  font-size: 1.05rem;
  font-weight: 850;
}

.panel-live {
  margin-top: 4px;
  color: var(--accent);
}

.now-track {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-top: 14px;
}

.now-cover {
  width: 68px;
  height: 68px;
  border-radius: 14px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 14px 30px color-mix(in srgb, var(--bg-primary) 24%, transparent);
}

.now-cover.fallback,
.queue-cover.fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-card));
}

.now-copy {
  min-width: 0;
}

.now-name,
.now-artist,
.now-requester {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.now-name {
  color: var(--text-primary);
  font-size: 0.96rem;
  font-weight: 850;
}

.now-artist {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.now-requester {
  width: fit-content;
  max-width: 100%;
  margin-top: 8px;
  padding: 4px 8px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--blue) 12%, transparent);
  color: var(--blue);
  font-size: 0.72rem;
  font-weight: 800;
}

.now-empty,
.empty {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-tertiary);
}

.now-empty {
  margin-top: 14px;
  min-height: 68px;
}

.refresh-btn {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: var(--bg-card);
  color: var(--text-secondary);
  cursor: pointer;
}

.refresh-btn:hover:not(:disabled) {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
}

.refresh-btn:disabled {
  opacity: 0.65;
}

.spinning {
  animation: spin 800ms linear infinite;
}

.panel-copy {
  color: var(--text-secondary);
  font-size: 0.82rem;
  line-height: 1.7;
}

.queue-summary {
  min-height: 32px;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  color: var(--text-tertiary);
  font-size: 0.74rem;
  font-weight: 800;
}

.queue-summary span {
  min-height: 24px;
  display: inline-flex;
  align-items: center;
  padding: 0 9px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--text-primary) 7%, transparent);
}

.queue-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: grid;
  align-content: start;
  gap: 9px;
  padding-right: 2px;
}

.queue-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-elevated) 62%, transparent);
}

.queue-item.mine {
  border-color: color-mix(in srgb, var(--coral) 36%, var(--divider));
  box-shadow: inset 3px 0 0 var(--coral);
}

.queue-item.dj {
  background: color-mix(in srgb, var(--blue) 9%, var(--bg-card));
}

.queue-cover {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}

.queue-body {
  min-width: 0;
  flex: 1;
}

.queue-title-row {
  display: flex;
  align-items: flex-start;
  gap: 7px;
}

.queue-position {
  min-width: 30px;
  min-height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: var(--radius-pill);
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  font-size: 0.7rem;
  font-weight: 900;
}

.queue-title {
  min-width: 0;
  flex: 1;
  color: var(--text-primary);
  font-size: 0.86rem;
  font-weight: 800;
  line-height: 1.42;
  overflow-wrap: anywhere;
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
  font-size: 0.64rem;
  font-weight: 850;
}

.queue-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 5px;
  font-size: 0.72rem;
}

.queue-requester {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--blue);
}

.queue-artist {
  min-width: 0;
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}

.queue-wait {
  color: var(--text-tertiary);
}

.queue-message {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 0.76rem;
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.queue-actions {
  display: grid;
  gap: 6px;
  flex-shrink: 0;
}

.queue-order-btn {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--bg-card) 78%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
}

.queue-order-btn:hover:not(:disabled) {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 30%, var(--divider));
  background: color-mix(in srgb, var(--accent) 9%, transparent);
}

.queue-order-btn:disabled {
  opacity: 0.44;
}

.queue-cancel {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid color-mix(in srgb, var(--coral) 26%, var(--divider));
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--coral) 8%, transparent);
  color: var(--coral);
  cursor: pointer;
}

.queue-cancel:disabled {
  opacity: 0.62;
}

.empty {
  flex: 1;
  justify-content: center;
  text-align: center;
  flex-direction: column;
  line-height: 1.6;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1200px) {
  .queue-panel {
    display: none;
  }
}
</style>
