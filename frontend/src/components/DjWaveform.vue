<template>
  <div class="dj-waveform" v-if="isDjPlaying">
    <div class="dj-meta">
      <span class="live-dot"></span>
      <Mic2 :size="16" />
      <span>DJ 小糖 · 播报中</span>
    </div>

    <div class="wave-container" aria-hidden="true">
      <span v-for="i in 28" :key="i" class="wave-bar" :style="barStyles[i - 1]"></span>
    </div>

    <div class="subtitle-track" ref="trackRef">
      <div v-if="!subtitleLines.length" class="no-subtitle">
        <Captions :size="26" />
        <span>{{ fallbackSubtitleText }}</span>
      </div>
      <div
        v-for="(line, i) in subtitleLines"
        :key="`${i}-${line.text}`"
        :class="['subtitle-line', { active: i === activeLine }]"
      >
        {{ line.text }}
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch, nextTick } from 'vue'
import { Captions, Mic2 } from '@lucide/vue'
import { usePlayerStore } from '../stores/player'

const player = usePlayerStore()
const trackRef = ref(null)
const isDjPlaying = computed(() => player.currentItem?.type === 'dj')

const subtitleLines = computed(() => {
  const text = normalizeSubtitle(player.currentItem?.djSubtitle || player.currentItem?.message || '')
  if (!text) return []
  return splitSubtitle(text)
})

const fallbackSubtitleText = computed(() => {
  if (player.djUnavailable) return '口播暂不可用，已继续播放'
  return player.currentItem?.name || '暂无口播字幕'
})

const activeLine = computed(() => {
  const lines = subtitleLines.value
  if (!lines.length) return 0
  const duration = player.duration || 0
  const progress = duration > 0 ? player.currentTime / duration : 0
  return Math.min(lines.length - 1, Math.max(0, Math.floor(progress * lines.length)))
})

watch(activeLine, () => {
  nextTick(() => {
    if (trackRef.value) {
      const lines = trackRef.value.querySelectorAll('.subtitle-line')
      const el = lines[activeLine.value]
      if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
  })
})

function normalizeSubtitle(text) {
  return String(text)
    .replace(/^\(台湾腔\)/, '')
    .replace(/\s+/g, ' ')
    .trim()
}

function splitSubtitle(text) {
  const rawParts = text
    .split(/[。！？!?；;，,、\n]/)
    .map((part) => part.trim())
    .filter(Boolean)

  const parts = rawParts.length ? rawParts : [text]
  const chunks = []

  for (const part of parts) {
    if (part.length <= 16) {
      chunks.push(part)
      continue
    }
    for (let i = 0; i < part.length; i += 14) {
      chunks.push(part.slice(i, i + 14))
    }
  }

  return chunks.map((text) => ({ text }))
}

const barStyles = Array.from({ length: 28 }, (_, i) => {
  const seed = (i * 2654435761) & 0xFFFFFFFF
  const rand = (seed % 100) / 100
  const delay = i * 0.045
  const dur = 0.42 + rand * 0.38
  return {
    animationDelay: `${delay}s`,
    animationDuration: `${dur}s`,
  }
})
</script>

<style scoped>
.dj-waveform {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 22px;
  padding: 32px 24px 46px;
  background:
    radial-gradient(circle at 50% 18%, color-mix(in srgb, var(--blue) 14%, transparent), transparent 42%),
    linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 82%, transparent), color-mix(in srgb, var(--bg-card) 96%, transparent));
}

.dj-meta {
  display: inline-flex;
  align-items: center;
  gap: 9px;
  padding: 8px 12px;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--divider));
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--accent) 9%, transparent);
  color: var(--accent);
  font-size: 0.78rem;
  font-weight: 850;
  text-transform: uppercase;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--accent);
  animation: breathe 1.5s ease-in-out infinite;
  box-shadow: 0 0 0 0 color-mix(in srgb, var(--accent) 28%, transparent);
}

.wave-container {
  width: min(100%, 620px);
  height: 104px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 0 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
}

.wave-bar {
  width: 5px;
  border-radius: var(--radius-pill);
  background: linear-gradient(180deg, var(--accent), color-mix(in srgb, var(--accent) 45%, var(--blue)));
  animation: wave 0.5s ease-in-out infinite alternate;
  opacity: 0.9;
}

.subtitle-track {
  width: min(100%, 640px);
  max-height: min(46vh, 330px);
  overflow-y: auto;
  padding: 22px 18px;
  mask-image: linear-gradient(transparent 0%, black 12%, black 88%, transparent 100%);
}

.no-subtitle {
  min-height: 130px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 10px;
  color: var(--text-tertiary);
  text-align: center;
}

.subtitle-line {
  color: var(--text-tertiary);
  text-align: center;
  font-family: var(--font-lyric);
  font-size: 1rem;
  line-height: 2.15;
  padding: 5px 0;
  opacity: 0.48;
  transition:
    color 240ms ease,
    opacity 240ms ease,
    transform 260ms ease,
    font-size 260ms ease;
}

.subtitle-line.active {
  color: var(--text-primary);
  font-size: clamp(1.42rem, 2.3vw, 2.1rem);
  font-weight: 850;
  opacity: 1;
  transform: scale(1.02);
}

@keyframes breathe {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--accent) 28%, transparent);
  }

  50% {
    transform: scale(1.28);
    box-shadow: 0 0 0 10px color-mix(in srgb, var(--accent) 8%, transparent);
  }
}

@keyframes wave {
  from { height: 14px; }
  to { height: 74px; }
}

@media (max-width: 720px) {
  .dj-waveform {
    padding: 26px 14px 36px;
  }

  .wave-container {
    gap: 4px;
    height: 86px;
  }

  .wave-bar {
    width: 4px;
  }
}
</style>
