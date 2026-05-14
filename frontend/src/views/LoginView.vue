<template>
  <div class="login-view">
    <div class="login-card">
      <div class="brand-mark">
        <span class="brand-core"></span>
        <span class="brand-ear brand-ear-left"></span>
        <span class="brand-ear brand-ear-right"></span>
      </div>
      <h2 class="login-title">RabbitHole.fm</h2>
      <p class="login-subtitle">掉进属于你的那首歌里。</p>

      <div class="tab-switch">
        <button :class="{ active: isLogin }" @click="isLogin = true">登录</button>
        <button :class="{ active: !isLogin }" @click="isLogin = false">注册</button>
      </div>

      <input v-model="username" placeholder="用户名" @keyup.enter="submit" />
      <div class="password-wrap">
        <input v-model="password" :type="showPwd ? 'text' : 'password'" placeholder="密码" @keyup.enter="submit" />
        <span class="toggle-pwd" @click="showPwd = !showPwd" :title="showPwd ? '隐藏密码' : '显示密码'">
          {{ showPwd ? '🙈' : '👁' }}
        </span>
      </div>

      <button class="submit-btn" @click="submit">
        {{ isLogin ? '登录' : '注册' }}
      </button>
      <p v-if="error" class="error">{{ error }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
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
}

.login-card {
  width: min(100%, 400px);
  padding: 42px 34px;
  border-radius: 28px;
  border: 1px solid var(--divider);
  background: color-mix(in srgb, var(--bg-elevated) 94%, transparent);
  box-shadow: var(--shadow-soft);
  text-align: center;
}

.brand-mark {
  width: 60px;
  height: 60px;
  position: relative;
  margin: 0 auto 18px;
  display: grid;
  place-items: center;
}

.brand-core {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: radial-gradient(circle at 36% 34%, var(--accent-soft), var(--accent));
}

.brand-ear {
  position: absolute;
  top: 2px;
  width: 11px;
  height: 20px;
  background: var(--mystic);
  border-radius: 999px 999px 14px 14px;
}

.brand-ear-left { left: 14px; transform: rotate(-20deg); }
.brand-ear-right { right: 14px; transform: rotate(20deg); }

.login-title {
  color: var(--text-primary);
  font-size: 2rem;
  font-family: var(--font-display);
}

.login-subtitle {
  color: var(--text-secondary);
  font-size: 0.94rem;
  margin: 8px 0 24px;
}

.tab-switch {
  display: flex;
  border-radius: 999px;
  overflow: hidden;
  margin-bottom: 20px;
  background: color-mix(in srgb, var(--accent) 8%, transparent);
  border: 1px solid var(--divider);
}

.tab-switch button {
  flex: 1;
  padding: 12px;
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  font-size: 0.94rem;
  transition: all var(--transition-theme);
}

.tab-switch button.active {
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 60%, var(--mystic)));
  color: #fff7ef;
}

input {
  display: block;
  width: 100%;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: 16px;
  padding: 12px 14px;
  color: var(--text-primary);
  font-size: 0.94rem;
  margin-bottom: 12px;
  outline: none;
}

input::placeholder {
  color: var(--text-tertiary);
}

input:focus {
  border-color: color-mix(in srgb, var(--accent) 45%, transparent);
}

.password-wrap {
  position: relative;
  margin-bottom: 12px;
}

.password-wrap input {
  margin-bottom: 0;
  padding-right: 60px;
}

.toggle-pwd {
  position: absolute;
  right: 10px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--accent);
  font-size: 0.9rem;
  cursor: pointer;
  user-select: none;
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  border-radius: 999px;
  padding: 5px 9px;
}

.submit-btn {
  width: 100%;
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 60%, var(--mystic)));
  color: #fff7ef;
  border: none;
  border-radius: 16px;
  padding: 13px;
  font-size: 1rem;
  font-weight: 600;
  cursor: pointer;
  margin-top: 8px;
}

.error {
  color: var(--highlight);
  font-size: 0.84rem;
  margin-top: 12px;
}
</style>
