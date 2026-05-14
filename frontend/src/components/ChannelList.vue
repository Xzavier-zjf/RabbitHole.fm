<template>
  <div class="channel-list" :style="{ width: width + 'px' }">
    <div class="rail-copy">
      <p class="rail-kicker">Rabbit Entrances</p>
      <h3 class="title">频道入口</h3>
      <p class="rail-desc">每个频道都是一条不同方向的兔子洞，点一下就开始坠入。</p>
    </div>

    <div class="custom-channel">
      <input
        v-model="customId"
        placeholder="输入歌单 ID..."
        @keyup.enter="loadCustom"
      />
      <button @click="loadCustom">进入</button>
    </div>

    <div class="channel-grid">
      <div
        v-for="ch in channels"
        :key="ch.id"
        :class="['channel-item', { active: selectedId === ch.id }]"
        @click="select(ch.id)"
      >
        <div class="channel-glow"></div>
        <div class="channel-badge">{{ ch.icon }}</div>
        <div class="ch-info">
          <div class="ch-name">{{ ch.name }}</div>
          <div class="ch-desc">{{ ch.desc }}</div>
        </div>
        <span class="channel-dot"></span>
      </div>
    </div>

    <button class="request-entry" @click="$router.push('/request')">
      <div class="request-icon">🎙️</div>
      <div class="request-copy">
        <div class="request-title">点歌留言</div>
        <div class="request-desc">让 DJ 念出你的心事和下一首歌。</div>
      </div>
    </button>

    <div class="user-section">
      <template v-if="user.isLoggedIn">
        <div class="user-info" @click="showUserMenu = !showUserMenu">
          <div class="user-avatar" :class="{ 'has-img': user.profile?.avatarUrl }">
            <img v-if="user.profile?.avatarUrl" :src="user.profile.avatarUrl" />
            <span v-else>{{ (user.profile?.nickname || '?')[0] }}</span>
          </div>
          <div class="user-meta">
            <div class="user-name">{{ user.profile?.nickname || '用户' }}</div>
            <div class="user-sub">洞中旅人已登录</div>
          </div>
        </div>
        <div v-if="showUserMenu" class="user-menu">
          <div class="menu-item" @click="openProfile">我的资料</div>
          <div class="menu-item" @click="openFavorites">我的收藏</div>
          <div class="menu-item" @click="openHistory">播放历史</div>
          <div class="menu-item" @click="handleLogout">退出登录</div>
        </div>
      </template>
      <template v-else>
        <button class="login-btn" @click="openLogin">登录 / 注册</button>
      </template>

      <div class="status-card">
        <div class="api-status" :class="{ online: apiOnline }">
          <span class="api-dot"></span>
          <span class="api-text">{{ apiOnline ? '音乐源已连接' : '音乐源未连接' }}</span>
        </div>

        <div class="dj-test">
          <button class="dj-test-btn" @click="testDjAudio" :disabled="testingDj">
            {{ testingDj ? '测试中...' : djTestOk ? '✅ DJ 口播正常' : '🎤 测试 DJ 口播' }}
          </button>
          <p v-if="djTestErr" class="dj-test-err">{{ djTestErr }}</p>
        </div>
      </div>
    </div>

    <ProfileEdit v-if="showProfileEdit" @close="showProfileEdit = false" @saved="onProfileSaved" />
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
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
  { id: 32953014, name: '华语热歌', desc: '网易云精选华语热歌', icon: '🎵' },
  { id: 19723756, name: '飙升榜', desc: '网易云音乐飙升榜', icon: '🚀' },
  { id: 3778678, name: '热歌榜', desc: '网易云音乐热歌榜', icon: '🔥' },
  { id: 3779629, name: '新歌榜', desc: '网易云音乐新歌榜', icon: '✨' },
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
  padding: 28px 18px 112px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 18px;
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--bg-secondary) 94%, transparent), color-mix(in srgb, var(--bg-primary) 88%, transparent));
  border-right: 1px solid var(--divider);
  box-shadow: inset -1px 0 0 color-mix(in srgb, var(--accent) 8%, transparent);
}

.rail-copy {
  display: grid;
  gap: 6px;
}

.rail-kicker {
  color: var(--mystic);
  font-size: 0.72rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
}

.title {
  color: var(--text-primary);
  font-size: 1.6rem;
  font-family: var(--font-display);
  font-weight: 700;
}

.rail-desc {
  color: var(--text-secondary);
  font-size: 0.88rem;
  line-height: 1.65;
}

.custom-channel {
  display: flex;
  gap: 10px;
  padding: 12px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-card) 86%, transparent);
}

.custom-channel input {
  flex: 1;
  min-width: 0;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: 999px;
  padding: 10px 14px;
  color: var(--text-primary);
  font-size: 0.9rem;
  outline: none;
  transition: border-color var(--transition-theme), background var(--transition-theme);
}

.custom-channel input::placeholder {
  color: var(--text-tertiary);
}

.custom-channel input:focus {
  border-color: color-mix(in srgb, var(--accent) 50%, transparent);
}

.custom-channel button,
.login-btn,
.dj-test-btn {
  border: none;
  border-radius: 999px;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 65%, var(--mystic)));
  color: #fff7ef;
  padding: 10px 16px;
  cursor: pointer;
  transition: transform var(--transition-theme), filter var(--transition-theme);
}

.custom-channel button:hover,
.login-btn:hover,
.dj-test-btn:hover:not(:disabled) {
  transform: translateY(-1px);
  filter: brightness(1.05);
}

.channel-grid {
  display: grid;
  gap: 12px;
}

.channel-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-radius: var(--radius-md);
  border: 1px solid var(--divider);
  background: linear-gradient(140deg, color-mix(in srgb, var(--bg-card) 94%, transparent), color-mix(in srgb, var(--bg-secondary) 86%, transparent));
  cursor: pointer;
  overflow: hidden;
  transition:
    transform var(--transition-theme),
    border-color var(--transition-theme),
    background var(--transition-theme);
}

.channel-item:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--accent) 34%, transparent);
}

.channel-item.active {
  border-color: color-mix(in srgb, var(--accent) 58%, transparent);
  box-shadow: inset 3px 0 0 var(--accent);
}

.channel-glow {
  position: absolute;
  inset: auto auto -38% -18%;
  width: 160px;
  height: 160px;
  border-radius: 50%;
  background: radial-gradient(circle, color-mix(in srgb, var(--mystic) 16%, transparent), transparent 60%);
  pointer-events: none;
}

.channel-badge {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  font-size: 1.2rem;
  flex-shrink: 0;
}

.ch-info {
  min-width: 0;
  position: relative;
  z-index: 1;
}

.ch-name {
  color: var(--text-primary);
  font-size: 0.98rem;
  font-weight: 600;
}

.ch-desc {
  color: var(--text-secondary);
  font-size: 0.8rem;
  margin-top: 4px;
  line-height: 1.45;
}

.channel-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--mystic) 78%, transparent);
  margin-left: auto;
  position: relative;
  z-index: 1;
  opacity: 0;
  transition: opacity var(--transition-theme), transform var(--transition-theme);
}

.channel-item.active .channel-dot {
  opacity: 1;
}

.channel-item.active .channel-dot {
  transform: scale(1.12);
}

.request-entry {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: transparent;
  cursor: pointer;
  font-family: inherit;
}

.request-entry:hover {
  background: color-mix(in srgb, var(--bg-card) 50%, transparent);
}

.request-icon {
  width: 44px;
  height: 44px;
  border-radius: 16px;
  display: grid;
  place-items: center;
  background: color-mix(in srgb, var(--mystic) 18%, transparent);
  flex-shrink: 0;
  font-size: 1.2rem;
}

.request-copy {
  min-width: 0;
  flex: 1;
}

.request-title {
  color: var(--text-primary);
  font-size: 0.98rem;
  font-weight: 600;
}

.request-desc {
  color: var(--text-secondary);
  font-size: 0.8rem;
  line-height: 1.45;
  margin-top: 4px;
}


.user-section {
  margin-top: auto;
  display: grid;
  gap: 12px;
}

.user-info,
.status-card,
.user-menu {
  border: 1px solid var(--divider);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  padding: 12px;
  transition: border-color var(--transition-theme), transform var(--transition-theme);
}

.user-info:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--accent) 32%, transparent);
}

.user-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--mystic));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 700;
  overflow: hidden;
  flex-shrink: 0;
}

.user-avatar.has-img {
  background: color-mix(in srgb, var(--bg-elevated) 94%, transparent);
}

.user-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.user-name {
  color: var(--text-primary);
  font-size: 0.96rem;
}

.user-sub {
  color: var(--text-secondary);
  font-size: 0.75rem;
  margin-top: 3px;
}

.user-menu {
  overflow: hidden;
}

.menu-item {
  padding: 12px 14px;
  color: var(--text-secondary);
  font-size: 0.86rem;
  cursor: pointer;
  transition: background var(--transition-theme), color var(--transition-theme);
}

.menu-item:hover {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--text-primary);
}

.login-btn {
  width: 100%;
}

.status-card {
  padding: 12px;
  display: grid;
  gap: 12px;
}

.api-status {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.78rem;
  color: var(--text-tertiary);
}

.api-status.online {
  color: var(--success);
}

.api-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--text-tertiary) 55%, transparent);
}

.api-status.online .api-dot {
  background: var(--success);
  box-shadow: 0 0 0 6px color-mix(in srgb, var(--success) 14%, transparent);
}

.dj-test {
  display: grid;
  gap: 8px;
}

.dj-test-btn {
  width: 100%;
  font-size: 0.82rem;
}

.dj-test-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}

.dj-test-err {
  color: var(--highlight);
  font-size: 0.76rem;
  text-align: center;
}
</style>
