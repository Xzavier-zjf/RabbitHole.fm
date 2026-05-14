<template>
  <div class="player-bar" v-if="player.currentItem">
    <div class="player-info">
      <img v-if="showCoverImage" :src="proxyCoverUrl(player.currentItem.coverUrl)" class="cover" referrerpolicy="no-referrer" @error="hideCover" />
      <div v-else class="cover cover-fallback">RH</div>
      <div class="meta">
        <div class="song-name">{{ player.currentItem.name || '未知曲目' }}</div>
        <div class="artist">{{ (player.currentItem.artists || []).join(' / ') }}</div>
      </div>
    </div>

    <button
      v-if="player.currentItem.songId"
      class="fav-btn"
      :class="{ favorited: favIds.has(player.currentItem.songId) }"
      @click="toggleFav"
      :title="favIds.has(player.currentItem.songId) ? '取消收藏' : '收藏歌曲'"
    >
      {{ favIds.has(player.currentItem.songId) ? '❤️' : '🤍' }}
    </button>

    <div class="player-controls">
      <button class="ctrl-btn" @click="player.prev()">&#9664;&#9664;</button>
      <button class="ctrl-btn play-btn" @click="player.togglePlay()">
        {{ player.isPlaying ? '&#9646;&#9646;' : '&#9654;' }}
      </button>
      <button class="ctrl-btn" @click="player.next()">&#9654;&#9654;</button>
    </div>

    <div class="progress-wrap">
      <div class="progress-bar" @click="seekTo($event)">
        <div class="progress-fill" :style="{ width: (player.progress * 100) + '%' }"></div>
      </div>
      <div class="time-display">
        {{ formatTime(player.currentTime) }} / {{ formatTime(player.duration) }}
      </div>
    </div>

    <div class="volume-control">
      <span class="volume-icon">&#128264;</span>
      <input type="range" min="0" max="1" step="0.01" :value="volume" @input="setVolume($event)" />
    </div>
  </div>
</template>

<script setup>
import { usePlayerStore } from '../stores/player'
import { useUserStore } from '../stores/user'
import { proxyCoverUrl, addFavorite, removeFavorite, getFavorites } from '../api'
import { ref, onMounted, computed, watch } from 'vue'

const player = usePlayerStore()
const userStore = useUserStore()
const volume = ref(0.8)
const favIds = ref(new Set())
const coverFailed = ref(false)

const showCoverImage = computed(() => !!player.currentItem?.coverUrl && !coverFailed.value)

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

function formatTime(seconds) {
  if (!seconds || isNaN(seconds)) return '0:00'
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${m}:${s.toString().padStart(2, '0')}`
}

function seekTo(e) {
  const rect = e.currentTarget.getBoundingClientRect()
  const fraction = (e.clientX - rect.left) / rect.width
  player.seek(Math.max(0, Math.min(1, fraction)))
}

function setVolume(e) {
  volume.value = parseFloat(e.target.value)
  player.setVolume(volume.value)
}

function hideCover(event) {
  coverFailed.value = true
}
</script>

<style scoped>
.player-bar {
  position: fixed;
  bottom: 16px;
  left: 18px;
  right: 18px;
  min-height: 76px;
  background: color-mix(in srgb, var(--bg-elevated) 88%, transparent);
  backdrop-filter: blur(24px);
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 14px 20px;
  z-index: 100;
  border: 1px solid var(--divider);
  border-radius: 24px;
  box-shadow: var(--shadow-soft);
}

.player-info {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 210px;
}

.cover {
  width: 52px;
  height: 52px;
  border-radius: 16px;
  object-fit: cover;
  flex-shrink: 0;
  box-shadow: 0 12px 24px color-mix(in srgb, var(--accent) 15%, transparent);
}

.cover-fallback {
  display: grid;
  place-items: center;
  color: color-mix(in srgb, var(--text-primary) 70%, transparent);
  font-family: var(--font-display);
  background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 28%, var(--bg-secondary)), color-mix(in srgb, var(--mystic) 18%, var(--bg-primary)));
}

.meta {
  overflow: hidden;
}

.song-name {
  color: var(--text-primary);
  font-size: 0.94rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.artist {
  color: var(--text-secondary);
  font-size: 0.78rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-top: 4px;
}

.fav-btn,
.ctrl-btn {
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  cursor: pointer;
  transition: transform var(--transition-theme), border-color var(--transition-theme), background var(--transition-theme);
}

.fav-btn {
  font-size: 1rem;
  border-radius: 999px;
  padding: 8px 12px;
}

.fav-btn:hover,
.ctrl-btn:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--accent) 36%, transparent);
}

.player-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ctrl-btn {
  color: var(--text-secondary);
  border-radius: 14px;
  padding: 8px 10px;
  font-size: 1rem;
}

.play-btn {
  color: #fff7ef;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 62%, var(--mystic)));
  border-radius: 50%;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
}

.progress-wrap {
  flex: 1;
  display: grid;
  gap: 8px;
}

.progress-bar {
  flex: 1;
  height: 4px;
  background: color-mix(in srgb, var(--text-primary) 12%, transparent);
  border-radius: 999px;
  cursor: pointer;
  position: relative;
  transition: height var(--transition-theme), box-shadow var(--transition-theme);
}

.progress-bar:hover {
  height: 6px;
  box-shadow: 0 0 14px color-mix(in srgb, var(--accent) 28%, transparent);
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent), color-mix(in srgb, var(--mystic) 68%, var(--accent)));
  border-radius: 999px;
  transition: width 0.1s linear;
}

.time-display {
  color: var(--text-secondary);
  font-size: 0.76rem;
  font-variant-numeric: tabular-nums;
}

.volume-control {
  display: flex;
  align-items: center;
  gap: 8px;
}

.volume-icon {
  color: var(--text-secondary);
}

.volume-control input {
  width: 88px;
  accent-color: var(--accent);
}

@media (max-width: 900px) {
  .player-bar {
    left: 12px;
    right: 12px;
    bottom: 12px;
    padding: 12px 14px;
    gap: 12px;
    flex-wrap: wrap;
  }

  .player-info,
  .progress-wrap {
    min-width: 100%;
  }
}
</style>
