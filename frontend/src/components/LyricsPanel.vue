<template>
  <div class="lyrics-shell" :class="{ restoring: isRestoring }">
    <div class="lyrics-bg" :style="coverGhostStyle"></div>

    <div class="lyrics-stage">
      <header class="lyrics-head">
        <div class="lyrics-kicker">
          <AudioLines :size="16" />
          <span>Live Lyrics</span>
        </div>
        <div class="lyrics-title-block">
          <div class="lyrics-song-name">{{ currentName }}</div>
          <div class="lyrics-song-meta">{{ currentArtists }}</div>
        </div>
      </header>

      <div class="lyrics-panel" ref="panelRef">
        <div v-if="!lyricLines.length" class="no-lyric-card">
          <div class="no-lyric-mark">
            <Captions :size="28" />
          </div>
          <div class="no-lyric-title">暂无歌词</div>
          <div class="no-lyric-copy">这首歌暂时没有可同步的歌词，音乐仍会继续播放。</div>
        </div>

        <div
          v-for="(line, i) in mergedLines"
          :key="i"
          :class="['lyric-line', {
            active: i === activeLine,
            settling: isRestoring && i === activeLine,
            nearby: Math.abs(i - activeLine) <= 2,
            distant: Math.abs(i - activeLine) >= 5,
          }]"
          :style="lineStyle(i)"
          :ref="el => { if (i === activeLine) activeEl = el }"
        >
          <div class="line-main">{{ line.text || '...' }}</div>
          <div v-if="line.translation" class="translation">{{ line.translation }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, computed, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { AudioLines, Captions } from '@lucide/vue'
import { usePlayerStore } from '../stores/player'
import { proxyCoverUrl } from '../api'

const player = usePlayerStore()
const panelRef = ref(null)
const activeEl = ref(null)
const isRestoring = ref(true)
let restoreTimer = null

const currentName = computed(() => player.currentItem?.name || '正在播放')
const currentArtists = computed(() => {
  const artists = player.currentItem?.artists || []
  return artists.length ? artists.join(' / ') : 'RabbitHole.fm'
})
const currentCover = computed(() => player.currentItem?.coverUrl || '')
const coverGhostStyle = computed(() => {
  if (!currentCover.value) return undefined
  return {
    backgroundImage: `url("${proxyCoverUrl(currentCover.value)}")`,
  }
})

const lyricLines = computed(() => {
  const lrc = player.currentItem?.lyric?.lrc
  if (!lrc) return []
  return parseLrc(lrc)
})

const translationLines = computed(() => {
  const tlrc = player.currentItem?.lyric?.tlyric
  if (!tlrc) return {}
  const lines = parseLrc(tlrc)
  const map = {}
  lines.forEach((l) => {
    map[Math.round(l.time * 10)] = l.text
  })
  return map
})

const mergedLines = computed(() => {
  return lyricLines.value.map((l) => ({
    ...l,
    translation: translationLines.value[Math.round(l.time * 10)] || null,
  }))
})

const activeLine = computed(() => {
  const t = player.currentTime
  for (let i = lyricLines.value.length - 1; i >= 0; i--) {
    if (lyricLines.value[i].time <= t) return i
  }
  return 0
})

const activeSweepStyle = computed(() => {
  const lines = lyricLines.value
  if (!lines.length || activeLine.value >= lines.length) return undefined
  const current = lines[activeLine.value]
  const next = lines[activeLine.value + 1]
  const start = current?.time ?? 0
  const end = next?.time ?? start + 4
  const span = Math.max(0.8, end - start)
  const ratio = Math.max(0, Math.min(1, (player.currentTime - start) / span))
  const pct = 12 + ratio * 88
  return {
    '--lyric-sweep': pct.toFixed(2) + '%',
  }
})

function lineStyle(index) {
  const distance = index - activeLine.value
  const abs = Math.abs(distance)
  const depth = Math.min(abs, 4)
  const translateY = distance < 0 ? depth * -5 : depth * 7
  const scale = index === activeLine.value ? 1 : Math.max(0.95, 1 - depth * 0.012)
  const blur = abs >= 4 ? Math.min(2.5, (abs - 3) * 0.75) : 0
  return {
    ...(index === activeLine.value ? activeSweepStyle.value || {} : {}),
    '--line-offset-y': translateY + 'px',
    '--line-scale': String(scale),
    '--line-blur': blur + 'px',
  }
}

watch(activeLine, () => {
  nextTick(() => {
    if (activeEl.value && panelRef.value) {
      const panel = panelRef.value
      const line = activeEl.value
      panel.scrollTo({
        top: Math.max(0, line.offsetTop - panel.clientHeight / 2 + line.clientHeight / 2),
        behavior: isRestoring.value ? 'auto' : 'smooth',
      })
    }
  })
})

watch(() => player.currentItem?.songId, () => {
  beginRestorePhase()
})

onMounted(() => {
  beginRestorePhase()
  nextTick(() => {
    if (activeEl.value && panelRef.value) {
      const panel = panelRef.value
      const line = activeEl.value
      panel.scrollTo({
        top: Math.max(0, line.offsetTop - panel.clientHeight / 2 + line.clientHeight / 2),
        behavior: 'auto',
      })
    }
  })
})

onBeforeUnmount(() => {
  if (restoreTimer) {
    clearTimeout(restoreTimer)
    restoreTimer = null
  }
})

function beginRestorePhase() {
  isRestoring.value = true
  if (restoreTimer) {
    clearTimeout(restoreTimer)
  }
  restoreTimer = setTimeout(() => {
    isRestoring.value = false
    restoreTimer = null
  }, 650)
}

function parseLrc(lrc) {
  const lines = []
  const regex = /\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)/g
  let match
  while ((match = regex.exec(lrc)) !== null) {
    const min = parseInt(match[1])
    const sec = parseInt(match[2])
    const ms = parseInt(match[3].padEnd(3, '0'))
    lines.push({ time: min * 60 + sec + ms / 1000, text: match[4].trim() })
  }
  return lines.sort((a, b) => a.time - b.time)
}
</script>

<style scoped>
.lyrics-shell {
  position: relative;
  height: 100%;
  overflow: hidden;
  background:
    radial-gradient(circle at 50% 10%, color-mix(in srgb, var(--accent) 10%, transparent), transparent 38%),
    linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 84%, transparent), color-mix(in srgb, var(--bg-card) 96%, transparent));
}

.lyrics-bg {
  position: absolute;
  inset: -20%;
  background-position: center;
  background-size: cover;
  opacity: 0.12;
  filter: blur(46px) saturate(1.22);
  transform: scale(1.05);
  mask-image: radial-gradient(circle at 50% 24%, black, transparent 62%);
}

.lyrics-stage {
  position: relative;
  z-index: 1;
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.lyrics-head {
  padding: 22px 28px 12px;
  display: grid;
  justify-items: center;
  gap: 12px;
  text-align: center;
}

.lyrics-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--accent);
  font-size: 0.74rem;
  font-weight: 850;
  text-transform: uppercase;
}

.lyrics-title-block {
  width: min(100%, 620px);
  padding: 14px 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-elevated) 68%, transparent);
  backdrop-filter: blur(14px);
}

.lyrics-song-name,
.lyrics-song-meta {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.lyrics-song-name {
  color: var(--text-primary);
  font-size: clamp(1.2rem, 2vw, 1.85rem);
  line-height: 1.15;
  font-weight: 850;
}

.lyrics-song-meta {
  margin-top: 6px;
  color: var(--text-secondary);
  font-size: 0.86rem;
}

.lyrics-panel {
  height: 100%;
  overflow-y: auto;
  padding: 12px 28px 74px;
  mask-image: linear-gradient(transparent 0%, black 10%, black 88%, transparent 100%);
  transition: opacity 320ms ease, transform 320ms ease;
}

.lyrics-shell.restoring .lyrics-panel {
  opacity: 0.78;
  transform: translateY(4px);
}

.no-lyric-card {
  width: min(100%, 480px);
  margin: 12vh auto 0;
  padding: 28px 24px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-elevated) 78%, transparent);
  box-shadow: var(--shadow-soft);
  text-align: center;
}

.no-lyric-mark {
  width: 62px;
  height: 62px;
  margin: 0 auto 16px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.no-lyric-title {
  color: var(--text-primary);
  font-size: 1.08rem;
  font-weight: 850;
}

.no-lyric-copy {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.75;
}

.lyric-line {
  width: min(100%, 820px);
  margin: 0 auto;
  padding: 14px 20px;
  border-radius: var(--radius-lg);
  color: var(--text-tertiary);
  text-align: center;
  opacity: 0.42;
  transform: translateY(var(--line-offset-y, 0px)) scale(var(--line-scale, 0.97));
  filter: blur(var(--line-blur, 0px));
  transition:
    color 240ms ease,
    transform 320ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 260ms ease,
    opacity 220ms ease,
    background 240ms ease,
    box-shadow 260ms ease;
}

.lyric-line + .lyric-line {
  margin-top: 4px;
}

.lyric-line.nearby {
  opacity: 0.72;
}

.lyric-line.distant {
  opacity: 0.22;
}

.line-main {
  font-family: var(--font-lyric);
  font-size: clamp(1rem, 1.2vw, 1.16rem);
  line-height: 1.95;
}

.lyric-line.active {
  --lyric-sweep: 18%;
  color: var(--text-primary);
  opacity: 1;
  background: color-mix(in srgb, var(--bg-elevated) 78%, transparent);
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--divider));
  box-shadow: 0 20px 54px color-mix(in srgb, var(--bg-primary) 20%, transparent);
}

.lyric-line.active .line-main {
  font-size: clamp(1.58rem, 2.7vw, 2.55rem);
  font-weight: 850;
  color: transparent;
  background-image: linear-gradient(
    90deg,
    var(--accent) 0%,
    color-mix(in srgb, var(--text-primary) 94%, white 4%) var(--lyric-sweep),
    color-mix(in srgb, var(--text-primary) 72%, transparent) 100%
  );
  background-clip: text;
  -webkit-background-clip: text;
}

.translation {
  margin-top: 6px;
  font-family: var(--font-ui);
  font-size: 0.86rem;
  line-height: 1.7;
  color: var(--text-secondary);
}

.lyric-line.settling {
  animation: lyric-settle 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

@keyframes lyric-settle {
  0% {
    opacity: 0.18;
    transform: translateY(10px) scale(0.97);
  }

  100% {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (max-width: 880px) {
  .lyrics-head {
    padding: 18px 16px 8px;
  }

  .lyrics-title-block {
    padding: 12px 14px;
  }

  .lyrics-panel {
    padding: 8px 14px 58px;
  }

  .lyric-line {
    width: 100%;
    padding: 12px 12px;
  }

  .lyric-line.active .line-main {
    font-size: clamp(1.35rem, 7vw, 1.95rem);
  }
}
</style>
