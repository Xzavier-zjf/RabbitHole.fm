<template>
  <div class="login-view">
    <section class="login-panel">
      <div class="brand-mark" aria-hidden="true">
        <Radio :size="28" />
      </div>
      <div class="login-head">
        <div class="login-kicker">RabbitHole.fm</div>
        <h1>{{ isLogin ? '欢迎回来' : '创建账号' }}</h1>
        <p>登录后同步收藏、历史和点歌状态。</p>
      </div>

      <div class="tab-switch" role="tablist" aria-label="登录注册切换">
        <button type="button" :class="{ active: isLogin }" @click="isLogin = true">登录</button>
        <button type="button" :class="{ active: !isLogin }" @click="isLogin = false">注册</button>
      </div>

      <label class="field">
        <span>用户名</span>
        <div class="input-wrap">
          <UserRound :size="18" />
          <input v-model="username" placeholder="输入用户名" autocomplete="username" @keyup.enter="submit" />
        </div>
      </label>

      <label class="field">
        <span>密码</span>
        <div class="input-wrap">
          <Lock :size="18" />
          <input
            v-model="password"
            :type="showPwd ? 'text' : 'password'"
            placeholder="输入密码"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
          <button class="password-toggle" type="button" @click="showPwd = !showPwd" :aria-label="showPwd ? '隐藏密码' : '显示密码'" :title="showPwd ? '隐藏密码' : '显示密码'">
            <EyeOff v-if="showPwd" :size="18" />
            <Eye v-else :size="18" />
          </button>
        </div>
      </label>

      <button class="submit-btn" type="button" @click="submit">
        <LogIn v-if="isLogin" :size="18" />
        <UserPlus v-else :size="18" />
        <span>{{ isLogin ? '登录' : '注册并登录' }}</span>
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Eye, EyeOff, Lock, LogIn, Radio, UserPlus, UserRound } from '@lucide/vue'
import { useUserStore } from '../stores/user'
import { usePlayerStore } from '../stores/player'
import { consumePostLoginRedirect, savePostLoginRedirect } from '../utils/auth-redirect'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const playerStore = usePlayerStore()
const isLogin = ref(true)
const username = ref('')
const password = ref('')
const showPwd = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  try {
    if (typeof route.query.redirect === 'string' || typeof route.query.channelId === 'string') {
      savePostLoginRedirect(
        typeof route.query.redirect === 'string' ? route.query.redirect : '/',
        typeof route.query.channelId === 'string' ? Number(route.query.channelId) : null,
      )
    }
    if (isLogin.value) {
      await userStore.login(username.value, password.value)
    } else {
      await userStore.register(username.value, password.value)
      await userStore.login(username.value, password.value)
    }
    const redirect = consumePostLoginRedirect('/')
    if (redirect.channelId) {
      playerStore.setCurrentChannelId(redirect.channelId)
    }
    router.replace(redirect.path)
  } catch (e) {
    error.value = e.response?.data?.msg || '操作失败'
  }
}
</script>

<style scoped>
.login-view {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  color: var(--text-primary);
}

.login-panel {
  width: min(100%, 430px);
  padding: 34px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-card) 88%, transparent);
  box-shadow: var(--shadow-panel);
}

.brand-mark {
  width: 60px;
  height: 60px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  color: var(--accent);
  background: color-mix(in srgb, var(--accent) 12%, var(--bg-card));
  border: 1px solid color-mix(in srgb, var(--accent) 18%, var(--divider));
}

.login-head {
  margin-top: 20px;
}

.login-kicker {
  color: var(--accent);
  font-size: 0.74rem;
  font-weight: 850;
  text-transform: uppercase;
}

.login-head h1 {
  margin-top: 8px;
  font-size: clamp(2rem, 7vw, 3.35rem);
  line-height: 0.98;
}

.login-head p {
  margin-top: 10px;
  color: var(--text-secondary);
  line-height: 1.7;
}

.tab-switch {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px;
  padding: 6px;
  margin: 24px 0 18px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--bg-elevated) 58%, transparent);
}

.tab-switch button {
  min-height: 40px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  font-weight: 850;
}

.tab-switch button.active {
  background: var(--accent);
  color: #07100c;
}

.field {
  display: grid;
  gap: 7px;
  margin-bottom: 14px;
}

.field > span {
  color: var(--text-secondary);
  font-size: 0.78rem;
  font-weight: 800;
}

.input-wrap {
  min-height: 48px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 12px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  color: var(--text-tertiary);
}

.input-wrap:focus-within {
  border-color: color-mix(in srgb, var(--accent) 46%, var(--input-border));
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--accent) 10%, transparent);
}

.input-wrap input {
  flex: 1;
  min-width: 0;
  height: 46px;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
}

.input-wrap input::placeholder {
  color: var(--text-tertiary);
}

.password-toggle {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: none;
  border-radius: var(--radius-sm);
  background: color-mix(in srgb, var(--text-primary) 8%, transparent);
  color: var(--text-secondary);
  cursor: pointer;
}

.password-toggle:hover {
  color: var(--text-primary);
}

.submit-btn {
  width: 100%;
  min-height: 48px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-top: 6px;
  border: none;
  border-radius: var(--radius-md);
  background: var(--accent);
  color: #07100c;
  cursor: pointer;
  font-weight: 900;
}

.error {
  margin-top: 12px;
  color: var(--highlight);
  font-size: 0.84rem;
  line-height: 1.6;
  text-align: center;
}

@media (max-width: 520px) {
  .login-panel {
    padding: 26px 20px;
  }
}
</style>
