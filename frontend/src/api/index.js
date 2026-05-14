import axios from 'axios'
import { useNoticeStore } from '../stores/notice'
import router from '../router'
import { getCurrentAppPath, savePostLoginRedirect } from '../utils/auth-redirect'
import { usePlayerStore } from '../stores/player'

function buildNoticePayload(err) {
  const status = err.response?.status
  const requestId = err.response?.headers?.['x-request-id'] || ''
  const serverMsg = err.response?.data?.msg || ''

  if (!err.response) {
    return {
      type: 'warning',
      title: 'Network',
      message: '网络连接不稳定，RabbitHole.fm 暂时没有收到回应。',
      requestId: '',
    }
  }

  if (status === 400) {
    return {
      type: 'warning',
      title: 'Check Input',
      message: serverMsg || '提交的信息还不完整，请检查后再试。',
      requestId: '',
    }
  }

  if (status === 404) {
    return {
      type: 'info',
      title: 'Not Found',
      message: serverMsg || '你打开的接口或内容已经找不到了。',
      requestId,
    }
  }

  if (status >= 500) {
    return {
      type: 'error',
      title: 'Server Busy',
      message: serverMsg || '兔子洞里刚刚有点拥挤，请稍后再试。',
      requestId,
    }
  }

  return {
    type: 'error',
    title: 'Request Failed',
    message: serverMsg || '这次操作没有成功，请稍后再试。',
    requestId,
  }
}

const api = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      const currentPath = router.currentRoute.value?.fullPath || getCurrentAppPath()
      const player = usePlayerStore()
      const redirect = currentPath.startsWith('/login')
        ? { path: '/', channelId: player.currentChannelId || null }
        : savePostLoginRedirect(currentPath, player.currentChannelId)
      const notice = useNoticeStore()
      notice.show({
        type: 'warning',
        title: 'Login Required',
        message: '登录状态已失效，请重新进入兔子洞。',
        duration: 2200,
      })
      localStorage.removeItem('token')
      setTimeout(() => {
        const query = {}
        if (redirect.path && redirect.path !== '/') {
          query.redirect = redirect.path
        }
        if (redirect.channelId) {
          query.channelId = String(redirect.channelId)
        }
        router.push({ path: '/login', query })
      }, 250)
      return Promise.reject(err)
    }
    const notice = useNoticeStore()
    notice.show(buildNoticePayload(err))
    return Promise.reject(err)
  }
)

// Auth
export const login = (username, password) => api.post('/user/login', { username, password })
export const register = (username, password) => api.post('/user/register', { username, password })
export const logout = () => api.post('/user/logout')
export const getMe = () => api.get('/user/me')

// Music
export const searchSongs = (keywords, limit = 30) => api.get('/music/search', { params: { keywords, limit } })
export const getSongDetail = (id) => api.get(`/music/song/${id}`)
export const getMusicApiStatus = () => api.get('/music/status')

// Radio
export const loadChannel = (playlistId) => api.get(`/radio/channel/${playlistId}`)
export const getSongData = (id) => api.get(`/radio/song/${id}`)
export const getDjAudioUrl = (prevId, nextId) =>
  `/api/radio/dj?prevId=${prevId ?? ''}&nextId=${nextId}`
export const getDjAudio = (url) => api.get(url.replace(/^\/api/, ''), { responseType: 'blob' })
export const popNext = (channelId) => api.post('/radio/next', null, { params: { channelId } })

// Favorites
export const addFavorite = (songId, data) => api.post(`/user/favorite/${songId}`, data)
export const removeFavorite = (songId) => api.delete(`/user/favorite/${songId}`)
export const getFavorites = () => api.get('/user/favorites')

// History
export const recordPlay = (data) => api.post('/user/history/record', data)
export const getHistory = (days = 7) => api.get('/user/history', { params: { days } })

// Song Request (点歌)
export const submitSongRequest = (data) => api.post('/request', data)
export const getRequestQueue = (channelId) => api.get(`/request/queue/${channelId}`)
export const cancelRequest = (id) => api.delete(`/request/${id}`)
export const getMyRequests = (params = 20) => {
  if (typeof params === 'number') {
    return api.get('/request/my', { params: { limit: params } })
  }
  return api.get('/request/my', { params })
}

// Profile
export const updateProfile = (data) => api.put('/user/profile', data)
export const uploadAvatar = (formData) => api.post('/user/avatar', formData, {
  headers: { 'Content-Type': 'multipart/form-data' },
})

// Channel Favorites
export const addChannelFavorite = (data) => api.post('/user/channel/favorite', data)
export const removeChannelFavorite = (channelId) => api.delete(`/user/channel/favorite/${channelId}`)
export const getChannelFavorites = () => api.get('/user/channel/favorites')

export const proxyCoverUrl = (url) => {
  if (!url) return ''
  return `/api/music/cover?url=${encodeURIComponent(url)}`
}

export default api
