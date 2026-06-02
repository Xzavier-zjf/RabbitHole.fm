import { defineStore } from 'pinia'
import { ref } from 'vue'

const QUEUE_LIMIT = 24

export const useRequestFeedStore = defineStore('request-feed', () => {
  const version = ref(0)
  const queueByChannel = ref({})
  const manualOrderByChannel = ref({})

  function normalizeChannelId(channelId) {
    return String(channelId || '')
  }

  function requestKeyFromId(requestId) {
    return `request:${requestId}`
  }

  function queueItemKey(item) {
    if (!item || typeof item !== 'object') return ''
    if (item.requestId != null && item.requestId !== '') return requestKeyFromId(item.requestId)
    if (item.id != null && item.id !== '') return requestKeyFromId(item.id)
    if (item.songId != null && item.songId !== '') {
      return [
        'song',
        item.songId,
        item.requester || '',
        item.message || '',
        item.createdAt || item.requestedAt || '',
      ].join(':')
    }
    if (item.djUrl) return ['dj', item.djUrl, item.name || '', item.requester || ''].join(':')
    return [
      'queue',
      item.type || '',
      item.name || '',
      item.requester || '',
      item.createdAt || item.requestedAt || '',
    ].join(':')
  }

  function dedupeQueue(items = []) {
    const source = Array.isArray(items) ? items.filter(Boolean) : []
    const seen = new Set()
    return source.filter((item) => {
      const key = queueItemKey(item)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
  }

  function applyManualOrder(channelKey, items) {
    const manualOrder = manualOrderByChannel.value[channelKey] || []
    if (!manualOrder.length || !items.length) return items

    const orderIndex = new Map(manualOrder.map((key, index) => [key, index]))
    return items
      .map((item, index) => ({
        item,
        index,
        key: queueItemKey(item),
      }))
      .sort((a, b) => {
        const aHasOrder = orderIndex.has(a.key)
        const bHasOrder = orderIndex.has(b.key)
        if (aHasOrder && bHasOrder) return orderIndex.get(a.key) - orderIndex.get(b.key)
        if (aHasOrder) return -1
        if (bHasOrder) return 1
        return a.index - b.index
      })
      .map(({ item }) => item)
  }

  function rememberManualOrder(channelKey, items) {
    manualOrderByChannel.value = {
      ...manualOrderByChannel.value,
      [channelKey]: items.map(queueItemKey).filter(Boolean),
    }
  }

  function pruneManualOrder(channelKey, items) {
    const manualOrder = manualOrderByChannel.value[channelKey] || []
    if (!manualOrder.length) return

    const currentKeys = new Set(items.map(queueItemKey))
    const nextOrder = manualOrder.filter((key) => currentKeys.has(key))
    const nextByChannel = { ...manualOrderByChannel.value }
    if (nextOrder.length) {
      nextByChannel[channelKey] = nextOrder
    } else {
      delete nextByChannel[channelKey]
    }
    manualOrderByChannel.value = nextByChannel
  }

  function setChannelQueue(channelKey, items) {
    queueByChannel.value = {
      ...queueByChannel.value,
      [channelKey]: items,
    }
  }

  function getQueue(channelId) {
    return queueByChannel.value[normalizeChannelId(channelId)] || []
  }

  function setQueue(channelId, items) {
    const key = normalizeChannelId(channelId)
    const nextQueue = applyManualOrder(key, dedupeQueue(items || []).slice(0, QUEUE_LIMIT))
    setChannelQueue(key, nextQueue)
    pruneManualOrder(key, nextQueue)
    bump()
  }

  function addRequest(channelId, item) {
    if (!item) return
    const key = normalizeChannelId(channelId)
    const current = queueByChannel.value[key] || []
    setChannelQueue(key, dedupeQueue([item, ...current]).slice(0, QUEUE_LIMIT))
    bump()
  }

  function replaceRequest(channelId, tempRequestId, item) {
    if (!item) {
      removeRequest(channelId, tempRequestId)
      return
    }
    const key = normalizeChannelId(channelId)
    const current = (queueByChannel.value[key] || []).filter((entry) => entry.requestId !== tempRequestId)
    const oldKey = requestKeyFromId(tempRequestId)
    const newKey = queueItemKey(item)
    const manualOrder = manualOrderByChannel.value[key] || []
    if (manualOrder.includes(oldKey) && newKey) {
      manualOrderByChannel.value = {
        ...manualOrderByChannel.value,
        [key]: manualOrder.map((entryKey) => entryKey === oldKey ? newKey : entryKey),
      }
    }
    const nextQueue = applyManualOrder(key, dedupeQueue([item, ...current]).slice(0, QUEUE_LIMIT))
    setChannelQueue(key, nextQueue)
    bump()
  }

  function removeRequest(channelId, requestId) {
    const key = normalizeChannelId(channelId)
    const current = queueByChannel.value[key] || []
    const nextQueue = current.filter((item) => item.requestId !== requestId)
    setChannelQueue(key, nextQueue)
    pruneManualOrder(key, nextQueue)
    bump()
  }

  function moveRequest(channelId, item, toIndex) {
    const key = normalizeChannelId(channelId)
    const current = queueByChannel.value[key] || []
    const itemKey = typeof item === 'string' ? item : queueItemKey(item)
    const fromIndex = current.findIndex((entry) => queueItemKey(entry) === itemKey)
    if (fromIndex < 0) return false

    const lastIndex = current.length - 1
    const safeToIndex = Math.min(lastIndex, Math.max(0, toIndex))
    if (fromIndex === safeToIndex) return false

    const nextQueue = [...current]
    const [target] = nextQueue.splice(fromIndex, 1)
    nextQueue.splice(safeToIndex, 0, target)
    setChannelQueue(key, nextQueue)
    rememberManualOrder(key, nextQueue)
    bump()
    return true
  }

  function promoteRequest(channelId, item) {
    return moveRequest(channelId, item, 0)
  }

  function delayRequest(channelId, item) {
    const key = normalizeChannelId(channelId)
    const current = queueByChannel.value[key] || []
    return moveRequest(channelId, item, current.length - 1)
  }

  function bump() {
    version.value += 1
  }

  return {
    version,
    queueByChannel,
    manualOrderByChannel,
    getQueue,
    setQueue,
    addRequest,
    replaceRequest,
    removeRequest,
    promoteRequest,
    delayRequest,
    bump,
  }
})
