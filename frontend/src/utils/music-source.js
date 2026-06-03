export function musicSource(item) {
  return item?.source || 'netease'
}

export function musicSourceLabel(item) {
  const source = musicSource(item)
  if (source === 'netease') return ''
  return item?.sourceLabel || sourceLabelFromKey(source)
}

export function sourceLabelFromKey(source) {
  if (source === 'free-api') return 'Free API'
  if (source === 'netease') return '网易云'
  return source
}

export function sourceSongId(item) {
  const id = item?.sourceSongId ?? item?.songId ?? item?.id
  return id == null ? '' : String(id)
}

export function musicKey(item) {
  return `${musicSource(item)}:${sourceSongId(item)}`
}

export function sourcePayload(item) {
  if (!item) return ''
  if (typeof item.sourcePayload === 'string') return item.sourcePayload
  try {
    return JSON.stringify(item)
  } catch {
    return ''
  }
}

export function favoritePayload(item) {
  return {
    source: musicSource(item),
    sourceLabel: item?.sourceLabel || sourceLabelFromKey(musicSource(item)),
    sourceSongId: sourceSongId(item),
    songName: item?.name || item?.songName || '',
    artists: Array.isArray(item?.artists) ? item.artists.join(' / ') : item?.artists || '',
    coverUrl: item?.coverUrl || '',
    songUrl: item?.songUrl || '',
    sourcePayload: sourcePayload(item),
  }
}

export function requestPayload(song, channelId, message) {
  return {
    channelId,
    songId: song?.id ?? song?.songId,
    source: musicSource(song),
    sourceLabel: song?.sourceLabel || sourceLabelFromKey(musicSource(song)),
    sourceSongId: sourceSongId(song),
    songName: song?.name || song?.songName || '',
    artists: Array.isArray(song?.artists) ? song.artists.join(' / ') : song?.artists || '',
    album: song?.album || '',
    coverUrl: song?.coverUrl || '',
    durationMs: song?.durationMs || null,
    songUrl: song?.songUrl || '',
    sourcePayload: sourcePayload(song),
    message,
  }
}
