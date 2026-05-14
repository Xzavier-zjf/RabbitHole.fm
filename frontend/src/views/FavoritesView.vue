<template>
  <div class="page-view">
    <div class="header">
      <button class="back-btn" @click="backToRadio">← 返回兔子洞</button>
      <div>
        <h2>我的收藏</h2>
        <p>那些你愿意再次掉进去的歌。</p>
      </div>
    </div>

    <div class="loading-state" v-if="loading">加载中...</div>
    <div class="list" v-else-if="list.length">
      <div class="item" v-for="f in list" :key="f.id">
        <img class="item-cover" :src="proxyCoverUrl(f.coverUrl)" v-if="f.coverUrl" referrerpolicy="no-referrer" />
        <div class="item-info">
          <div class="item-name">{{ f.songName }}</div>
          <div class="item-artists">{{ f.artists }}</div>
        </div>
        <button class="remove-btn" @click="remove(f)">取消收藏</button>
      </div>
    </div>
    <div class="empty" v-else-if="!loading">暂无收藏</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getFavorites, removeFavorite, proxyCoverUrl } from '../api'
import { usePlayerStore } from '../stores/player'

const list = ref([])
const loading = ref(false)
const router = useRouter()
const playerStore = usePlayerStore()

onMounted(fetch)

async function fetch() {
  loading.value = true
  try {
    const res = await getFavorites()
    list.value = res.data
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function remove(f) {
  try {
    await removeFavorite(f.songId)
    list.value = list.value.filter((x) => x.id !== f.id)
  } catch { /* ignore */ }
}

function backToRadio() {
  const viewState = playerStore.getPlaybackViewState()
  playerStore.setPlaybackViewState({ showLyrics: !!viewState.showLyrics })
  router.push({ path: '/', query: { from: 'favorites' } })
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
}

.header p {
  color: var(--text-secondary);
  margin-top: 4px;
}

.back-btn,
.remove-btn {
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  border-radius: 999px;
  cursor: pointer;
}

.back-btn {
  padding: 10px 16px;
  color: var(--text-secondary);
}

.list {
  max-width: 760px;
  display: grid;
  gap: 10px;
}

.item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
}

.item-cover {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  object-fit: cover;
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

.remove-btn {
  margin-left: auto;
  color: var(--highlight);
  padding: 8px 12px;
}

.empty,
.loading-state {
  color: var(--text-tertiary);
  text-align: center;
  padding: 60px 0;
}
</style>
