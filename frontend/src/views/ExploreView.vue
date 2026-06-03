<template>
  <div class="explore-view">
    <header class="page-head">
      <button class="back-btn" type="button" @click="$router.push('/')">
        <ChevronLeft :size="18" />
        <span>返回播放</span>
      </button>
      <div class="head-copy">
        <div class="page-kicker">
          <Compass :size="16" />
          <span>Explore</span>
        </div>
        <h1>探索</h1>
        <p>从频道、关键词和即时搜索里快速发现下一首歌。</p>
      </div>
    </header>

    <section class="panel-card channel-panel">
      <div class="section-head">
        <div>
          <div class="section-kicker">Channels</div>
          <h2>推荐入口</h2>
        </div>
      </div>

      <div class="channel-grid">
        <button
          v-for="channel in channels"
          :key="channel.id"
          class="channel-card"
          type="button"
          @click="openChannel(channel.id)"
        >
          <span class="channel-icon" :class="'tone-' + channel.tone">
            <component :is="channel.icon" :size="20" />
          </span>
          <span class="channel-copy">
            <span class="channel-name">{{ channel.name }}</span>
            <span class="channel-desc">{{ channel.desc }}</span>
          </span>
          <ArrowRight :size="18" />
        </button>
      </div>
    </section>

    <section class="panel-card search-panel">
      <div class="section-head">
        <div>
          <div class="section-kicker">Search</div>
          <h2>试听搜索</h2>
        </div>
      </div>

      <div class="quick-chips" aria-label="快捷搜索词">
        <button v-for="term in quickTerms" :key="term" type="button" @click="searchTerm(term)">
          {{ term }}
        </button>
      </div>

      <form class="search-row" @submit.prevent="search">
        <Search :size="18" />
        <input v-model="keyword" placeholder="搜索歌曲、歌手或专辑" />
        <button class="primary-btn" type="submit" :disabled="searching">
          <LoaderCircle v-if="searching" :size="18" class="spin" />
          <Search v-else :size="18" />
          <span>{{ searching ? '搜索中' : '搜索' }}</span>
        </button>
      </form>
      <MusicSourceSelect class="explore-source-select" />

      <div v-if="results.length" class="results">
        <article class="track-row" v-for="song in results" :key="`${song.source || 'netease'}-${song.id}`">
          <img v-if="song.coverUrl" class="track-cover" :src="proxyCoverUrl(song.coverUrl)" referrerpolicy="no-referrer" />
          <span v-else class="track-cover fallback">
            <Music :size="18" />
          </span>
          <span class="track-copy">
            <span class="track-title-row">
              <span class="track-title">{{ song.name }}</span>
              <span v-if="sourceLabel(song)" class="source-badge">{{ sourceLabel(song) }}</span>
            </span>
            <span class="track-meta">{{ (song.artists || []).join(' / ') || '未知歌手' }}</span>
          </span>
          <button class="icon-btn" type="button" @click="playSong(song)" aria-label="播放歌曲" title="播放歌曲">
            <Play :size="17" fill="currentColor" />
          </button>
          <button class="icon-btn" type="button" @click="saveSong(song)" aria-label="加入歌单" title="加入歌单">
            <ListPlus :size="17" />
          </button>
        </article>
      </div>

      <div v-else class="empty-state">
        <Sparkles :size="30" />
        <span>{{ emptyText }}</span>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChevronLeft,
  Compass,
  Flame,
  ListMusic,
  ListPlus,
  LoaderCircle,
  Music,
  Play,
  Rocket,
  Search,
  Sparkles,
} from '@lucide/vue'
import { proxyCoverUrl, searchSongs } from '../api'
import { usePlayerStore } from '../stores/player'
import { usePlaylistsStore } from '../stores/playlists'
import { useNoticeStore } from '../stores/notice'
import { useMusicSourceStore } from '../stores/music-source'
import MusicSourceSelect from '../components/MusicSourceSelect.vue'

const router = useRouter()
const player = usePlayerStore()
const playlists = usePlaylistsStore()
const notice = useNoticeStore()
const sourceStore = useMusicSourceStore()

const keyword = ref('')
const results = ref([])
const searching = ref(false)
const hasSearched = ref(false)

const quickTerms = ['周杰伦', '陈奕迅', '新歌', 'City Pop', 'Lo-fi', '摇滚']
const channels = [
  { id: 32953014, name: '华语热歌', desc: '适合房间点歌的稳定热歌入口', icon: ListMusic, tone: 'mint' },
  { id: 19723756, name: '飙升榜', desc: '快速捕捉最近上升曲目', icon: Rocket, tone: 'blue' },
  { id: 3778678, name: '热歌榜', desc: '更高密度的热门播放列表', icon: Flame, tone: 'coral' },
  { id: 3779629, name: '新歌榜', desc: '从新歌里继续往下听', icon: Sparkles, tone: 'mint' },
]

const emptyText = computed(() => {
  if (hasSearched.value && sourceStore.selectedSource !== 'all') {
    return '当前音乐源暂无结果，可切换到全部来源再试。'
  }
  return hasSearched.value ? '没有找到结果，换个关键词试试。' : '选择一个快捷词，或搜索你想听的方向。'
})

function openChannel(channelId) {
  router.push({ path: '/', query: { channelId: String(channelId) } })
}

function searchTerm(term) {
  keyword.value = term
  search()
}

async function search() {
  if (!keyword.value.trim()) return
  searching.value = true
  hasSearched.value = true
  try {
    const res = await searchSongs(keyword.value.trim(), 12, sourceStore.selectedSource)
    results.value = res.data || []
  } catch {
    results.value = []
  } finally {
    searching.value = false
  }
}

function playSong(song) {
  const index = player.addToQueue(song)
  if (index < 0) {
    notice.show({
      type: 'warning',
      title: 'Explore',
      message: '这首歌缺少可播放 ID，暂时不能加入队列。',
      duration: 2200,
    })
    return
  }
  player.playItem(index)
  player.setMiniPlayer(true)
  notice.show({
    type: 'success',
    title: 'Explore',
    message: '已加入队列并开始播放。',
    duration: 1800,
  })
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

function sourceLabel(song) {
  const source = song?.source || 'netease'
  if (source === 'netease') return ''
  return song?.sourceLabel || source
}
</script>

<style scoped>
.explore-view {
  min-height: 100vh;
  padding: 24px;
  color: var(--text-primary);
  overflow-y: auto;
}

.page-head,
.panel-card {
  width: min(1180px, 100%);
  margin-inline: auto;
}

.page-head {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;
}

.head-copy {
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

.page-head h1 {
  margin-top: 8px;
  font-size: clamp(2rem, 4vw, 4rem);
  line-height: 1;
}

.page-head p {
  margin-top: 10px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.panel-card {
  padding: 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-card) 84%, transparent);
  box-shadow: var(--shadow-soft);
}

.search-panel {
  margin-top: 16px;
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

.back-btn,
.primary-btn,
.icon-btn {
  min-height: var(--control-height);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-weight: 800;
}

.back-btn,
.icon-btn {
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  color: var(--text-secondary);
}

.back-btn {
  padding: 0 14px;
}

.icon-btn {
  width: var(--control-height);
}

.back-btn:hover,
.icon-btn:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
  background: var(--bg-card-hover);
}

.primary-btn {
  border: none;
  padding: 0 16px;
  background: var(--accent);
  color: #07100c;
}

.channel-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.channel-card {
  min-height: 132px;
  display: grid;
  grid-template-columns: 46px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.channel-card:hover {
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
  background: var(--bg-card-hover);
}

.channel-icon {
  width: 46px;
  height: 46px;
  display: grid;
  place-items: center;
  border-radius: 12px;
}

.tone-mint {
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.tone-blue {
  color: var(--blue);
  background: color-mix(in srgb, var(--blue) 12%, transparent);
}

.tone-coral {
  color: var(--coral);
  background: color-mix(in srgb, var(--coral) 12%, transparent);
}

.channel-copy {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.channel-name {
  color: var(--text-primary);
  font-weight: 850;
}

.channel-desc {
  color: var(--text-secondary);
  font-size: 0.82rem;
  line-height: 1.5;
}

.quick-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.quick-chips button {
  min-height: 36px;
  padding: 0 13px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 800;
}

.quick-chips button:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
}

.search-row {
  min-height: 52px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 10px 0 14px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-lg);
  background: var(--input-bg);
  color: var(--text-tertiary);
}

.explore-source-select {
  width: min(100%, 360px);
  margin-top: 10px;
}

.search-row:focus-within {
  border-color: color-mix(in srgb, var(--accent) 46%, var(--input-border));
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--accent) 10%, transparent);
}

.search-row input {
  flex: 1;
  min-width: 0;
  height: 48px;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
}

.results {
  display: grid;
  gap: 9px;
  margin-top: 14px;
}

.track-row {
  min-height: 66px;
  display: flex;
  align-items: center;
  gap: 12px;
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

.track-copy {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 4px;
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
  font-size: 0.8rem;
}

.empty-state {
  min-height: 220px;
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

@media (max-width: 980px) {
  .channel-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .explore-view {
    padding: 16px;
  }

  .page-head {
    flex-direction: column;
  }

  .channel-grid {
    grid-template-columns: 1fr;
  }

  .search-row {
    flex-wrap: wrap;
    padding: 10px;
  }

  .search-row input,
  .primary-btn {
    min-width: 100%;
  }

  .explore-source-select {
    width: 100%;
  }
}
</style>
