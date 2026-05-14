<template>
  <div class="lyrics-shell" :class="{ restoring: isRestoring }">
    <div class="lyrics-backdrop">
      <div class="lyrics-backdrop-orbit orbit-a"></div>
      <div class="lyrics-backdrop-orbit orbit-b"></div>
    </div>

    <div class="lyrics-stage">
      <div class="lyrics-head">
        <div v-if="currentCover" class="lyrics-cover-ghost" :style="coverGhostStyle"></div>
        <div class="lyrics-kicker">Now Drifting</div>
        <div class="lyrics-song-block">
          <div class="lyrics-song-name">{{ currentName }}</div>
          <div class="lyrics-song-meta">{{ currentArtists }}</div>
        </div>
        <div class="lyrics-caption">歌词会沿着当前播放位置慢慢浮上来。</div>
      </div>

      <div class="lyrics-panel" ref="panelRef">
        <div v-if="!lyricLines.length" class="no-lyric-card">
          <div class="no-lyric-mark">RH</div>
          <div class="no-lyric-title">这首歌暂时没有歌词</div>
          <div class="no-lyric-copy">像兔子洞里的一小段回声，但旋律还在继续向前。</div>
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
import { usePlayerStore } from '../stores/player'

const player = usePlayerStore()
const panelRef = ref(null)
const activeEl = ref(null)
const isRestoring = ref(true)
let restoreTimer = null

const currentName = computed(() => player.currentItem?.name || '正在坠入新的旋律')
const currentArtists = computed(() => {
  const artists = player.currentItem?.artists || []
  return artists.length ? artists.join(' / ') : 'RabbitHole.fm'
})
const currentCover = computed(() => player.currentItem?.coverUrl || '')
const coverGhostStyle = computed(() => {
  if (!currentCover.value) return undefined
  return {
    backgroundImage: 'linear-gradient(180deg, color-mix(in srgb, var(--bg-card) 22%, transparent), color-mix(in srgb, var(--bg-card) 78%, transparent)), url(' + JSON.stringify(currentCover.value).slice(1, -1) + ')',
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
  const pct = 16 + ratio * 84
  return {
    '--lyric-sweep': pct.toFixed(2) + '%',
  }
})

function lineStyle(index) {
  const distance = index - activeLine.value
  const abs = Math.abs(distance)
  const depth = Math.min(abs, 4)
  const translateY = distance < 0 ? depth * -5 : depth * 7
  const scale = index === activeLine.value ? 1 : Math.max(0.94, 1 - depth * 0.015)
  const blur = abs >= 3 ? Math.min(3, (abs - 2) * 0.8) : 0
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
  border-radius: inherit;
  background:
    radial-gradient(circle at 50% 18%, color-mix(in srgb, var(--accent) 12%, transparent), transparent 44%),
    radial-gradient(circle at 82% 24%, color-mix(in srgb, var(--mystic) 10%, transparent), transparent 32%),
    linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 92%, transparent), color-mix(in srgb, var(--bg-card) 96%, transparent));
}

.lyrics-shell.restoring {
  filter: saturate(0.92);
}

.lyrics-backdrop {
  position: absolute;
  inset: 0;
  pointer-events: none;
  overflow: hidden;
}

.lyrics-backdrop-orbit {
  position: absolute;
  border-radius: 50%;
  filter: blur(6px);
}

.orbit-a {
  width: 340px;
  height: 340px;
  top: -120px;
  left: 50%;
  transform: translateX(-50%);
  background: radial-gradient(circle, color-mix(in srgb, var(--accent) 16%, transparent), transparent 68%);
  opacity: 0.82;
}

.orbit-b {
  width: 280px;
  height: 280px;
  right: -70px;
  bottom: 10%;
  background: radial-gradient(circle, color-mix(in srgb, var(--mystic) 14%, transparent), transparent 70%);
  opacity: 0.56;
}

.lyrics-stage {
  position: relative;
  z-index: 1;
  height: 100%;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.lyrics-head {
  position: relative;
  width: 100%;
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
  padding: 24px 28px 12px;
  text-align: center;
  overflow: hidden;
}

.lyrics-cover-ghost {
  position: absolute;
  top: -18px;
  left: 50%;
  width: 240px;
  height: 128px;
  border-radius: 28px;
  transform: translateX(-50%) perspective(700px) rotateX(16deg) scale(1.04);
  background-position: center;
  background-size: cover;
  opacity: 0.2;
  filter: blur(16px) saturate(1.08);
  pointer-events: none;
}

.lyrics-head > * {
  position: relative;
  z-index: 1;
}

.lyrics-kicker {
  color: var(--mystic);
  font-size: 0.74rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.lyrics-song-block {
  width: min(100%, 520px);
  min-width: 0;
  max-width: min(100%, 520px);
  box-sizing: border-box;
  margin: 12px auto 0;
  padding: 16px 22px 14px;
  border-radius: 24px;
  overflow: hidden;
  background: linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 86%, transparent), color-mix(in srgb, var(--bg-card) 78%, transparent));
  border: 1px solid color-mix(in srgb, var(--divider) 84%, transparent);
  box-shadow:
    0 14px 36px color-mix(in srgb, var(--bg-primary) 12%, transparent),
    inset 0 1px 0 color-mix(in srgb, var(--accent) 8%, transparent);
}

.lyrics-song-name {
  display: block;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: transparent;
  font-family: var(--font-display);
  font-size: clamp(1.3rem, 2vw, 1.8rem);
  line-height: 1.08;
  letter-spacing: 0.01em;
  background-image: linear-gradient(90deg, color-mix(in srgb, var(--accent) 82%, white 16%), color-mix(in srgb, var(--text-primary) 94%, white 6%) 55%, color-mix(in srgb, var(--mystic) 58%, white 10%));
  background-clip: text;
  -webkit-background-clip: text;
  text-shadow: 0 0 18px color-mix(in srgb, var(--accent) 14%, transparent);
}

.lyrics-song-meta {
  max-width: 100%;
  min-width: 0;
  margin-top: 7px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: color-mix(in srgb, var(--text-secondary) 90%, white 6%);
  font-size: 0.86rem;
}

.lyrics-caption {
  margin-top: 10px;
  color: color-mix(in srgb, var(--text-secondary) 92%, white 4%);
  font-size: 0.82rem;
}

.lyrics-panel {
  position: relative;
  height: 100%;
  overflow-y: auto;
  padding: 12px 28px 64px;
  mask-image: linear-gradient(transparent 0%, black 10%, black 88%, transparent 100%);
  opacity: 1;
  transition: opacity 360ms ease, transform 360ms ease;
}

.lyrics-shell.restoring .lyrics-panel {
  opacity: 0.78;
  transform: translateY(4px);
}

.lyrics-panel::before,
.lyrics-panel::after {
  content: '';
  position: sticky;
  top: 0;
  width: 72px;
  height: 100%;
  pointer-events: none;
  z-index: 2;
}

.lyrics-panel::before {
  float: left;
  margin-left: -28px;
  background: linear-gradient(90deg, color-mix(in srgb, var(--bg-card) 90%, transparent), transparent 86%);
}

.lyrics-panel::after {
  float: right;
  margin-right: -28px;
  background: linear-gradient(270deg, color-mix(in srgb, var(--bg-card) 90%, transparent), transparent 86%);
}

.no-lyric-card {
  width: min(100%, 520px);
  margin: 12vh auto 0;
  padding: 28px 24px;
  border-radius: 28px;
  border: 1px solid color-mix(in srgb, var(--divider) 90%, transparent);
  background: color-mix(in srgb, var(--bg-elevated) 84%, transparent);
  box-shadow: 0 20px 56px color-mix(in srgb, var(--bg-primary) 16%, transparent);
  text-align: center;
}

.no-lyric-mark {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 24%, transparent), color-mix(in srgb, var(--mystic) 18%, transparent));
  color: var(--text-primary);
  font-family: var(--font-display);
  font-size: 1.2rem;
}

.no-lyric-title {
  color: var(--text-primary);
  font-size: 1.1rem;
  font-weight: 600;
}

.no-lyric-copy {
  margin-top: 8px;
  color: var(--text-secondary);
  font-size: 0.9rem;
  line-height: 1.8;
}

.lyric-line {
  width: min(100%, 760px);
  margin: 0 auto;
  padding: 16px 22px;
  border-radius: 28px;
  color: color-mix(in srgb, var(--text-tertiary) 88%, transparent);
  text-align: center;
  opacity: 0.42;
  transform: translateY(var(--line-offset-y, 0px)) scale(var(--line-scale, 0.97));
  filter: blur(var(--line-blur, 0px));
  transition:
    color 260ms cubic-bezier(0.4, 0, 0.2, 1),
    transform 360ms cubic-bezier(0.22, 1, 0.36, 1),
    filter 320ms ease,
    text-shadow 320ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 260ms ease,
    background 260ms ease,
    box-shadow 300ms ease;
}

.lyric-line + .lyric-line {
  margin-top: 6px;
}

.lyric-line.nearby {
  opacity: 0.68;
}

.lyric-line.distant {
  opacity: 0.24;
}

.line-main {
  font-family: var(--font-lyric);
  font-size: clamp(1.02rem, 1.2vw, 1.14rem);
  line-height: 1.95;
}

.lyric-line.active {
  --lyric-sweep: 18%;
  color: var(--text-primary);
  opacity: 1;
  transform: translateY(var(--line-offset-y, -2px)) scale(var(--line-scale, 1));
  background: linear-gradient(180deg, color-mix(in srgb, var(--bg-elevated) 92%, transparent), color-mix(in srgb, var(--bg-card) 82%, transparent));
  border: 1px solid color-mix(in srgb, var(--accent) 14%, transparent);
  box-shadow:
    0 18px 48px color-mix(in srgb, var(--bg-primary) 14%, transparent),
    0 0 0 1px color-mix(in srgb, var(--mystic) 6%, transparent),
    inset 0 1px 0 color-mix(in srgb, var(--text-primary) 5%, transparent);
}

.lyric-line.active .line-main {
  font-size: clamp(1.7rem, 2.5vw, 2.35rem);
  font-weight: 700;
  letter-spacing: 0.01em;
  color: transparent;
  background-image: linear-gradient(
    90deg,
    color-mix(in srgb, var(--accent) 76%, white 20%) 0%,
    color-mix(in srgb, var(--text-primary) 92%, white 8%) var(--lyric-sweep),
    color-mix(in srgb, var(--text-primary) 84%, transparent) 100%
  );
  background-clip: text;
  -webkit-background-clip: text;
  text-shadow: 0 0 14px color-mix(in srgb, var(--accent) 24%, transparent);
}

.lyric-line.active::after {
  content: '';
  display: block;
  width: 88px;
  height: 3px;
  margin: 10px auto 0;
  border-radius: 999px;
  background: linear-gradient(90deg, transparent, var(--accent), color-mix(in srgb, var(--mystic) 40%, var(--accent)), transparent);
}

.lyric-line.settling {
  animation: lyric-settle 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.translation {
  margin-top: 6px;
  font-family: var(--font-ui);
  font-size: 0.86rem;
  line-height: 1.75;
  color: var(--text-secondary);
}

.lyric-line.active .translation {
  color: color-mix(in srgb, var(--text-secondary) 88%, white 6%);
}

@keyframes lyric-settle {
  0% {
    opacity: 0.18;
    transform: translateY(10px) scale(0.97);
  }

  100% {
    opacity: 1;
    transform: translateY(-2px) scale(1);
  }
}

@media (max-width: 880px) {
  .lyrics-head {
    padding: 18px 18px 8px;
  }

  .lyrics-cover-ghost {
    width: 190px;
    height: 108px;
    top: -10px;
  }

  .lyrics-song-block {
    width: 100%;
    max-width: 100%;
    padding: 14px 14px 12px;
  }

  .lyrics-song-name {
    font-size: clamp(1.1rem, 5vw, 1.45rem);
  }

  .lyrics-panel {
    padding: 8px 16px 54px;
  }

  .lyrics-panel::before,
  .lyrics-panel::after {
    width: 28px;
  }

  .lyric-line {
    width: 100%;
    padding: 14px 14px;
    border-radius: 22px;
  }

  .lyric-line.active .line-main {
    font-size: clamp(1.4rem, 7vw, 1.9rem);
  }
}
</style>
