<template>
  <div class="channel-list" :style="{ width: width + 'px' }">
    <div class="rail-brand">
      <div class="rail-logo" aria-hidden="true">
        <Radio :size="20" />
      </div>
      <div>
        <div class="rail-title">RabbitHole.fm</div>
        <div class="rail-subtitle">Music Library</div>
      </div>
    </div>

    <nav class="rail-nav" aria-label="主要导航">
      <button class="nav-item active" type="button">
        <Library :size="18" />
        <span>频道资料库</span>
      </button>
      <button class="nav-item" type="button" @click="$router.push('/explore')">
        <Compass :size="18" />
        <span>探索</span>
      </button>
      <button class="nav-item" type="button" @click="openRequest">
        <MessageSquareText :size="18" />
        <span>点歌留言</span>
      </button>
      <button class="nav-item" type="button" @click="$router.push('/playlists')">
        <ListMusic :size="18" />
        <span>我的歌单</span>
      </button>
      <button class="nav-item" type="button" @click="openFavorites">
        <Heart :size="18" />
        <span>我的收藏</span>
      </button>
      <button class="nav-item" type="button" @click="openHistory">
        <History :size="18" />
        <span>播放历史</span>
      </button>
    </nav>

    <section class="custom-channel" aria-label="自定义歌单">
      <div class="section-label">Custom Playlist</div>
      <div class="custom-input-row">
        <input
          v-model="customId"
          placeholder="输入歌单 ID"
          inputmode="numeric"
          @keyup.enter="loadCustom"
        />
        <button class="icon-action" type="button" @click="loadCustom" aria-label="进入歌单" title="进入歌单">
          <ArrowRight :size="18" />
        </button>
      </div>
    </section>

    <section class="library-section">
      <div class="section-head">
        <span>Featured Channels</span>
        <span class="section-count">{{ channels.length }}</span>
      </div>

      <div class="channel-grid">
        <button
          v-for="ch in channels"
          :key="ch.id"
          :class="['channel-item', { active: selectedId === ch.id }]"
          type="button"
          @click="select(ch.id)"
        >
          <span class="channel-icon" :class="'tone-' + ch.tone">
            <component :is="ch.icon" :size="18" />
          </span>
          <span class="ch-info">
            <span class="ch-name">{{ ch.name }}</span>
            <span class="ch-desc">{{ ch.desc }}</span>
          </span>
          <span class="channel-playing">
            <Volume2 v-if="selectedId === ch.id" :size="16" />
          </span>
        </button>
      </div>
    </section>

    <section class="user-section">
      <template v-if="user.isLoggedIn">
        <button class="user-info" type="button" @click="showUserMenu = !showUserMenu">
          <div class="user-avatar" :class="{ 'has-img': user.profile?.avatarUrl }">
            <img v-if="user.profile?.avatarUrl" :src="user.profile.avatarUrl" />
            <span v-else>{{ (user.profile?.nickname || user.profile?.username || '?')[0] }}</span>
          </div>
          <div class="user-meta">
            <div class="user-name">{{ user.profile?.nickname || user.profile?.username || '用户' }}</div>
            <div class="user-sub">已登录</div>
          </div>
          <ChevronDown :size="16" />
        </button>
        <div v-if="showUserMenu" class="user-menu">
          <button type="button" @click="openProfile">
            <UserRound :size="16" />
            <span>我的资料</span>
          </button>
          <button type="button" @click="openFavorites">
            <Heart :size="16" />
            <span>我的收藏</span>
          </button>
          <button type="button" @click="openHistory">
            <History :size="16" />
            <span>播放历史</span>
          </button>
          <button type="button" @click="handleLogout">
            <LogOut :size="16" />
            <span>退出登录</span>
          </button>
        </div>
      </template>
      <template v-else>
        <button class="login-btn" type="button" @click="openLogin">
          <UserRound :size="18" />
          <span>登录 / 注册</span>
        </button>
      </template>

      <div class="status-card">
        <div class="api-status" :class="{ online: apiOnline }">
          <span class="api-dot"></span>
          <span>{{ apiOnline ? '音乐源已连接' : '音乐源未连接' }}</span>
        </div>

        <button class="dj-test-btn" type="button" @click="testDjAudio" :disabled="testingDj">
          <Mic2 :size="16" />
          <span>{{ testingDj ? '测试中...' : djTestOk ? 'DJ 口播正常' : '测试 DJ 口播' }}</span>
        </button>
        <p v-if="djTestErr" class="dj-test-err">{{ djTestErr }}</p>
      </div>
    </section>

    <ProfileEdit v-if="showProfileEdit" @close="showProfileEdit = false" @saved="onProfileSaved" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ArrowRight,
  ChevronDown,
  Compass,
  Flame,
  Heart,
  History,
  Library,
  ListMusic,
  LogOut,
  MessageSquareText,
  Mic2,
  Radio,
  Rocket,
  Sparkles,
  UserRound,
  Volume2,
} from '@lucide/vue'
import { useUserStore } from '../stores/user'
import { getMusicApiStatus } from '../api'
import ProfileEdit from './ProfileEdit.vue'
import { savePostLoginRedirect } from '../utils/auth-redirect'

const props = defineProps({
  width: { type: Number, default: 280 },
  currentChannelId: { type: Number, default: null },
})

const emit = defineEmits(['select', 'open-history', 'open-favorites'])
const router = useRouter()
const user = useUserStore()

const selectedId = ref(null)
const customId = ref('')
const showUserMenu = ref(false)
const showProfileEdit = ref(false)
const apiOnline = ref(false)
const testingDj = ref(false)
const djTestOk = ref(false)
const djTestErr = ref('')
let testAudio = null

onMounted(checkApiStatus)

watch(() => props.currentChannelId, (value) => {
  if (value) {
    selectedId.value = value
  }
}, { immediate: true })

async function checkApiStatus() {
  try {
    const res = await getMusicApiStatus()
    apiOnline.value = res.data.apiAlive
  } catch {
    apiOnline.value = false
  }
}

function testDjAudio() {
  testingDj.value = true
  djTestErr.value = ''
  djTestOk.value = false

  if (testAudio) {
    testAudio.pause()
    testAudio = null
  }

  testAudio = new Audio('/api/tts/test')
  testAudio.volume = 0.7

  testAudio.addEventListener('play', () => {
    testingDj.value = false
    djTestOk.value = true
    setTimeout(() => {
      djTestOk.value = false
    }, 3000)
  })

  testAudio.addEventListener('error', () => {
    testingDj.value = false
    djTestErr.value = 'TTS 服务不可用，请检查 MiMo API Key'
    testAudio = null
  })

  testAudio.addEventListener('ended', () => {
    testAudio = null
  })

  testAudio.play().catch(() => {
    testingDj.value = false
    djTestErr.value = 'TTS 服务不可用，请检查 MiMo API Key'
    testAudio = null
  })
}

const channels = [
  { id: 32953014, name: '华语热歌', desc: '精选华语热歌', icon: ListMusic, tone: 'mint' },
  { id: 19723756, name: '飙升榜', desc: '实时上升曲目', icon: Rocket, tone: 'blue' },
  { id: 3778678, name: '热歌榜', desc: '高热度播放列表', icon: Flame, tone: 'coral' },
  { id: 3779629, name: '新歌榜', desc: '近期新歌入口', icon: Sparkles, tone: 'mint' },
]

function select(id) {
  selectedId.value = id
  emit('select', id)
}

function loadCustom() {
  const id = parseInt(customId.value)
  if (id > 0) select(id)
}

function openProfile() {
  showUserMenu.value = false
  showProfileEdit.value = true
}

function onProfileSaved() {
  showProfileEdit.value = false
}

function openLogin() {
  const redirect = savePostLoginRedirect(router.currentRoute.value.fullPath || '/', props.currentChannelId || selectedId.value)
  const query = {}
  if (redirect.path && redirect.path !== '/') {
    query.redirect = redirect.path
  }
  if (redirect.channelId) {
    query.channelId = String(redirect.channelId)
  }
  router.push({ path: '/login', query })
}

function openRequest() {
  const channelId = props.currentChannelId || selectedId.value || 32953014
  router.push({
    path: '/request',
    query: {
      channelId: String(channelId),
      room: `channel-${channelId}`,
    },
  })
}

async function handleLogout() {
  showUserMenu.value = false
  await user.logoutUser()
}

function openHistory() {
  showUserMenu.value = false
  emit('open-history')
}

function openFavorites() {
  showUserMenu.value = false
  emit('open-favorites')
}
</script>

<style scoped>
.channel-list {
  height: 100vh;
  padding: 16px 14px calc(var(--player-height) + 18px);
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: color-mix(in srgb, var(--bg-secondary) 94%, transparent);
  border-right: 1px solid var(--divider);
}

.rail-brand {
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 8px 8px 6px;
}

.rail-logo {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-card));
  border: 1px solid color-mix(in srgb, var(--accent) 18%, var(--divider));
}

.rail-title {
  color: var(--text-primary);
  font-size: 1rem;
  font-weight: 800;
}

.rail-subtitle {
  margin-top: 2px;
  color: var(--text-tertiary);
  font-size: 0.76rem;
}

.rail-nav,
.channel-grid,
.user-menu {
  display: grid;
  gap: 6px;
}

.nav-item,
.channel-item,
.user-info,
.login-btn,
.user-menu button,
.dj-test-btn {
  width: 100%;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast),
    transform var(--transition-fast);
}

.nav-item,
.login-btn,
.user-menu button,
.dj-test-btn {
  min-height: var(--control-height);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  font-weight: 700;
  text-align: left;
}

.nav-item:hover,
.user-menu button:hover,
.channel-item:hover {
  color: var(--text-primary);
  background: var(--bg-card-hover);
}

.nav-item.active {
  color: var(--text-primary);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-card));
  border-color: color-mix(in srgb, var(--accent) 18%, var(--divider));
}

.custom-channel,
.library-section,
.status-card {
  border: 1px solid var(--divider);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-card) 74%, transparent);
}

.custom-channel {
  padding: 12px;
}

.section-label,
.section-head {
  color: var(--text-tertiary);
  font-size: 0.72rem;
  font-weight: 800;
  text-transform: uppercase;
}

.custom-input-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.custom-input-row input {
  flex: 1;
  min-width: 0;
  height: 42px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  color: var(--text-primary);
  padding: 0 12px;
  outline: none;
}

.custom-input-row input::placeholder {
  color: var(--text-tertiary);
}

.custom-input-row input:focus {
  border-color: color-mix(in srgb, var(--accent) 48%, var(--input-border));
}

.icon-action {
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: #07100c;
  cursor: pointer;
}

.library-section {
  padding: 12px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.section-count {
  color: var(--accent);
}

.channel-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 62px;
  padding: 10px;
  text-align: left;
}

.channel-item.active {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 26%, var(--divider));
  background: color-mix(in srgb, var(--accent) 10%, var(--bg-card));
}

.channel-icon {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  flex-shrink: 0;
}

.tone-mint {
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, transparent);
}

.tone-blue {
  color: var(--blue);
  background: color-mix(in srgb, var(--blue) 12%, transparent);
}

.tone-coral {
  color: var(--coral);
  background: color-mix(in srgb, var(--coral) 12%, transparent);
}

.ch-info {
  min-width: 0;
  flex: 1;
  display: grid;
  gap: 3px;
}

.ch-name,
.ch-desc {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ch-name {
  color: var(--text-primary);
  font-size: 0.92rem;
  font-weight: 800;
}

.ch-desc {
  color: var(--text-tertiary);
  font-size: 0.76rem;
}

.channel-playing {
  width: 18px;
  color: var(--accent);
}

.user-section {
  margin-top: auto;
  display: grid;
  gap: 10px;
}

.user-info {
  min-height: 58px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-color: var(--divider);
  background: color-mix(in srgb, var(--bg-card) 74%, transparent);
}

.user-info:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 24%, var(--divider));
}

.user-avatar {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  overflow: hidden;
  flex-shrink: 0;
  color: #07100c;
  font-weight: 800;
  background: linear-gradient(135deg, var(--accent), var(--blue));
}

.user-avatar.has-img {
  background: var(--bg-card);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-meta {
  min-width: 0;
  flex: 1;
}

.user-name,
.user-sub {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-name {
  color: var(--text-primary);
  font-size: 0.9rem;
  font-weight: 800;
}

.user-sub {
  margin-top: 3px;
  color: var(--text-tertiary);
  font-size: 0.74rem;
}

.user-menu {
  padding: 6px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: var(--bg-card);
}

.login-btn {
  justify-content: center;
  border-color: transparent;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 58%, var(--blue)));
  color: #07100c;
}

.status-card {
  padding: 12px;
  display: grid;
  gap: 10px;
}

.api-status {
  display: flex;
  align-items: center;
  gap: 9px;
  color: var(--text-tertiary);
  font-size: 0.78rem;
  font-weight: 700;
}

.api-status.online {
  color: var(--success);
}

.api-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-tertiary);
}

.api-status.online .api-dot {
  background: var(--success);
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--success) 14%, transparent);
}

.dj-test-btn {
  min-height: 38px;
  justify-content: center;
  color: var(--text-secondary);
  border-color: var(--divider);
  background: var(--bg-card);
}

.dj-test-btn:hover:not(:disabled) {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--blue) 28%, var(--divider));
}

.dj-test-btn:disabled {
  opacity: 0.64;
}

.dj-test-err {
  color: var(--highlight);
  font-size: 0.76rem;
  line-height: 1.5;
  text-align: center;
}
</style>
