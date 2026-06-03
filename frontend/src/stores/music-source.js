import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { getMusicApiStatus } from '../api'

export const MUSIC_SOURCE_OPTIONS = [
  { key: 'all', label: '全部来源' },
  { key: 'netease', label: '网易云' },
  { key: 'free-api', label: 'Free API' },
]

const SOURCE_KEY = 'rabbithole:music-source'
const VALID_SOURCES = new Set(MUSIC_SOURCE_OPTIONS.map((option) => option.key))

function readSource() {
  try {
    const value = localStorage.getItem(SOURCE_KEY)
    return VALID_SOURCES.has(value) ? value : 'all'
  } catch {
    return 'all'
  }
}

function persistSource(value) {
  try {
    localStorage.setItem(SOURCE_KEY, value)
  } catch {
    // Ignore local persistence failure.
  }
}

export const useMusicSourceStore = defineStore('musicSource', () => {
  const selectedSource = ref(readSource())
  const sourceStatuses = ref({})
  const statusLoaded = ref(false)
  const options = MUSIC_SOURCE_OPTIONS
  const selectedLabel = computed(() =>
    options.find((option) => option.key === selectedSource.value)?.label || '全部来源'
  )
  const availableOptions = computed(() =>
    options.map((option) => ({
      ...option,
      available: option.key === 'all' || sourceStatuses.value[option.key]?.alive !== false,
      enabled: option.key === 'all' || sourceStatuses.value[option.key]?.enabled !== false,
    }))
  )

  function setSource(source) {
    selectedSource.value = VALID_SOURCES.has(source) ? source : 'all'
    persistSource(selectedSource.value)
  }

  async function loadStatus() {
    try {
      const res = await getMusicApiStatus()
      const nextStatuses = {}
      for (const source of res.data?.sources || []) {
        if (source?.key) {
          nextStatuses[source.key] = source
        }
      }
      sourceStatuses.value = nextStatuses
    } catch {
      sourceStatuses.value = {}
    } finally {
      statusLoaded.value = true
    }
  }

  function sourceStatus(source) {
    return sourceStatuses.value[source] || null
  }

  return {
    options,
    availableOptions,
    selectedSource,
    selectedLabel,
    sourceStatuses,
    statusLoaded,
    setSource,
    loadStatus,
    sourceStatus,
  }
})
