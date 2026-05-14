import { ref, watch } from 'vue'

const STORAGE_KEY = 'rabbithole-theme'
const theme = ref('dark')
let initialized = false

function resolveInitialTheme() {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'dark' || saved === 'light') {
    return saved
  }
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

function applyTheme(value) {
  document.documentElement.setAttribute('data-theme', value)
  localStorage.setItem(STORAGE_KEY, value)
}

export function useTheme() {
  if (!initialized && typeof window !== 'undefined') {
    theme.value = resolveInitialTheme()
    applyTheme(theme.value)
    watch(theme, applyTheme)
    initialized = true
  }

  function toggleTheme() {
    theme.value = theme.value === 'dark' ? 'light' : 'dark'
  }

  return {
    theme,
    toggleTheme,
  }
}
