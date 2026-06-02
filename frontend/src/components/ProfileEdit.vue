<template>
  <div class="modal-overlay" @click.self="$emit('close')">
    <div class="modal-card">
      <div class="modal-header">
        <div>
          <div class="modal-kicker">
            <UserRound :size="15" />
            <span>Profile</span>
          </div>
          <h3>我的资料</h3>
        </div>
        <button class="close-btn" type="button" @click="$emit('close')" aria-label="关闭" title="关闭">
          <X :size="18" />
        </button>
      </div>

      <div class="modal-body">
        <div class="avatar-section">
          <div class="avatar-preview" :class="{ 'has-img': previewUrl }">
            <img v-if="previewUrl" :src="previewUrl" />
            <span v-else>{{ (nickname || username || '?')[0] }}</span>
          </div>
          <div class="avatar-actions">
            <label class="upload-btn">
              <Upload :size="17" />
              <span>{{ previewUrl ? '更换头像' : '上传头像' }}</span>
              <input type="file" accept="image/*" @change="onFileChange" hidden />
            </label>
            <button v-if="previewUrl" class="remove-avatar-btn" type="button" @click="removeAvatar">
              <Trash2 :size="17" />
              <span>移除</span>
            </button>
          </div>
        </div>

        <label class="field">
          <span>昵称</span>
          <input v-model="nickname" placeholder="输入昵称" maxlength="32" @keyup.enter="save" />
        </label>

        <label class="field">
          <span>用户名</span>
          <input :value="username" disabled />
        </label>
      </div>

      <div class="modal-footer">
        <button class="cancel-btn" type="button" @click="$emit('close')">取消</button>
        <button class="save-btn" type="button" @click="save" :disabled="saving">
          <LoaderCircle v-if="saving" :size="17" class="spin" />
          <Save v-else :size="17" />
          <span>{{ saving ? '保存中' : '保存' }}</span>
        </button>
      </div>

      <p v-if="msg" :class="{ ok: ok, err: !ok }">{{ msg }}</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { LoaderCircle, Save, Trash2, Upload, UserRound, X } from '@lucide/vue'
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
let objectPreviewUrl = ''

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
  revokeObjectPreview()
  selectedFile.value = file
  objectPreviewUrl = URL.createObjectURL(file)
  previewUrl.value = objectPreviewUrl
}

function removeAvatar() {
  revokeObjectPreview()
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
      revokeObjectPreview()
      previewUrl.value = avatarUrl
      selectedFile.value = null
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

function revokeObjectPreview() {
  if (!objectPreviewUrl) return
  URL.revokeObjectURL(objectPreviewUrl)
  objectPreviewUrl = ''
}

onBeforeUnmount(() => {
  revokeObjectPreview()
})
</script>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 300;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
  background: var(--bg-overlay);
  backdrop-filter: blur(12px);
}

.modal-card {
  width: 430px;
  max-width: 100%;
  padding: 24px;
  border: 1px solid var(--divider);
  border-radius: var(--radius-xl);
  background: color-mix(in srgb, var(--bg-elevated) 96%, transparent);
  box-shadow: var(--shadow-panel);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 22px;
}

.modal-kicker {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--accent);
  font-size: 0.72rem;
  font-weight: 850;
  text-transform: uppercase;
  margin-bottom: 8px;
}

.modal-header h3 {
  margin: 0;
  color: var(--text-primary);
  font-size: 1.55rem;
  line-height: 1.1;
}

.close-btn,
.cancel-btn,
.save-btn,
.remove-avatar-btn,
.upload-btn {
  min-height: var(--control-height);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: var(--radius-md);
  cursor: pointer;
  font-weight: 800;
}

.close-btn {
  width: 40px;
  min-width: 40px;
  border: 1px solid var(--divider);
  background: var(--bg-card);
  color: var(--text-secondary);
}

.close-btn:hover {
  color: var(--text-primary);
  border-color: color-mix(in srgb, var(--accent) 28%, var(--divider));
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 20px;
}

.avatar-preview {
  width: 78px;
  height: 78px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  overflow: hidden;
  flex-shrink: 0;
  color: #07100c;
  font-size: 1.8rem;
  font-weight: 900;
  background: linear-gradient(135deg, var(--accent), var(--blue));
}

.avatar-preview.has-img {
  background: var(--bg-card);
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.upload-btn {
  padding: 0 14px;
  border: 1px solid color-mix(in srgb, var(--accent) 26%, var(--divider));
  background: color-mix(in srgb, var(--accent) 10%, transparent);
  color: var(--accent);
}

.remove-avatar-btn,
.cancel-btn {
  padding: 0 14px;
  border: 1px solid var(--divider);
  background: var(--bg-card);
  color: var(--text-secondary);
}

.remove-avatar-btn {
  color: var(--coral);
  border-color: color-mix(in srgb, var(--coral) 28%, var(--divider));
}

.field {
  display: grid;
  gap: 7px;
  margin-bottom: 14px;
}

.field span {
  color: var(--text-secondary);
  font-size: 0.78rem;
  font-weight: 800;
}

.field input {
  width: 100%;
  min-height: 46px;
  border: 1px solid var(--input-border);
  border-radius: var(--radius-md);
  background: var(--input-bg);
  color: var(--text-primary);
  padding: 0 12px;
  outline: none;
}

.field input:focus {
  border-color: color-mix(in srgb, var(--accent) 46%, var(--input-border));
  box-shadow: 0 0 0 5px color-mix(in srgb, var(--accent) 10%, transparent);
}

.field input:disabled {
  opacity: 0.58;
  cursor: not-allowed;
}

.modal-footer {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 14px;
}

.save-btn {
  padding: 0 16px;
  border: none;
  background: var(--accent);
  color: #07100c;
}

.save-btn:disabled {
  opacity: 0.62;
  cursor: not-allowed;
}

.ok,
.err {
  margin-top: 12px;
  text-align: center;
  font-size: 0.84rem;
  line-height: 1.6;
}

.ok { color: var(--success); }
.err { color: var(--highlight); }

.spin {
  animation: spin 850ms linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 520px) {
  .modal-card {
    padding: 20px;
  }

  .avatar-section {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
