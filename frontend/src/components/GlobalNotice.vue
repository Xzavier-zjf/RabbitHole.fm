<template>
  <transition name="notice-fade">
    <div v-if="notice.visible" class="global-notice" :class="notice.type">
      <div class="notice-icon" aria-hidden="true">
        <AlertCircle v-if="notice.type === 'error'" :size="18" />
        <TriangleAlert v-else-if="notice.type === 'warning'" :size="18" />
        <CheckCircle2 v-else-if="notice.type === 'success'" :size="18" />
        <Info v-else :size="18" />
      </div>
      <div class="notice-copy">
        <div v-if="notice.title" class="notice-eyebrow">{{ notice.title }}</div>
        <div class="notice-title">{{ notice.message }}</div>
        <div v-if="notice.requestId" class="notice-meta">Request ID: {{ notice.requestId }}</div>
      </div>
      <button class="notice-close" type="button" @click="notice.clear()" aria-label="关闭通知" title="关闭通知">
        <X :size="17" />
      </button>
    </div>
  </transition>
</template>

<script setup>
import { AlertCircle, CheckCircle2, Info, TriangleAlert, X } from '@lucide/vue'
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
  padding: 14px;
  border-radius: var(--radius-lg);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 96%, transparent);
  box-shadow: var(--shadow-soft);
  backdrop-filter: blur(18px);
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
  border-color: color-mix(in srgb, var(--blue) 28%, var(--divider));
}

.notice-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  color: var(--blue);
  background: color-mix(in srgb, var(--blue) 11%, transparent);
}

.global-notice.error .notice-icon {
  color: var(--highlight);
  background: color-mix(in srgb, var(--highlight) 11%, transparent);
}

.global-notice.warning .notice-icon {
  color: var(--warning);
  background: color-mix(in srgb, var(--warning) 12%, transparent);
}

.global-notice.success .notice-icon {
  color: var(--success);
  background: color-mix(in srgb, var(--success) 11%, transparent);
}

.notice-copy {
  min-width: 0;
  flex: 1;
}

.notice-eyebrow {
  color: var(--accent);
  font-size: 0.72rem;
  font-weight: 850;
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
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--text-primary) 7%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
}

.notice-close:hover {
  color: var(--text-primary);
  background: color-mix(in srgb, var(--text-primary) 11%, transparent);
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

@media (max-width: 560px) {
  .global-notice {
    top: 10px;
    right: 10px;
    left: 10px;
    max-width: none;
    padding: 10px;
    gap: 10px;
    border-radius: var(--radius-lg);
  }

  .notice-icon,
  .notice-close {
    width: 30px;
    height: 30px;
  }

  .notice-eyebrow {
    margin-bottom: 3px;
    font-size: 0.66rem;
  }

  .notice-title {
    font-size: 0.82rem;
    line-height: 1.45;
  }

  .notice-meta {
    display: none;
  }
}
</style>
