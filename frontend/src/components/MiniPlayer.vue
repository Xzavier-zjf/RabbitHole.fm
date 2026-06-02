<template>
  <aside v-if="player.currentItem && player.miniPlayerEnabled" class="mini-player" aria-label="迷你播放器">
    <button class="cover-btn" type="button" @click="openPlayer" aria-label="打开播放页" title="打开播放页">
      <img
        v-if="showCoverImage"
        :src="proxyCoverUrl(player.currentItem.coverUrl)"
        class="mini-cover"
        referrerpolicy="no-referrer"
        @error="coverFailed = true"
      />
      <span v-else class="mini-cover fallback">
        <Music :size="18" />
      </span>
    </button>

    <div class="mini-meta">
      <div class="mini-title">{{ player.currentItem.name || '未知曲目' }}</div>
      <div class="mini-artist">{{ (player.currentItem.artists || []).join(' / ') || itemTypeLabel }}</div>
    </div>

    <div class="mini-controls">
      <button class="mini-btn" type="button" @click="player.prev()" aria-label="上一首" title="上一首">
        <SkipBack :size="16" />
      </button>
      <button class="mini-btn play-btn" type="button" @click="player.togglePlay()" :aria-label="player.isPlaying ? '暂停' : '播放'" :title="player.isPlaying ? '暂停' : '播放'">
        <Pause v-if="player.isPlaying" :size="17" fill="currentColor" />
        <Play v-else :size="17" fill="currentColor" />
      </button>
      <button class="mini-btn" type="button" @click="player.next()" aria-label="下一首" title="下一首">
        <SkipForward :size="16" />
      </button>
      <button class="mini-btn" type="button" @click="openPlayer" aria-label="展开播放器" title="展开播放器">
        <Maximize2 :size="16" />
      </button>
      <button class="mini-btn" type="button" @click="player.setMiniPlayer(false)" aria-label="关闭迷你播放器" title="关闭迷你播放器">
        <X :size="16" />
      </button>
    </div>

    <div class="mini-progress" aria-hidden="true">
      <span :style="{ width: (player.progress * 100) + '%' }"></span>
    </div>
  </aside>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Maximize2, Music, Pause, Play, SkipBack, SkipForward, X } from '@lucide/vue'
import { usePlayerStore } from '../stores/player'
import { proxyCoverUrl } from '../api'

const player = usePlayerStore()
const router = useRouter()
const coverFailed = ref(false)

const showCoverImage = computed(() => !!player.currentItem?.coverUrl && !coverFailed.value)
const itemTypeLabel = computed(() => player.currentItem?.type === 'dj' ? 'DJ 口播' : 'RabbitHole.fm')

watch(() => player.currentItem?.coverUrl, () => {
  coverFailed.value = false
})

function openPlayer() {
  router.push('/')
}
</script>

<style scoped>
.mini-player {
  position: fixed;
  right: 18px;
  bottom: 18px;
  z-index: 260;
  width: min(440px, calc(100vw - 24px));
  min-height: 74px;
  display: grid;
  grid-template-columns: 52px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  overflow: hidden;
  border: 1px solid var(--divider-strong);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-elevated) 92%, transparent);
  box-shadow: var(--shadow-panel);
  backdrop-filter: blur(20px);
}

.cover-btn {
  width: 52px;
  height: 52px;
  padding: 0;
  border: none;
  border-radius: 12px;
  overflow: hidden;
  background: transparent;
  cursor: pointer;
}

.mini-cover {
  width: 52px;
  height: 52px;
  object-fit: cover;
}

.mini-cover.fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-card));
}

.mini-meta {
  min-width: 0;
}

.mini-title,
.mini-artist {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mini-title {
  color: var(--text-primary);
  font-size: 0.92rem;
  font-weight: 850;
}

.mini-artist {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.76rem;
}

.mini-controls {
  display: flex;
  align-items: center;
  gap: 6px;
}

.mini-btn {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid var(--divider);
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--bg-card) 72%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
}

.mini-btn:hover {
  color: var(--text-primary);
  background: var(--bg-card-hover);
}

.play-btn {
  border-color: transparent;
  background: var(--accent);
  color: #07100c;
}

.mini-progress {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  height: 3px;
  background: color-mix(in srgb, var(--text-primary) 10%, transparent);
}

.mini-progress span {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, var(--accent), var(--blue));
}

@media (max-width: 620px) {
  .mini-player {
    left: 10px;
    right: 10px;
    bottom: 10px;
    width: auto;
    grid-template-columns: 46px minmax(0, 1fr) auto;
    gap: 10px;
  }

  .cover-btn,
  .mini-cover {
    width: 46px;
    height: 46px;
  }

  .mini-controls {
    gap: 4px;
  }

  .mini-btn {
    width: 32px;
    height: 32px;
  }
}
</style>
