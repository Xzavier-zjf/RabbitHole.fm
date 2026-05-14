<template>
  <div class="dj-waveform" v-if="isDjPlaying">
    <div class="dj-meta">
      <span class="live-dot"></span>
      <span class="dj-caption">DJ 小糖 · 播报中</span>
    </div>

    <div class="wave-container">
      <span v-for="i in 20" :key="i" class="wave-bar" :style="barStyles[i - 1]"></span>
    </div>

    <div class="subtitle-track" ref="trackRef">
      <div v-if="!subtitleLines.length" class="no-subtitle">正在生成口播字幕...</div>
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
import { usePlayerStore } from '../stores/player'

const player = usePlayerStore()
const trackRef = ref(null)
const isDjPlaying = computed(() => player.currentItem?.type === 'dj')

const subtitleLines = computed(() => {
  const text = normalizeSubtitle(player.currentItem?.djSubtitle || player.currentItem?.message || '')
  if (!text) return []
  return splitSubtitle(text)
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

const barStyles = Array.from({ length: 20 }, (_, i) => {
  const seed = (i * 2654435761) & 0xFFFFFFFF
  const rand = (seed % 100) / 100
  const delay = i * 0.08
  const dur = 0.4 + rand * 0.3
  return {
    animationDelay: `${delay}s`,
    animationDuration: `${dur}s`,
  }
})
</script>

<style scoped>
.dj-waveform {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px 24px 44px;
  gap: 18px;
  flex: 1;
}

.dj-meta {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--mystic);
  letter-spacing: 0.16em;
  font-size: 0.78rem;
  text-transform: uppercase;
}

.live-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--mystic);
  animation: breathe 1.5s ease-in-out infinite;
  box-shadow: 0 0 0 0 color-mix(in srgb, var(--mystic) 28%, transparent);
}

@keyframes breathe {
  0%, 100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--mystic) 28%, transparent);
  }
  50% {
    transform: scale(1.28);
    box-shadow: 0 0 0 10px color-mix(in srgb, var(--mystic) 10%, transparent);
  }
}

.wave-container {
  display: flex;
  align-items: center;
  gap: 5px;
  height: 72px;
}

.wave-bar {
  width: 5px;
  background: linear-gradient(180deg, var(--accent), var(--mystic));
  border-radius: 999px;
  animation: wave 0.5s ease-in-out infinite alternate;
}

@keyframes wave {
  from { height: 10px; }
  to { height: 56px; }
}

.subtitle-track {
  width: min(100%, 560px);
  max-height: 290px;
  overflow-y: auto;
  padding: 22px 18px;
  mask-image: linear-gradient(transparent 0%, black 12%, black 88%, transparent 100%);
}

.no-subtitle {
  color: var(--text-tertiary);
  text-align: center;
  padding: 24px 0;
  font-size: 1rem;
}

.subtitle-line {
  color: var(--text-tertiary);
  text-align: center;
  font-family: var(--font-lyric);
  font-size: 1rem;
  line-height: 2.15;
  padding: 4px 0;
  transition: all 280ms ease;
}

.subtitle-line.active {
  color: var(--text-primary);
  font-size: 1.42rem;
  font-weight: 700;
  transform: scale(1.04);
  text-shadow: 0 0 12px color-mix(in srgb, var(--accent) 36%, transparent);
}

.subtitle-line.active::before {
  content: '▶';
  color: var(--mystic);
  margin-right: 8px;
}
</style>
