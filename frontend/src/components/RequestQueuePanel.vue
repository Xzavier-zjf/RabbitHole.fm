<template>
  <aside class="queue-panel">
    <div class="panel-head">
      <div>
        <div class="panel-kicker">Other Rabbits</div>
        <h3 class="panel-title">点歌队列</h3>
      </div>
      <button class="refresh-btn" @click="fetchQueue" :disabled="loading" title="刷新队列">
        {{ loading ? '...' : '↻' }}
      </button>
    </div>

    <div class="panel-copy">其他听众也在这口洞里留下了歌和话。</div>

    <div v-if="list.length" class="queue-list">
      <div
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
        <div v-else class="queue-cover fallback">{{ item.type === 'dj' ? '🎙' : '♪' }}</div>

        <div class="queue-body">
          <div class="queue-title">{{ queueTitle(item) }}</div>
          <div class="queue-meta">
            <span class="queue-requester">{{ requesterLabel(item) }}</span>
            <span v-if="item.artists?.length" class="queue-artist">{{ item.artists.join(' / ') }}</span>
          </div>
          <div v-if="queueMessage(item)" class="queue-message">“{{ queueMessage(item) }}”</div>
        </div>
      </div>
    </div>

    <div v-else class="empty">
      还没有新的点歌留言，先挑一首歌把兔子们叫进来。
    </div>
  </aside>
</template>

<script setup>
import { computed, ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { useUserStore } from '../stores/user'
import { useRequestFeedStore } from '../stores/request-feed'
import { getRequestQueue, proxyCoverUrl } from '../api'

const props = defineProps({
  channelId: {
    type: Number,
    default: 32953014,
  },
})

const userStore = useUserStore()
const requestFeed = useRequestFeedStore()
const loading = ref(false)
const list = computed(() => requestFeed.getQueue(props.channelId))
let pollTimer = null

watch(
  () => props.channelId,
  () => {
    fetchQueue()
  },
  { immediate: true }
)

onMounted(() => {
  pollTimer = setInterval(fetchQueue, 10000)
})

onBeforeUnmount(() => {
  if (pollTimer) clearInterval(pollTimer)
})

async function fetchQueue() {
  if (!props.channelId) return
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

function isMine(item) {
  const nickname = userStore.profile?.nickname
  const username = userStore.profile?.username
  return !!item.requester && [nickname, username].filter(Boolean).includes(item.requester)
}

function requesterLabel(item) {
  if (item.requester) return `🐰 ${item.requester}`
  return item.type === 'dj' ? 'DJ 小糖' : 'RabbitHole.fm'
}

function queueTitle(item) {
  if (item.type === 'dj') return item.name || 'DJ 口播'
  return item.name || '未命名歌曲'
}

function queueMessage(item) {
  return item.message || item.djSubtitle || ''
}
</script>

<style scoped>
.queue-panel {
  width: 360px;
  flex-shrink: 0;
  min-height: 0;
  padding: 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  box-shadow: var(--shadow-soft);
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
  color: var(--mystic);
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.panel-title {
  font-family: var(--font-display);
  font-size: 1.45rem;
  color: var(--text-primary);
}

.refresh-btn {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 92%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
}

.panel-copy {
  color: var(--text-secondary);
  font-size: 0.84rem;
  line-height: 1.7;
}

.queue-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  display: grid;
  gap: 10px;
  padding-right: 2px;
}

.queue-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px;
  border-radius: var(--radius-md);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 82%, transparent);
}

.queue-item.mine {
  border-left: 3px solid var(--highlight);
}

.queue-item.dj {
  background: color-mix(in srgb, var(--mystic) 9%, var(--bg-elevated));
}

.queue-cover {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  object-fit: cover;
  flex-shrink: 0;
}

.queue-cover.fallback {
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
  font-size: 1.1rem;
}

.queue-body {
  min-width: 0;
  flex: 1;
}

.queue-title {
  color: var(--text-primary);
  font-size: 0.9rem;
  font-weight: 600;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: anywhere;
}

.queue-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 5px;
  font-size: 0.74rem;
}

.queue-requester {
  color: var(--mystic);
}

.queue-artist {
  color: var(--text-secondary);
}

.queue-message {
  color: var(--text-secondary);
  font-size: 0.78rem;
  line-height: 1.6;
  margin-top: 6px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.empty {
  color: var(--text-tertiary);
  font-size: 0.88rem;
  line-height: 1.8;
  padding-top: 12px;
}

@media (max-width: 1200px) {
  .queue-panel {
    display: none;
  }
}

@media (max-width: 1360px) {
  .queue-panel {
    width: 320px;
  }
}
</style>
