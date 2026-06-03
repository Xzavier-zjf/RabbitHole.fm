<template>
  <div class="music-source-select" role="radiogroup" aria-label="音乐源选择">
    <button
      v-for="option in sourceStore.availableOptions"
      :key="option.key"
      class="source-option"
      :class="{ active: sourceStore.selectedSource === option.key, unavailable: !option.available || !option.enabled }"
      type="button"
      role="radio"
      :aria-checked="sourceStore.selectedSource === option.key"
      :aria-disabled="option.key !== 'all' && (!option.available || !option.enabled)"
      :title="sourceTitle(option)"
      @click="sourceStore.setSource(option.key)"
    >
      {{ option.label }}
    </button>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { useMusicSourceStore } from '../stores/music-source'

const sourceStore = useMusicSourceStore()

onMounted(() => {
  if (!sourceStore.statusLoaded) {
    sourceStore.loadStatus()
  }
})

function sourceTitle(option) {
  if (option.key === 'all') return '切换到全部来源'
  if (!option.enabled) return option.label + ' 已关闭'
  if (!option.available) return option.label + ' 当前不可用'
  return '切换到' + option.label
}
</script>

<style scoped>
.music-source-select {
  min-height: 40px;
  display: inline-grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-card) 78%, transparent);
}

.source-option {
  min-width: 0;
  min-height: 32px;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 0.72rem;
  font-weight: 850;
  white-space: nowrap;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
}

.source-option:hover,
.source-option:focus-visible {
  color: var(--text-primary);
  background: var(--bg-card-hover);
}

.source-option:focus-visible {
  outline: 2px solid var(--focus-ring);
  outline-offset: 2px;
}

.source-option.active {
  color: #07100c;
  background: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 72%, transparent);
}

.source-option.unavailable:not(.active) {
  opacity: 0.48;
}

@media (max-width: 560px) {
  .music-source-select {
    width: 100%;
    min-height: 44px;
  }

  .source-option {
    min-height: 36px;
  }
}
</style>
