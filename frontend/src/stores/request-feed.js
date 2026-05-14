import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useRequestFeedStore = defineStore('request-feed', () => {
  const version = ref(0)
  const queueByChannel = ref({})

  function normalizeChannelId(channelId) {
    return String(channelId || '')
  }

  function dedupeQueue(items = []) {
    const seen = new Set()
    return items.filter((item) => {
      const key = [
        item?.requestId ?? '',
        item?.songId ?? '',
        item?.type ?? '',
        item?.djUrl ?? '',
        item?.name ?? '',
      ].join('|')
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
  }

  function getQueue(channelId) {
    return queueByChannel.value[normalizeChannelId(channelId)] || []
  }

  function setQueue(channelId, items) {
    const key = normalizeChannelId(channelId)
    queueByChannel.value = {
      ...queueByChannel.value,
      [key]: dedupeQueue(items || []).slice(0, 10),
    }
    bump()
  }

  function addRequest(channelId, item) {
    const key = normalizeChannelId(channelId)
    const current = queueByChannel.value[key] || []
    queueByChannel.value = {
      ...queueByChannel.value,
      [key]: dedupeQueue([item, ...current]).slice(0, 10),
    }
    bump()
  }

  function replaceRequest(channelId, tempRequestId, item) {
    const key = normalizeChannelId(channelId)
    const current = (queueByChannel.value[key] || []).filter((entry) => entry.requestId !== tempRequestId)
    queueByChannel.value = {
      ...queueByChannel.value,
      [key]: dedupeQueue([item, ...current]).slice(0, 10),
    }
    bump()
  }

  function removeRequest(channelId, requestId) {
    const key = normalizeChannelId(channelId)
    const current = queueByChannel.value[key] || []
    queueByChannel.value = {
      ...queueByChannel.value,
      [key]: current.filter((item) => item.requestId !== requestId),
    }
    bump()
  }

  function bump() {
    version.value += 1
  }

  return {
    version,
    queueByChannel,
    getQueue,
    setQueue,
    addRequest,
    replaceRequest,
    removeRequest,
    bump,
  }
})
