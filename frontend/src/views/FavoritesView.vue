<template>
  <div class="page-view">
    <header class="page-head">
      <button class="back-btn" type="button" @click="backToRadio">
        <ChevronLeft :size="18" />
        <span>返回播放</span>
      </button>
      <div>
        <div class="page-kicker">
          <Heart :size="16" />
          <span>Library</span>
        </div>
        <h1>我的收藏</h1>
        <p>保存下来，适合再次播放的歌曲。</p>
      </div>
    </header>

    <section class="panel-card">
      <div class="panel-head">
        <div>
          <div class="section-kicker">Saved Tracks</div>
          <h2>收藏歌曲</h2>
        </div>
        <span class="count-badge">{{ list.length }}</span>
      </div>

      <div class="loading-state" v-if="loading">
        <LoaderCircle :size="28" class="spin" />
        <span>加载中...</span>
      </div>

      <div class="list" v-else-if="list.length">
        <article class="track-row" v-for="f in list" :key="f.id">
          <img class="track-cover" :src="proxyCoverUrl(f.coverUrl)" v-if="f.coverUrl" referrerpolicy="no-referrer" />
          <div v-else class="track-cover fallback">
            <Music :size="18" />
          </div>
          <div class="track-info">
            <div class="track-title-row">
              <span class="track-title">{{ f.songName }}</span>
              <span v-if="sourceLabel(f)" class="source-badge">{{ sourceLabel(f) }}</span>
            </div>
            <div class="track-meta">{{ f.artists || '未知歌手' }}</div>
          </div>
          <button class="icon-action" type="button" @click="playFavorite(f)" aria-label="播放歌曲" title="播放歌曲">
            <Play :size="17" fill="currentColor" />
          </button>
          <button class="icon-action" type="button" @click="saveToPlaylist(f)" aria-label="加入歌单" title="加入歌单">
            <ListPlus :size="17" />
          </button>
          <button class="remove-btn" type="button" @click="remove(f)" aria-label="取消收藏" title="取消收藏">
            <Trash2 :size="18" />
            <span>取消收藏</span>
          </button>
        </article>
      </div>

      <div class="empty" v-else-if="!loading">
        <Heart :size="32" />
        <span>暂无收藏</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ChevronLeft, Heart, ListPlus, LoaderCircle, Music, Play, Trash2 } from '@lucide/vue'
import { getFavorites, removeFavorite, proxyCoverUrl } from '../api'
import { musicSource, musicSourceLabel, sourceSongId } from '../utils/music-source'
import { usePlayerStore } from '../stores/player'
import { usePlaylistsStore } from '../stores/playlists'
import { useNoticeStore } from '../stores/notice'

const list = ref([])
const loading = ref(false)
const router = useRouter()
const playerStore = usePlayerStore()
const playlists = usePlaylistsStore()
const notice = useNoticeStore()

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
    await removeFavorite(f.songId, {
      source: musicSource(f),
      sourceSongId: sourceSongId(f),
    })
    list.value = list.value.filter((x) => x.id !== f.id)
  } catch { /* ignore */ }
}

function sourceLabel(item) {
  return musicSourceLabel(item)
}

function playFavorite(f) {
  const index = playerStore.addToQueue(f)
  if (index < 0) {
    notice.show({
      type: 'warning',
      title: 'Favorites',
      message: '这首收藏缺少歌曲 ID，暂时不能播放。',
      duration: 2200,
    })
    return
  }
  playerStore.playItem(index)
  playerStore.setMiniPlayer(true)
}

function saveToPlaylist(f) {
  const added = playlists.addTrackToFirst(f)
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
  router.push({ path: '/', query: { from: 'favorites' } })
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
}

.back-btn,
.remove-btn,
.icon-action {
  min-height: var(--control-height);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-md);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 800;
}

.back-btn {
  padding: 0 14px;
  flex-shrink: 0;
}

.back-btn:hover,
.remove-btn:hover {
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
  min-height: 66px;
  padding: 10px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
}

.track-cover {
  width: 46px;
  height: 46px;
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

.track-info {
  min-width: 0;
  flex: 1;
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
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.remove-btn {
  padding: 0 12px;
  color: var(--coral);
}

.icon-action {
  width: var(--control-height);
  flex-shrink: 0;
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

  .track-row {
    align-items: flex-start;
  }

  .remove-btn span {
    display: none;
  }
}

@media (max-width: 520px) {
  .track-row {
    display: grid;
    grid-template-columns: 46px minmax(0, 1fr) auto auto auto;
  }
}
</style>
