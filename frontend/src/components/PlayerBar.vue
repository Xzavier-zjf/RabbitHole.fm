<template>
  <div class="player-bar" v-if="player.currentItem && !player.miniPlayerEnabled">
    <div class="player-info">
      <img
        v-if="showCoverImage"
        :src="proxyCoverUrl(player.currentItem.coverUrl)"
        class="cover"
        referrerpolicy="no-referrer"
        @error="hideCover"
      />
      <div v-else class="cover cover-fallback">
        <Music :size="24" />
      </div>
      <div class="meta">
        <div class="song-name">{{ player.currentItem.name || '未知曲目' }}</div>
        <div class="artist">{{ (player.currentItem.artists || []).join(' / ') || itemTypeLabel }}</div>
      </div>
    </div>

    <div class="transport">
      <div class="player-controls">
        <button class="ctrl-btn" type="button" @click="player.prev()" aria-label="上一首" title="上一首">
          <SkipBack :size="18" />
        </button>
        <button class="ctrl-btn play-btn" type="button" @click="player.togglePlay()" :aria-label="player.isPlaying ? '暂停' : '播放'" :title="player.isPlaying ? '暂停' : '播放'">
          <Pause v-if="player.isPlaying" :size="22" fill="currentColor" />
          <Play v-else :size="22" fill="currentColor" />
        </button>
        <button class="ctrl-btn" type="button" @click="player.next()" aria-label="下一首" title="下一首">
          <SkipForward :size="18" />
        </button>
      </div>

      <div class="progress-row">
        <span class="time-display">{{ formatTime(player.currentTime) }}</span>
        <div
          class="progress-bar"
          role="slider"
          tabindex="0"
          :aria-valuenow="Math.round(player.progress * 100)"
          :aria-valuetext="progressValueText"
          aria-valuemin="0"
          aria-valuemax="100"
          aria-label="播放进度"
          @click="seekTo($event)"
          @keydown="onProgressKeydown"
        >
          <div class="progress-fill" :style="{ width: (player.progress * 100) + '%' }"></div>
          <span class="progress-thumb" :style="{ left: (player.progress * 100) + '%' }"></span>
        </div>
        <span class="time-display">{{ formatTime(player.duration) }}</span>
      </div>
    </div>

    <div class="player-actions">
      <button
        v-if="player.currentItem.songId"
        class="action-btn"
        :class="{ active: favIds.has(player.currentItem.songId) }"
        type="button"
        @click="toggleFav"
        :title="favIds.has(player.currentItem.songId) ? '取消收藏' : '收藏歌曲'"
        :aria-label="favIds.has(player.currentItem.songId) ? '取消收藏' : '收藏歌曲'"
      >
        <Heart :size="18" :fill="favIds.has(player.currentItem.songId) ? 'currentColor' : 'none'" />
      </button>

      <button
        v-if="player.currentItem.songId"
        class="action-btn"
        type="button"
        @click="addToPlaylist"
        aria-label="加入我的歌单"
        title="加入我的歌单"
      >
        <ListPlus :size="18" />
      </button>

      <button
        class="action-btn"
        :class="{ active: player.smartContinueEnabled }"
        type="button"
        @click="player.toggleSmartContinue()"
        :aria-label="player.smartContinueEnabled ? '关闭智能续播' : '开启智能续播'"
        :title="player.smartContinueEnabled ? '关闭智能续播' : '开启智能续播'"
      >
        <Repeat2 :size="18" />
      </button>

      <button
        class="action-btn"
        type="button"
        @click="player.setMiniPlayer(true)"
        aria-label="打开迷你播放器"
        title="打开迷你播放器"
      >
        <PictureInPicture2 :size="18" />
      </button>

      <div class="volume-control">
        <Volume2 :size="18" class="volume-icon" />
        <input
          type="range"
          min="0"
          max="1"
          step="0.01"
          :value="volume"
          aria-label="音量"
          @input="setVolume($event)"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { usePlayerStore } from '../stores/player'
import { useUserStore } from '../stores/user'
import { useNoticeStore } from '../stores/notice'
import { usePlaylistsStore } from '../stores/playlists'
import { proxyCoverUrl, addFavorite, removeFavorite, getFavorites } from '../api'
import { ref, onMounted, computed, watch } from 'vue'
import {
  Heart,
  ListPlus,
  Music,
  Pause,
  PictureInPicture2,
  Play,
  Repeat2,
  SkipBack,
  SkipForward,
  Volume2,
} from '@lucide/vue'

const player = usePlayerStore()
const userStore = useUserStore()
const notice = useNoticeStore()
const playlists = usePlaylistsStore()
const volume = ref(0.8)
const favIds = ref(new Set())
const coverFailed = ref(false)

const showCoverImage = computed(() => !!player.currentItem?.coverUrl && !coverFailed.value)
const itemTypeLabel = computed(() => player.currentItem?.type === 'dj' ? 'DJ 口播' : 'RabbitHole.fm')
const progressValueText = computed(() => {
  return `${formatTime(player.currentTime)} / ${formatTime(player.duration)}`
})

watch(() => player.currentItem?.coverUrl, () => {
  coverFailed.value = false
})

onMounted(loadFavs)

async function loadFavs() {
  if (!userStore.isLoggedIn) return
  try {
    const res = await getFavorites()
    favIds.value = new Set((res.data || []).map((f) => f.songId))
  } catch { /* ignore */ }
}

async function toggleFav() {
  if (!userStore.isLoggedIn) return
  const item = player.currentItem
  if (!item?.songId) return
  try {
    if (favIds.value.has(item.songId)) {
      await removeFavorite(item.songId)
      favIds.value.delete(item.songId)
    } else {
      await addFavorite(item.songId, {
        songName: item.name || '',
        artists: (item.artists || []).join(' / '),
        coverUrl: item.coverUrl || '',
      })
      favIds.value.add(item.songId)
    }
  } catch { /* ignore */ }
}

function addToPlaylist() {
  if (!player.currentItem?.songId) return
  const added = playlists.addTrackToFirst(player.currentItem)
  notice.show({
    type: added ? 'success' : 'info',
    title: 'Playlist',
    message: added ? '已加入我的歌单。' : '这首歌已经在歌单里了。',
    duration: 1800,
  })
}

function formatTime(seconds) {
  if (!seconds || isNaN(seconds)) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function seekTo(e) {
  if (!player.duration) return
  const rect = e.currentTarget.getBoundingClientRect()
  const fraction = (e.clientX - rect.left) / rect.width
  player.seek(Math.max(0, Math.min(1, fraction)))
}

function onProgressKeydown(e) {
  if (!player.duration) return

  const stepSeconds = 5
  const currentTime = Math.max(0, player.currentTime || 0)
  let nextTime = null

  switch (e.key) {
    case 'ArrowLeft':
    case 'ArrowDown':
      nextTime = currentTime - stepSeconds
      break
    case 'ArrowRight':
    case 'ArrowUp':
      nextTime = currentTime + stepSeconds
      break
    case 'Home':
      nextTime = 0
      break
    case 'End':
      nextTime = player.duration
      break
    default:
      return
  }

  e.stopPropagation()
  e.preventDefault()
  const clampedTime = Math.min(player.duration, Math.max(0, nextTime))
  player.seek(clampedTime / player.duration)
}

function setVolume(e) {
  volume.value = parseFloat(e.target.value)
  player.setVolume(volume.value)
}

function hideCover() {
  coverFailed.value = true
}
</script>

<style scoped>
.player-bar {
  position: fixed;
  left: 16px;
  right: 16px;
  bottom: 14px;
  min-height: var(--player-height);
  z-index: 100;
  display: grid;
  grid-template-columns: minmax(220px, 0.8fr) minmax(320px, 1.2fr) minmax(190px, 0.55fr);
  align-items: center;
  gap: 18px;
  padding: 12px 16px;
  border: 1px solid var(--divider-strong);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-elevated) 88%, transparent);
  backdrop-filter: blur(22px);
  box-shadow: var(--shadow-panel);
}

.player-info {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.cover {
  width: 58px;
  height: 58px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 14px 30px color-mix(in srgb, var(--bg-primary) 30%, transparent);
}

.cover-fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-card));
}

.meta {
  min-width: 0;
}

.song-name,
.artist {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.song-name {
  color: var(--text-primary);
  font-size: 0.96rem;
  font-weight: 850;
}

.artist {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.78rem;
}

.transport {
  min-width: 0;
  display: grid;
  gap: 9px;
}

.player-controls {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.ctrl-btn,
.action-btn {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-card) 76%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    transform var(--transition-fast),
    border-color var(--transition-fast),
    background var(--transition-fast),
    color var(--transition-fast);
}

.ctrl-btn:hover,
.action-btn:hover {
  transform: translateY(-1px);
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
  background: var(--bg-card-hover);
}

.play-btn {
  width: 48px;
  height: 48px;
  border: none;
  border-radius: 50%;
  background: var(--accent);
  color: #07100c;
}

.play-btn svg {
  margin-left: 1px;
}

.progress-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) 42px;
  align-items: center;
  gap: 10px;
}

.progress-bar {
  height: 8px;
  position: relative;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--text-primary) 10%, transparent);
  cursor: pointer;
}

.progress-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--accent), color-mix(in srgb, var(--accent) 62%, var(--blue)));
}

.progress-thumb {
  position: absolute;
  top: 50%;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--text-primary);
  border: 3px solid var(--accent);
  transform: translate(-50%, -50%);
  opacity: 0;
  transition: opacity var(--transition-fast);
}

.progress-bar:hover .progress-thumb,
.progress-bar:focus-visible .progress-thumb {
  opacity: 1;
}

.time-display {
  color: var(--text-tertiary);
  font-size: 0.74rem;
  font-variant-numeric: tabular-nums;
  text-align: center;
}

.player-actions {
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.action-btn.active {
  color: var(--coral);
  border-color: color-mix(in srgb, var(--coral) 34%, var(--divider));
}

.volume-control {
  min-width: 136px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
}

.volume-control input {
  width: 96px;
  accent-color: var(--accent);
}

@media (max-width: 980px) {
  .player-bar {
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 12px;
  }

  .transport {
    grid-column: 1 / -1;
    order: 3;
  }

  .player-actions {
    justify-content: flex-end;
  }
}

@media (max-width: 620px) {
  .player-bar {
    left: 10px;
    right: 10px;
    bottom: 10px;
    padding: 10px;
    border-radius: var(--radius-lg);
  }

  .cover {
    width: 50px;
    height: 50px;
  }

  .volume-control {
    display: none;
  }

  .progress-row {
    grid-template-columns: 38px minmax(0, 1fr) 38px;
    gap: 7px;
  }
}
</style>
