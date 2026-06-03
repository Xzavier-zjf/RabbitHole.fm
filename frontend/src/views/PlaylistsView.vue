<template>
  <div class="page-view">
    <header class="page-head">
      <button class="back-btn" type="button" @click="backToRadio">
        <ChevronLeft :size="18" />
        <span>返回播放</span>
      </button>
      <div class="head-copy">
        <div class="page-kicker">
          <ListMusic :size="16" />
          <span>Library</span>
        </div>
        <h1>我的歌单</h1>
        <p>把收藏、搜索和当前播放里的歌曲整理成自己的播放列表。</p>
      </div>
    </header>

    <div class="playlist-layout">
      <aside class="panel-card playlist-sidebar">
        <div class="section-head">
          <div>
            <div class="section-kicker">Playlists</div>
            <h2>歌单</h2>
          </div>
          <span class="count-badge">{{ playlists.playlists.length }}</span>
        </div>

        <form class="create-row" @submit.prevent="createPlaylist">
          <input v-model="newPlaylistName" placeholder="新建歌单" />
          <button class="icon-btn primary" type="submit" aria-label="新建歌单" title="新建歌单">
            <Plus :size="18" />
          </button>
        </form>

        <div class="playlist-list">
          <button
            v-for="playlist in playlists.playlists"
            :key="playlist.id"
            class="playlist-item"
            :class="{ active: playlist.id === selectedId }"
            type="button"
            @click="selectedId = playlist.id"
          >
            <span class="playlist-icon">
              <ListMusic :size="17" />
            </span>
            <span class="playlist-copy">
              <span class="playlist-name">{{ playlist.name }}</span>
              <span class="playlist-meta">{{ playlist.tracks.length }} 首歌</span>
            </span>
          </button>
        </div>
      </aside>

      <section class="panel-card tracks-panel">
        <div class="section-head">
          <div class="title-edit" v-if="activePlaylist">
            <div class="section-kicker">Saved Queue</div>
            <div v-if="renaming" class="rename-row">
              <input v-model="renameValue" @keyup.enter="saveRename" />
              <button class="icon-btn" type="button" @click="saveRename" aria-label="保存歌单名" title="保存歌单名">
                <Check :size="17" />
              </button>
              <button class="icon-btn" type="button" @click="renaming = false" aria-label="取消重命名" title="取消重命名">
                <X :size="17" />
              </button>
            </div>
            <h2 v-else>{{ activePlaylist.name }}</h2>
          </div>

          <div class="panel-actions" v-if="activePlaylist">
            <button class="action-btn" type="button" :disabled="!activePlaylist.tracks.length" @click="playAll">
              <Play :size="17" fill="currentColor" />
              <span>播放全部</span>
            </button>
            <button class="icon-btn" type="button" @click="startRename" aria-label="重命名歌单" title="重命名歌单">
              <Pencil :size="17" />
            </button>
            <button
              class="icon-btn danger"
              type="button"
              :disabled="playlists.playlists.length <= 1"
              @click="deleteActive"
              aria-label="删除歌单"
              title="删除歌单"
            >
              <Trash2 :size="17" />
            </button>
          </div>
        </div>

        <div class="list" v-if="activePlaylist?.tracks.length">
          <article
            class="track-row"
            v-for="(track, index) in activePlaylist.tracks"
            :key="trackKey(track)"
            :class="{
              dragging: draggingTrackKey === trackKey(track),
              'drop-target': dropTargetIndex === index,
            }"
            draggable="true"
            @dragstart="onTrackDragStart($event, index, track)"
            @dragover.prevent="onTrackDragOver(index)"
            @dragleave="onTrackDragLeave(index)"
            @drop.prevent="onTrackDrop(index)"
            @dragend="onTrackDragEnd"
          >
            <button
              class="drag-handle"
              type="button"
              :aria-label="`拖拽排序：${track.name}`"
              :title="`拖拽排序：${track.name}`"
            >
              <GripVertical :size="18" />
            </button>
            <img v-if="track.coverUrl" class="track-cover" :src="proxyCoverUrl(track.coverUrl)" referrerpolicy="no-referrer" />
            <span v-else class="track-cover fallback">
              <Music :size="18" />
            </span>
            <div class="track-info">
              <div class="track-title-row">
                <span class="track-title">{{ track.name }}</span>
                <span v-if="sourceLabel(track)" class="source-badge">{{ sourceLabel(track) }}</span>
              </div>
              <div class="track-meta">{{ track.artists.join(' / ') || '未知歌手' }}</div>
            </div>
            <div class="track-actions">
              <button
                class="icon-btn"
                type="button"
                :disabled="index === 0"
                @click="moveTrack(track, -1)"
                aria-label="上移歌曲"
                title="上移歌曲"
              >
                <ArrowUp :size="17" />
              </button>
              <button
                class="icon-btn"
                type="button"
                :disabled="index === activePlaylist.tracks.length - 1"
                @click="moveTrack(track, 1)"
                aria-label="下移歌曲"
                title="下移歌曲"
              >
                <ArrowDown :size="17" />
              </button>
              <button class="icon-btn" type="button" @click="playTrack(track)" aria-label="播放歌曲" title="播放歌曲">
                <Play :size="17" fill="currentColor" />
              </button>
              <button class="icon-btn danger" type="button" @click="removeTrack(track)" aria-label="从歌单移除" title="从歌单移除">
                <Trash2 :size="17" />
              </button>
            </div>
          </article>
        </div>

        <div class="empty" v-else>
          <ListMusic :size="32" />
          <span>这个歌单还没有歌曲。你可以从当前播放、搜索结果或收藏页加入。</span>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowDown,
  ArrowUp,
  Check,
  ChevronLeft,
  GripVertical,
  ListMusic,
  Music,
  Pencil,
  Play,
  Plus,
  Trash2,
  X,
} from '@lucide/vue'
import { proxyCoverUrl } from '../api'
import { usePlayerStore } from '../stores/player'
import { usePlaylistsStore } from '../stores/playlists'

const router = useRouter()
const player = usePlayerStore()
const playlists = usePlaylistsStore()

const selectedId = ref(playlists.playlists[0]?.id || '')
const newPlaylistName = ref('')
const renaming = ref(false)
const renameValue = ref('')
const draggingTrackIndex = ref(null)
const draggingTrackKey = ref('')
const dropTargetIndex = ref(null)

const activePlaylist = computed(() => playlists.getPlaylist(selectedId.value))

watch(
  () => playlists.playlists.map((playlist) => playlist.id).join('|'),
  () => {
    if (!activePlaylist.value) {
      selectedId.value = playlists.playlists[0]?.id || ''
    }
  }
)

function createPlaylist() {
  const playlist = playlists.createPlaylist(newPlaylistName.value)
  selectedId.value = playlist.id
  newPlaylistName.value = ''
}

function startRename() {
  if (!activePlaylist.value) return
  renameValue.value = activePlaylist.value.name
  renaming.value = true
}

function saveRename() {
  if (!activePlaylist.value) return
  playlists.renamePlaylist(activePlaylist.value.id, renameValue.value)
  renaming.value = false
}

function deleteActive() {
  if (!activePlaylist.value) return
  const currentId = activePlaylist.value.id
  playlists.deletePlaylist(currentId)
  selectedId.value = playlists.playlists[0]?.id || ''
}

function trackKey(track) {
  return track?.trackKey || playlists.trackKey(track)
}

function sourceLabel(track) {
  const source = track?.source || 'netease'
  if (source === 'netease') return ''
  return track?.sourceLabel || source
}

function removeTrack(track) {
  if (!activePlaylist.value) return
  playlists.removeTrack(activePlaylist.value.id, track)
}

function moveTrack(track, direction) {
  if (!activePlaylist.value) return
  playlists.moveTrack(activePlaylist.value.id, track, direction)
}

function onTrackDragStart(event, index, track) {
  draggingTrackIndex.value = index
  draggingTrackKey.value = trackKey(track)
  dropTargetIndex.value = null
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', trackKey(track))
}

function onTrackDragOver(index) {
  if (draggingTrackIndex.value === null || draggingTrackIndex.value === index) {
    dropTargetIndex.value = null
    return
  }
  dropTargetIndex.value = index
}

function onTrackDragLeave(index) {
  if (dropTargetIndex.value === index) {
    dropTargetIndex.value = null
  }
}

function onTrackDrop(index) {
  if (!activePlaylist.value || draggingTrackIndex.value === null) return
  playlists.reorderTrack(activePlaylist.value.id, draggingTrackIndex.value, index)
  clearDragState()
}

function onTrackDragEnd() {
  clearDragState()
}

function clearDragState() {
  draggingTrackIndex.value = null
  draggingTrackKey.value = ''
  dropTargetIndex.value = null
}

function playTrack(track) {
  const index = player.addToQueue(track)
  if (index < 0) return
  player.playItem(index)
  player.setMiniPlayer(true)
}

function playAll() {
  if (!activePlaylist.value?.tracks.length) return
  const firstPlayableIndex = activePlaylist.value.tracks
    .map((track) => player.addToQueue(track))
    .find((index) => index >= 0)
  if (firstPlayableIndex == null) return
  player.playItem(firstPlayableIndex)
  player.setMiniPlayer(true)
}

function backToRadio() {
  router.push({ path: '/', query: { from: 'playlists' } })
}
</script>

<style scoped>
.page-view {
  min-height: 100vh;
  padding: 24px;
  color: var(--text-primary);
  overflow-y: auto;
}

.page-head,
.playlist-layout {
  width: min(1180px, 100%);
  margin-inline: auto;
}

.page-head {
  display: flex;
  align-items: flex-start;
  gap: 18px;
  margin-bottom: 18px;
}

.head-copy {
  min-width: 0;
}

.page-kicker,
.section-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--accent);
  font-size: 0.74rem;
  font-weight: 850;
  text-transform: uppercase;
}

.page-head h1 {
  margin-top: 8px;
  font-size: clamp(2rem, 4vw, 4rem);
  line-height: 1;
}

.page-head p {
  margin-top: 10px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.playlist-layout {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr);
  gap: 16px;
}

.panel-card {
  min-width: 0;
  padding: 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-card) 84%, transparent);
  box-shadow: var(--shadow-soft);
}

.section-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.section-head h2 {
  margin-top: 4px;
  font-size: 1.28rem;
}

.count-badge {
  min-width: 34px;
  min-height: 30px;
  display: grid;
  place-items: center;
  border-radius: var(--radius-pill);
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  font-size: 0.8rem;
  font-weight: 850;
}

.back-btn,
.icon-btn,
.action-btn {
  min-height: var(--control-height);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-md);
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 800;
}

.back-btn,
.action-btn {
  padding: 0 14px;
}

.icon-btn {
  width: var(--control-height);
}

.icon-btn:hover,
.action-btn:hover,
.back-btn:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
  background: var(--bg-card-hover);
}

.icon-btn.primary,
.action-btn {
  border-color: transparent;
  background: var(--accent);
  color: #07100c;
}

.icon-btn.danger {
  color: var(--coral);
}

.icon-btn:disabled,
.action-btn:disabled {
  opacity: 0.58;
}

.create-row,
.rename-row {
  display: flex;
  gap: 8px;
}

.create-row {
  margin-bottom: 14px;
}

.create-row input,
.rename-row input {
  flex: 1;
  min-width: 0;
  height: var(--control-height);
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  color: var(--text-primary);
  padding: 0 12px;
  outline: none;
}

.create-row input:focus,
.rename-row input:focus {
  border-color: color-mix(in srgb, var(--accent) 46%, var(--input-border));
}

.playlist-list,
.list {
  display: grid;
  gap: 9px;
}

.playlist-item {
  width: 100%;
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 9px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.playlist-item.active {
  border-color: color-mix(in srgb, var(--accent) 30%, var(--divider));
  background: color-mix(in srgb, var(--accent) 10%, var(--bg-card));
}

.playlist-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border-radius: 10px;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.playlist-copy,
.track-info {
  min-width: 0;
  flex: 1;
}

.playlist-name,
.playlist-meta,
.track-title-row,
.track-meta {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.playlist-name,
.track-title {
  color: var(--text-primary);
  font-weight: 850;
}

.track-title-row {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}

.track-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-badge {
  min-height: 20px;
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
  padding: 0 7px;
  border-radius: var(--radius-pill);
  background: color-mix(in srgb, var(--blue) 12%, transparent);
  color: var(--blue);
  font-size: 0.66rem;
  font-weight: 850;
}

.playlist-meta,
.track-meta {
  margin-top: 4px;
  color: var(--text-secondary);
  font-size: 0.8rem;
}

.tracks-panel {
  min-height: 500px;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.track-row {
  min-height: 66px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
  transition:
    opacity var(--transition-fast),
    border-color var(--transition-fast),
    background var(--transition-fast),
    transform var(--transition-fast);
}

.track-row.dragging {
  opacity: 0.52;
}

.track-row.drop-target {
  border-color: color-mix(in srgb, var(--accent) 42%, var(--divider));
  background: color-mix(in srgb, var(--accent) 9%, var(--bg-elevated));
  transform: translateY(-1px);
}

.drag-handle {
  width: 34px;
  height: 44px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text-tertiary);
  cursor: grab;
}

.drag-handle:active {
  cursor: grabbing;
}

.drag-handle:hover,
.drag-handle:focus-visible {
  color: var(--accent);
  border-color: color-mix(in srgb, var(--accent) 22%, var(--divider));
  background: color-mix(in srgb, var(--accent) 9%, transparent);
}

.track-cover {
  width: 46px;
  height: 46px;
  border-radius: 10px;
  object-fit: cover;
  flex-shrink: 0;
}

.track-cover.fallback {
  display: grid;
  place-items: center;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.track-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 7px;
  flex-shrink: 0;
}

.empty {
  min-height: 320px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 12px;
  color: var(--text-tertiary);
  text-align: center;
  line-height: 1.7;
}

@media (max-width: 900px) {
  .page-view {
    padding: 16px;
  }

  .page-head {
    flex-direction: column;
  }

  .playlist-layout {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .section-head,
  .track-row {
    align-items: flex-start;
  }

  .section-head {
    flex-direction: column;
  }

  .track-row {
    display: grid;
    grid-template-columns: 34px 46px minmax(0, 1fr);
  }

  .track-actions {
    grid-column: 2 / -1;
    justify-content: flex-start;
    flex-wrap: wrap;
  }
}
</style>
