import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { loadChannel, getSongData, getDjAudio, recordPlay } from '../api'

const LOCAL_HISTORY_KEY = 'rabbithole:play-history'
const LAST_CHANNEL_KEY = 'rabbithole:last-channel-id'
const PLAYBACK_CONTEXT_KEY = 'rabbithole:playback-context'

function readLocalHistory() {
  try {
    const parsed = JSON.parse(localStorage.getItem(LOCAL_HISTORY_KEY) || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function persistLocalHistory(entry) {
  try {
    const existing = readLocalHistory()
    const next = [entry, ...existing].slice(0, 120)
    localStorage.setItem(LOCAL_HISTORY_KEY, JSON.stringify(next))
  } catch {
    // Ignore local history persistence failure.
  }
}

function readLastChannelId() {
  try {
    const raw = localStorage.getItem(LAST_CHANNEL_KEY)
    const parsed = Number(raw)
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null
  } catch {
    return null
  }
}

function persistLastChannelId(channelId) {
  try {
    if (channelId) {
      localStorage.setItem(LAST_CHANNEL_KEY, String(channelId))
    }
  } catch {
    // Ignore channel persistence failure.
  }
}

function readPlaybackContext() {
  try {
    const parsed = JSON.parse(sessionStorage.getItem(PLAYBACK_CONTEXT_KEY) || 'null')
    if (!parsed || typeof parsed !== 'object') return null
    return {
      channelId: Number.isFinite(Number(parsed.channelId)) ? Number(parsed.channelId) : null,
      currentIndex: Number.isFinite(Number(parsed.currentIndex)) ? Number(parsed.currentIndex) : 0,
      currentTime: Number.isFinite(Number(parsed.currentTime)) ? Number(parsed.currentTime) : 0,
      songId: Number.isFinite(Number(parsed.songId)) ? Number(parsed.songId) : null,
      requestId: Number.isFinite(Number(parsed.requestId)) ? Number(parsed.requestId) : null,
      type: typeof parsed.type === 'string' ? parsed.type : 'song',
      showLyrics: !!parsed.showLyrics,
      updatedAt: Number.isFinite(Number(parsed.updatedAt)) ? Number(parsed.updatedAt) : 0,
    }
  } catch {
    return null
  }
}

export const usePlayerStore = defineStore('player', () => {
  const queue = ref([])
  const currentIndex = ref(0)
  const currentChannelId = ref(readLastChannelId())
  const isPlaying = ref(false)
  const audio = ref(null)
  const currentItem = computed(() => queue.value[currentIndex.value] || null)
  const progress = ref(0)
  const duration = ref(0)
  const currentTime = ref(0)
  const isLoading = ref(false)
  const error = ref('')
  const viewState = ref({ showLyrics: false })
  let activeDjBlobUrl = null
  let refillPending = false
  let lastContextPersistAt = 0

  function initAudio() {
    if (audio.value) return
    audio.value = new Audio()

    audio.value.addEventListener('timeupdate', () => {
      currentTime.value = audio.value.currentTime
      duration.value = audio.value.duration || 0
      progress.value = duration.value > 0 ? audio.value.currentTime / duration.value : 0
      persistPlaybackContext(false)
    })

    audio.value.addEventListener('ended', () => {
      persistPlaybackContext(true)
      next()
    })

    audio.value.addEventListener('error', () => {
      // Skip broken items and remove them
      const broken = queue.value[currentIndex.value]
      if (broken) broken.broken = true
      persistPlaybackContext(true)
      next()
    })

    audio.value.addEventListener('pause', () => {
      persistPlaybackContext(true)
    })
  }

  function persistPlaybackContext(force = false) {
    const now = Date.now()
    if (!force && now - lastContextPersistAt < 1500) {
      return
    }
    lastContextPersistAt = now
    const item = currentItem.value
    const payload = {
      channelId: currentChannelId.value,
      currentIndex: currentIndex.value,
      currentTime: currentTime.value,
      songId: item?.songId ?? null,
      requestId: item?.requestId ?? null,
      type: item?.type ?? 'song',
      showLyrics: !!viewState.value.showLyrics,
      updatedAt: now,
    }
    try {
      sessionStorage.setItem(PLAYBACK_CONTEXT_KEY, JSON.stringify(payload))
    } catch {
      // Ignore playback context persistence failure.
    }
  }

  function getSavedPlaybackContext() {
    const snapshot = readPlaybackContext()
    if (!snapshot) return null
    if (snapshot.updatedAt && Date.now() - snapshot.updatedAt > 1000 * 60 * 60 * 6) {
      return null
    }
    return snapshot
  }

  function applyResumeTime(seconds) {
    if (!audio.value || !Number.isFinite(seconds) || seconds <= 0) return
    const safeTime = Math.max(0, seconds)
    const apply = () => {
      if (!audio.value) return
      const total = Number.isFinite(audio.value.duration) ? audio.value.duration : 0
      audio.value.currentTime = total > 1 ? Math.min(safeTime, Math.max(total - 0.5, 0)) : safeTime
    }
    if (audio.value.readyState >= 1) {
      apply()
      return
    }
    const onReady = () => {
      audio.value?.removeEventListener('loadedmetadata', onReady)
      apply()
    }
    audio.value.addEventListener('loadedmetadata', onReady)
  }

  function revokeDjBlobUrl() {
    if (activeDjBlobUrl) {
      URL.revokeObjectURL(activeDjBlobUrl)
      activeDjBlobUrl = null
    }
  }

  function decodeBase64Utf8(base64) {
    const binary = atob(base64)
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0))
    return new TextDecoder().decode(bytes)
  }

  async function buildQueue(channelId, options = {}) {
    const { resumeContext = null } = options
    isLoading.value = true
    error.value = ''
    currentChannelId.value = channelId
    persistLastChannelId(channelId)
    try {
      const res = await loadChannel(channelId)
      queue.value = res.data
      if (queue.value.length > 0) {
        const resumeIndex = resolveResumeIndex(queue.value, resumeContext)
        const resumeTime = resumeIndex >= 0 && resumeContext?.channelId === channelId ? resumeContext.currentTime : 0
        currentIndex.value = resumeIndex >= 0 ? resumeIndex : 0
        await playItem(currentIndex.value, { resumeTime })
      } else {
        currentIndex.value = 0
        error.value = '该频道没有可播放的歌曲，请尝试其他频道'
      }
    } catch (e) {
      error.value = e.response?.data?.msg || '加载频道失败，请确认后端、网易云 API 与 Redis 已启动'
    } finally {
      isLoading.value = false
    }
  }

  async function playItem(index, options = {}) {
    const { resumeTime = 0 } = options
    if (index < 0 || index >= queue.value.length) return
    currentIndex.value = index
    const item = queue.value[index]

    // Refill when near end of queue (only once, with guard)
    if (index >= queue.value.length - 5 && !refillPending) {
      refillPending = true
      refillQueue().finally(() => { refillPending = false })
    }

    initAudio()
    revokeDjBlobUrl()

    if (item.type === 'song') {
      if (!item.songUrl) {
        await fetchSongUrlWithRetry(item.songId)
        if (!item.songUrl) {
          item.broken = true
          next()
          return
        }
      }
      audio.value.src = item.songUrl
    } else if (item.type === 'dj') {
      item.djSubtitle = item.djSubtitle || ''
      try {
        const res = await getDjAudio(item.djUrl)
        const subtitleBase64 = res.headers['x-dj-subtitle-base64']
        if (subtitleBase64) {
          item.djSubtitle = decodeBase64Utf8(subtitleBase64)
        }
        activeDjBlobUrl = URL.createObjectURL(res.data)
        audio.value.src = activeDjBlobUrl
      } catch {
        item.broken = true
        next()
        return
      }
    }

    applyResumeTime(resumeTime)
    audio.value.play().catch(() => {})
    isPlaying.value = true
    recordHistory(item)
    preloadNextSong()
    persistPlaybackContext(true)
  }

  async function fetchSongUrlWithRetry(songId) {
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        await fetchSongUrl(songId)
        const item = queue.value[currentIndex.value]
        if (item && item.songId === songId && item.songUrl) return
      } catch { /* retry */ }
      if (attempt < 2) {
        await new Promise(r => setTimeout(r, 1500))
      }
    }
  }

  function recordHistory(item) {
    if (item.type !== 'song' || !item.songId) return
    const entry = {
      id: `${item.songId}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      songId: item.songId,
      songName: item.name || '',
      artists: (item.artists || []).join(' / '),
      channelId: currentChannelId.value,
      playedAt: new Date().toISOString(),
    }
    persistLocalHistory(entry)
    recordPlay({
      songId: item.songId,
      songName: item.name || '',
      artists: (item.artists || []).join(' / '),
      channelId: currentChannelId.value,
    }).catch(() => {})
  }

  async function refillQueue() {
    if (!currentChannelId.value) return
    try {
      const res = await loadChannel(currentChannelId.value)
      const newItems = res.data || []

      // Collect IDs of all items already in queue to avoid duplicates
      const existingKeys = new Set(
        queue.value.map(item => `${item.type}:${item.songId || item.djUrl || ''}`)
      )

      const trulyNew = newItems.filter(item => {
        const key = `${item.type}:${item.songId || item.djUrl || ''}`
        return !existingKeys.has(key)
      })

      if (trulyNew.length > 0) {
        queue.value.push(...trulyNew)
      }
    } catch {
      // Silent fail - queue just runs out
    }
  }

  async function fetchSongUrl(songId) {
    try {
      const res = await getSongData(songId)
      const item = queue.value[currentIndex.value]
      if (item && item.songId === songId) {
        item.songUrl = res.data.url
        item.lyric = res.data.lyric
      }
    } catch {
      // URL will remain null, item will be skipped
    }
  }

  function preloadNextSong() {
    const startIdx = currentIndex.value + 1
    const endIdx = Math.min(startIdx + 2, queue.value.length)
    for (let i = startIdx; i < endIdx; i++) {
      const item = queue.value[i]
      if (item && item.type === 'song' && !item.songUrl) {
        getSongData(item.songId).then(res => {
          item.songUrl = res.data.url
          item.lyric = res.data.lyric
        }).catch(() => {})
      }
    }
  }

  function addToQueue(song) {
    queue.value.push({
      type: 'song',
      songId: song.id,
      name: song.name,
      artists: song.artists,
      album: song.album,
      coverUrl: song.coverUrl,
      durationMs: song.durationMs,
    })
  }

  function insertAt(items, position) {
    const arr = Array.isArray(items) ? items : [items]
    const idx = Math.min(position, queue.value.length)
    queue.value.splice(idx, 0, ...arr)
  }

  function play() {
    initAudio()
    audio.value.play().catch(() => {})
    isPlaying.value = true
  }

  function pause() {
    if (audio.value) {
      audio.value.pause()
    }
    isPlaying.value = false
  }

  function togglePlay() {
    if (isPlaying.value) pause()
    else play()
  }

  function next() {
    const nextIdx = currentIndex.value + 1
    if (nextIdx < queue.value.length) {
      playItem(nextIdx)
    }
  }

  function prev() {
    const prevIdx = currentIndex.value - 1
    if (prevIdx >= 0) {
      playItem(prevIdx)
    }
  }

  function seek(fraction) {
    if (audio.value && duration.value > 0) {
      audio.value.currentTime = fraction * duration.value
    }
  }

  function setVolume(v) {
    if (audio.value) {
      audio.value.volume = v
    }
  }

  function setCurrentChannelId(channelId) {
    currentChannelId.value = channelId || null
    persistLastChannelId(currentChannelId.value)
    persistPlaybackContext(true)
  }

  function setPlaybackViewState(patch = {}) {
    viewState.value = {
      ...viewState.value,
      ...patch,
    }
    persistPlaybackContext(true)
  }

  function resolveResumeIndex(items, resumeContext) {
    if (!resumeContext || resumeContext.channelId !== currentChannelId.value) {
      return -1
    }
    if (resumeContext.requestId != null) {
      const byRequestId = items.findIndex((item) => item.requestId === resumeContext.requestId)
      if (byRequestId >= 0) return byRequestId
    }
    if (resumeContext.songId != null) {
      const bySongId = items.findIndex((item) => item.songId === resumeContext.songId && item.type === resumeContext.type)
      if (bySongId >= 0) return bySongId
    }
    if (resumeContext.currentIndex >= 0 && resumeContext.currentIndex < items.length) {
      return resumeContext.currentIndex
    }
    return -1
  }

  function getPlaybackViewState() {
    return { ...viewState.value }
  }

  return {
    queue, currentIndex, currentChannelId, isPlaying, currentItem,
    progress, duration, currentTime, audio, isLoading, error,
    buildQueue, addToQueue, insertAt, play, pause, togglePlay, next, prev,
    seek, setVolume, playItem, setCurrentChannelId,
    setPlaybackViewState, getSavedPlaybackContext, getPlaybackViewState,
  }
})
