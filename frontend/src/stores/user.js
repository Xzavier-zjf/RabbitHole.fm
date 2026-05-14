import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, register as apiRegister, logout as apiLogout, getMe } from '../api'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const profile = ref(null)
  const isLoggedIn = computed(() => !!token.value)

  // Auto-fetch profile on init if already logged in
  if (token.value) {
    fetchMe()
  }

  async function login(username, password) {
    const res = await apiLogin(username, password)
    token.value = res.data.token
    localStorage.setItem('token', token.value)
    await fetchMe()
  }

  async function register(username, password) {
    await apiRegister(username, password)
  }

  async function logoutUser() {
    try {
      await apiLogout()
    } finally {
      token.value = ''
      profile.value = null
      localStorage.removeItem('token')
    }
  }

  async function fetchMe() {
    if (!token.value) return
    try {
      const res = await getMe()
      profile.value = res.data
    } catch {
      profile.value = null
    }
  }

  return { token, profile, isLoggedIn, login, register, logoutUser, fetchMe }
})
