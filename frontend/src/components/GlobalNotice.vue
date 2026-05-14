<template>
  <transition name="notice-fade">
    <div v-if="notice.visible" class="global-notice" :class="notice.type">
      <div class="notice-copy">
        <div v-if="notice.title" class="notice-eyebrow">{{ notice.title }}</div>
        <div class="notice-title">{{ notice.message }}</div>
        <div v-if="notice.requestId" class="notice-meta">Request ID: {{ notice.requestId }}</div>
      </div>
      <button class="notice-close" @click="notice.clear()">×</button>
    </div>
  </transition>
</template>

<script setup>
import { useNoticeStore } from '../stores/notice'

const notice = useNoticeStore()
</script>

<style scoped>
.global-notice {
  position: fixed;
  top: 18px;
  right: 18px;
  z-index: 1200;
  max-width: min(420px, calc(100vw - 36px));
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 94%, transparent);
  box-shadow: var(--shadow-soft);
}

.global-notice.error {
  border-color: color-mix(in srgb, var(--highlight) 38%, var(--divider));
}

.global-notice.warning {
  border-color: color-mix(in srgb, var(--accent) 42%, var(--divider));
}

.global-notice.success {
  border-color: color-mix(in srgb, var(--success) 38%, var(--divider));
}

.global-notice.info {
  border-color: color-mix(in srgb, var(--mystic) 28%, var(--divider));
}

.notice-copy {
  min-width: 0;
  flex: 1;
}

.notice-eyebrow {
  color: var(--mystic);
  font-size: 0.72rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  margin-bottom: 5px;
}

.notice-title {
  color: var(--text-primary);
  font-size: 0.9rem;
  line-height: 1.5;
}

.notice-meta {
  color: var(--text-secondary);
  font-size: 0.75rem;
  margin-top: 4px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.notice-close {
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 1.1rem;
  line-height: 1;
}

.notice-fade-enter-active,
.notice-fade-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.notice-fade-enter-from,
.notice-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
