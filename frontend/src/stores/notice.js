import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useNoticeStore = defineStore('notice', () => {
  const visible = ref(false)
  const type = ref('info')
  const title = ref('')
  const message = ref('')
  const requestId = ref('')
  let timer = null

  function show(payload = {}) {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    type.value = payload.type || 'info'
    title.value = payload.title || ''
    message.value = payload.message || ''
    requestId.value = payload.requestId || ''
    visible.value = !!message.value
    if (visible.value) {
      timer = setTimeout(() => {
        visible.value = false
      }, payload.duration || 5000)
    }
  }

  function clear() {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
    visible.value = false
    title.value = ''
    message.value = ''
    requestId.value = ''
  }

  return { visible, type, title, message, requestId, show, clear }
})
