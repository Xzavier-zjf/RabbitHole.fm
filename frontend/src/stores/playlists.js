import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

const PLAYLISTS_KEY = 'rabbithole:playlists'
const DEFAULT_PLAYLIST_NAME = '我的歌单'

function readPlaylists() {
  try {
    const parsed = JSON.parse(localStorage.getItem(PLAYLISTS_KEY) || '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.map(normalizePlaylist).filter(Boolean)
  } catch {
    return []
  }
}

function persistPlaylists(playlists) {
  try {
    localStorage.setItem(PLAYLISTS_KEY, JSON.stringify(playlists))
  } catch {
    // Ignore local playlist persistence failure.
  }
}

function normalizePlaylist(playlist) {
  if (!playlist || typeof playlist !== 'object') return null
  const tracks = []
  const seen = new Set()
  if (Array.isArray(playlist.tracks)) {
    playlist.tracks.map(normalizeTrack).filter(Boolean).forEach((track) => {
      if (seen.has(track.trackKey)) return
      seen.add(track.trackKey)
      tracks.push(track)
    })
  }
  const id = playlist.id || createId()
  return {
    id,
    name: playlist.name || DEFAULT_PLAYLIST_NAME,
    createdAt: playlist.createdAt || new Date().toISOString(),
    updatedAt: playlist.updatedAt || playlist.createdAt || new Date().toISOString(),
    tracks,
  }
}

function normalizeTrack(track) {
  if (!track || typeof track !== 'object') return null
  const songId = track.songId ?? track.id
  if (!songId) return null
  const artists = Array.isArray(track.artists)
    ? track.artists
    : String(track.artists || '')
        .split(/[、/]/)
        .map((name) => name.trim())
        .filter(Boolean)
  const source = track.source || 'netease'
  const normalized = {
    songId,
    name: track.name || track.songName || '未命名歌曲',
    artists,
    album: track.album || '',
    coverUrl: track.coverUrl || '',
    durationMs: track.durationMs || null,
    songUrl: track.songUrl || '',
    lyric: track.lyric || null,
    source,
    sourceLabel: track.sourceLabel || (source === 'netease' ? '网易云' : source),
    addedAt: track.addedAt || new Date().toISOString(),
  }
  return {
    ...normalized,
    trackKey: createTrackKey(normalized),
  }
}

function createId() {
  return `pl-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function createTrackKey(track) {
  const source = track?.source || 'netease'
  const songId = track?.songId ?? track?.id
  return `${source}:${String(songId)}`
}

function createTrackKeyFromRef(trackRef, fallbackSource = 'netease') {
  if (trackRef && typeof trackRef === 'object') {
    return createTrackKey(trackRef)
  }
  return `${fallbackSource}:${String(trackRef)}`
}

export const usePlaylistsStore = defineStore('playlists', () => {
  const playlists = ref(readPlaylists())

  if (playlists.value.length === 0) {
    playlists.value = [{
      id: createId(),
      name: DEFAULT_PLAYLIST_NAME,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      tracks: [],
    }]
    persist()
  }

  const totalTracks = computed(() => {
    return playlists.value.reduce((total, playlist) => total + playlist.tracks.length, 0)
  })

  function persist() {
    persistPlaylists(playlists.value)
  }

  function createPlaylist(name = DEFAULT_PLAYLIST_NAME) {
    const trimmed = name.trim() || DEFAULT_PLAYLIST_NAME
    const playlist = {
      id: createId(),
      name: trimmed,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      tracks: [],
    }
    playlists.value = [playlist, ...playlists.value]
    persist()
    return playlist
  }

  function renamePlaylist(id, name) {
    const trimmed = name.trim()
    if (!trimmed) return
    playlists.value = playlists.value.map((playlist) =>
      playlist.id === id
        ? { ...playlist, name: trimmed, updatedAt: new Date().toISOString() }
        : playlist
    )
    persist()
  }

  function deletePlaylist(id) {
    if (playlists.value.length <= 1) return
    playlists.value = playlists.value.filter((playlist) => playlist.id !== id)
    persist()
  }

  function addTrack(playlistId, track) {
    const normalized = normalizeTrack(track)
    if (!normalized) return false
    let added = false
    playlists.value = playlists.value.map((playlist) => {
      if (playlist.id !== playlistId) return playlist
      const exists = playlist.tracks.some((item) => item.trackKey === normalized.trackKey)
      if (exists) return playlist
      added = true
      return {
        ...playlist,
        updatedAt: new Date().toISOString(),
        tracks: [normalized, ...playlist.tracks],
      }
    })
    if (added) persist()
    return added
  }

  function addTrackToFirst(track) {
    const first = playlists.value[0] || createPlaylist()
    return addTrack(first.id, track)
  }

  function removeTrack(playlistId, trackRef) {
    const key = createTrackKeyFromRef(trackRef)
    playlists.value = playlists.value.map((playlist) => {
      if (playlist.id !== playlistId) return playlist
      return {
        ...playlist,
        updatedAt: new Date().toISOString(),
        tracks: playlist.tracks.filter((track) => track.trackKey !== key),
      }
    })
    persist()
  }

  function reorderTrack(playlistId, fromIndex, toIndex) {
    const sourceIndex = Number(fromIndex)
    const targetIndex = Number(toIndex)
    if (!Number.isInteger(sourceIndex) || !Number.isInteger(targetIndex)) return false

    let changed = false
    playlists.value = playlists.value.map((playlist) => {
      if (playlist.id !== playlistId) return playlist
      const lastIndex = playlist.tracks.length - 1
      if (sourceIndex < 0 || sourceIndex > lastIndex) return playlist
      const safeTargetIndex = Math.min(lastIndex, Math.max(0, targetIndex))
      if (sourceIndex === safeTargetIndex) return playlist

      const tracks = [...playlist.tracks]
      const [track] = tracks.splice(sourceIndex, 1)
      tracks.splice(safeTargetIndex, 0, track)
      changed = true
      return {
        ...playlist,
        updatedAt: new Date().toISOString(),
        tracks,
      }
    })

    if (changed) persist()
    return changed
  }

  function moveTrack(playlistId, trackRef, direction) {
    const playlist = getPlaylist(playlistId)
    if (!playlist) return false
    const key = createTrackKeyFromRef(trackRef)
    const index = playlist.tracks.findIndex((track) => track.trackKey === key)
    if (index < 0) return false
    return reorderTrack(playlistId, index, index + direction)
  }

  function getPlaylist(id) {
    return playlists.value.find((playlist) => playlist.id === id) || playlists.value[0] || null
  }

  return {
    playlists,
    totalTracks,
    createPlaylist,
    renamePlaylist,
    deletePlaylist,
    addTrack,
    addTrackToFirst,
    removeTrack,
    reorderTrack,
    moveTrack,
    getPlaylist,
    trackKey: createTrackKey,
  }
})
