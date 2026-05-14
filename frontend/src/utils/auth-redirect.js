const REDIRECT_KEY = 'post_login_redirect'

function normalizeRedirect(path) {
  if (!path || typeof path !== 'string') return '/'
  if (!path.startsWith('/')) return '/'
  if (path.startsWith('/login')) return '/'
  return path
}

export function getCurrentAppPath() {
  return `${window.location.pathname || '/'}${window.location.search || ''}${window.location.hash || ''}`
}

function normalizeChannelId(channelId) {
  const parsed = Number(channelId)
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null
}

export function savePostLoginRedirect(path, channelId = null) {
  const payload = {
    path: normalizeRedirect(path),
    channelId: normalizeChannelId(channelId),
  }
  sessionStorage.setItem(REDIRECT_KEY, JSON.stringify(payload))
  return payload
}

function readRedirectPayload() {
  try {
    const raw = sessionStorage.getItem(REDIRECT_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw)
    return {
      path: normalizeRedirect(parsed?.path || '/'),
      channelId: normalizeChannelId(parsed?.channelId),
    }
  } catch {
    return null
  }
}

export function consumePostLoginRedirect(fallback = '/') {
  const saved = readRedirectPayload()
  sessionStorage.removeItem(REDIRECT_KEY)
  return {
    path: saved?.path || normalizeRedirect(fallback),
    channelId: saved?.channelId || null,
  }
}

export function peekPostLoginRedirect(fallback = '/') {
  const saved = readRedirectPayload()
  return {
    path: saved?.path || normalizeRedirect(fallback),
    channelId: saved?.channelId || null,
  }
}
