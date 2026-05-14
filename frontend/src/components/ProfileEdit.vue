<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-card">
      <div class="modal-header">
        <div>
          <div class="modal-kicker">Rabbit Identity</div>
          <h3>我的资料</h3>
        </div>
        <button class="close-btn" @click="$emit('close')">×</button>
      </div>

      <div class="modal-body">
        <div class="avatar-section">
          <div class="avatar-preview" :class="{ 'has-img': previewUrl }">
            <img v-if="previewUrl" :src="previewUrl" />
            <span v-else>{{ (nickname || '?')[0] }}</span>
          </div>
          <div class="avatar-actions">
            <label class="upload-btn">
              <input type="file" accept="image/*" @change="onFileChange" hidden />
              {{ previewUrl ? '更换头像' : '上传头像' }}
            </label>
            <button v-if="previewUrl" class="remove-avatar-btn" @click="removeAvatar">移除</button>
          </div>
        </div>

        <div class="field">
          <label>昵称</label>
          <input v-model="nickname" placeholder="输入昵称..." maxlength="32" @keyup.enter="save" />
        </div>

        <div class="field">
          <label>用户名</label>
          <input :value="username" disabled />
        </div>
      </div>

      <div class="modal-footer">
        <button class="cancel-btn" @click="$emit('close')">取消</button>
        <button class="save-btn" @click="save" :disabled="saving">
          {{ saving ? '保存中...' : '保存' }}
        </button>
      </div>

      <p v-if="msg" :class="{ ok: ok, err: !ok }">{{ msg }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '../stores/user'
import { updateProfile, uploadAvatar } from '../api'

const emit = defineEmits(['close', 'saved'])
const user = useUserStore()

const nickname = ref('')
const previewUrl = ref('')
const username = ref('')
const saving = ref(false)
const msg = ref('')
const ok = ref(true)
const selectedFile = ref(null)

onMounted(() => {
  const p = user.profile
  if (p) {
    nickname.value = p.nickname || ''
    previewUrl.value = p.avatarUrl || ''
    username.value = p.username || ''
  }
})

function onFileChange(e) {
  const file = e.target.files[0]
  if (!file) return
  selectedFile.value = file
  previewUrl.value = URL.createObjectURL(file)
}

function removeAvatar() {
  selectedFile.value = null
  previewUrl.value = ''
}

async function save() {
  saving.value = true
  msg.value = ''
  try {
    let avatarUrl = user.profile?.avatarUrl || ''

    if (selectedFile.value) {
      const formData = new FormData()
      formData.append('file', selectedFile.value)
      const uploadRes = await uploadAvatar(formData)
      avatarUrl = uploadRes.data.avatarUrl
    } else if (!previewUrl.value) {
      avatarUrl = ''
    }

    const res = await updateProfile({
      nickname: nickname.value.trim(),
      avatarUrl,
    })
    user.profile = res.data.user
    ok.value = true
    msg.value = '保存成功'
    setTimeout(() => emit('saved'), 600)
  } catch (e) {
    ok.value = false
    msg.value = e.response?.data?.msg || '保存失败'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(8, 7, 11, 0.55);
  backdrop-filter: blur(10px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 300;
  padding: 18px;
}

.modal-card {
  background: color-mix(in srgb, var(--bg-elevated) 96%, transparent);
  border: 1px solid var(--divider);
  border-radius: 28px;
  width: 420px;
  max-width: 100%;
  padding: 24px;
  box-shadow: var(--shadow-soft);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 22px;
}

.modal-kicker {
  color: var(--mystic);
  font-size: 0.72rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.modal-header h3 {
  margin: 0;
  font-size: 1.5rem;
  color: var(--text-primary);
  font-family: var(--font-display);
}

.close-btn,
.cancel-btn,
.save-btn,
.remove-avatar-btn {
  cursor: pointer;
}

.close-btn {
  background: none;
  border: none;
  color: var(--text-tertiary);
  font-size: 1.5rem;
  line-height: 1;
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}

.avatar-preview {
  width: 76px;
  height: 76px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--accent), var(--mystic));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.8rem;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  overflow: hidden;
}

.avatar-preview.has-img {
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.upload-btn,
.remove-avatar-btn,
.cancel-btn,
.save-btn {
  border-radius: 999px;
  padding: 10px 16px;
  font-size: 0.84rem;
}

.upload-btn {
  display: inline-block;
  background: color-mix(in srgb, var(--accent) 14%, transparent);
  color: var(--accent);
  border: 1px solid color-mix(in srgb, var(--accent) 20%, transparent);
  text-align: center;
}

.remove-avatar-btn,
.cancel-btn {
  background: color-mix(in srgb, var(--bg-card) 90%, transparent);
  border: 1px solid var(--divider);
  color: var(--text-secondary);
}

.save-btn {
  background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 60%, var(--mystic)));
  color: #fff7ef;
  border: none;
}

.field {
  margin-bottom: 14px;
}

.field label {
  display: block;
  color: var(--text-secondary);
  font-size: 0.78rem;
  margin-bottom: 6px;
}

.field input {
  width: 100%;
  background: var(--input-bg);
  border: 1px solid var(--input-border);
  border-radius: 16px;
  padding: 11px 13px;
  color: var(--text-primary);
  font-size: 0.92rem;
  outline: none;
  box-sizing: border-box;
}

.field input:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.modal-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 10px;
}

.ok,
.err {
  font-size: 0.84rem;
  margin-top: 10px;
  text-align: center;
}

.ok { color: var(--success); }
.err { color: var(--highlight); }
</style>
