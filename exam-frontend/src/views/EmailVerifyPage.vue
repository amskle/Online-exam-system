<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ArrowLeft, Message } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { sendEmailCodeApi, verifyEmailCodeApi } from '@/api/user-api'
import {
  clearAuthChallenge,
  getAuthChallenge,
  saveAuthChallenge,
  type AuthChallengeState
} from '@/utils/authChallenge'
import { RoleEnum, setRole, setRoleName, setToken } from '@/utils/localStorage'

const router = useRouter()
const challenge = ref<AuthChallengeState | null>(getAuthChallenge())
const email = ref('')
const code = ref('')
const trustDevice = ref(true)
const submitting = ref(false)
const sending = ref(false)
const now = ref(Date.now())
const resendAt = ref(challenge.value?.emailRequired ? 0 : Date.now() + 60_000)

const remainingSeconds = computed(() => {
  if (!challenge.value) return 0
  return Math.max(0, Math.ceil((challenge.value.expiresAt - now.value) / 1000))
})
const resendSeconds = computed(() => Math.max(0, Math.ceil((resendAt.value - now.value) / 1000)))
const needsEmail = computed(() => challenge.value?.emailRequired === true)

let timer: ReturnType<typeof setInterval> | null = null

onMounted(() => {
  if (!challenge.value) {
    ElMessage.warning('验证请求已失效，请重新登录或注册')
    router.replace('/')
    return
  }
  timer = setInterval(() => {
    now.value = Date.now()
    if (challenge.value && !submitting.value && !sending.value && remainingSeconds.value === 0) {
      clearAuthChallenge()
      ElMessage.warning('验证请求已过期，请重新操作')
      router.replace('/')
    }
  }, 1000)
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
})

const saveUpdatedChallenge = (maskedEmail?: string, expiresIn?: number) => {
  if (!challenge.value) return
  challenge.value = {
    ...challenge.value,
    emailRequired: false,
    maskedEmail: maskedEmail ?? challenge.value.maskedEmail,
    expiresAt: Date.now() + (expiresIn ?? 300) * 1000
  }
  saveAuthChallenge(challenge.value)
}

const sendCode = async () => {
  if (!challenge.value || resendSeconds.value > 0) return
  if (needsEmail.value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim())) {
    ElMessage.warning('请输入正确的邮箱地址')
    return
  }
  sending.value = true
  try {
    const response = await sendEmailCodeApi({
      challengeId: challenge.value.challengeId,
      email: needsEmail.value ? email.value.trim() : undefined
    })
    saveUpdatedChallenge(response.data?.maskedEmail, response.data?.expiresIn)
    resendAt.value = Date.now() + 60_000
    code.value = ''
    ElMessage.success('验证码已发送')
  } catch (error: any) {
    ElMessage.warning(error?.message ?? '验证码发送失败')
  } finally {
    sending.value = false
  }
}

const verify = async () => {
  if (!challenge.value || submitting.value) return
  if (!/^\d{6}$/.test(code.value)) {
    ElMessage.warning('请输入6位数字验证码')
    return
  }
  submitting.value = true
  try {
    const response = await verifyEmailCodeApi({
      challengeId: challenge.value.challengeId,
      code: code.value,
      trustDevice: trustDevice.value
    })
    const data = response.data
    const role = (data?.role ?? 0) as RoleEnum
    if (!data?.token || !role) throw new Error('认证响应不完整')
    setToken(data.token)
    setRole(role)
    setRoleName(data.roleName ?? '')
    const purpose = challenge.value.purpose
    const target = role === RoleEnum.ADMIN
      ? '/admin-home/dashboards'
      : role === RoleEnum.TEACHER
        ? '/admin-home/questions'
        : '/user-home/dashboards'
    await router.replace(target)
    clearAuthChallenge()
    ElMessage.success(purpose === 'REGISTER' ? '注册成功' : '登录成功')
  } catch (error: any) {
    ElMessage.warning(error?.message ?? '验证失败')
  } finally {
    submitting.value = false
  }
}

const goBack = () => {
  clearAuthChallenge()
  router.replace('/')
}
</script>

<template>
  <main class="verify-screen">
    <section class="verify-tool" aria-labelledby="verify-title">
      <button class="back-button" type="button" aria-label="返回" title="返回" @click="goBack">
        <ArrowLeft />
      </button>

      <div class="mail-mark" aria-hidden="true">
        <Message />
      </div>
      <p class="product">狗子在线考试</p>
      <h1 id="verify-title">邮箱验证</h1>
      <p v-if="needsEmail" class="hint">当前账号尚未绑定邮箱</p>
      <p v-else class="hint">验证码已发送至 <strong>{{ challenge?.maskedEmail }}</strong></p>

      <div v-if="needsEmail" class="field">
        <label for="email">邮箱</label>
        <input id="email" v-model="email" type="email" autocomplete="email" placeholder="name@example.com"
          @keyup.enter="sendCode" />
      </div>

      <button v-if="needsEmail" class="primary" type="button" :disabled="sending" @click="sendCode">
        {{ sending ? '发送中...' : '发送验证码' }}
      </button>

      <template v-else>
        <div class="field">
          <label for="code">验证码</label>
          <input id="code" v-model="code" class="code-input" type="text" inputmode="numeric" maxlength="6"
            autocomplete="one-time-code" placeholder="000000" @input="code = code.replace(/\D/g, '')"
            @keyup.enter="verify" />
        </div>

        <div class="verify-options">
          <label class="trust-option">
            <input v-model="trustDevice" type="checkbox" />
            7天内此设备免验证
          </label>
          <button class="resend" type="button" :disabled="sending || resendSeconds > 0" @click="sendCode">
            {{ resendSeconds > 0 ? resendSeconds + 's 后重发' : '重新发送' }}
          </button>
        </div>

        <button class="primary" type="button" :disabled="submitting" @click="verify">
          {{ submitting ? '验证中...' : '确认验证' }}
        </button>
      </template>

      <p class="expiry">
        本次验证剩余 {{ Math.floor(remainingSeconds / 60) }}:{{ String(remainingSeconds % 60).padStart(2, '0') }}
      </p>
    </section>
  </main>
</template>

<style scoped>
.verify-screen {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 24px;
  box-sizing: border-box;
  background: #f4f6f8;
  color: #172033;
  font-family: Inter, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.verify-tool {
  position: relative;
  width: min(100%, 410px);
  padding: 42px 40px 36px;
  box-sizing: border-box;
  background: #ffffff;
  border: 1px solid #dfe4ea;
  border-radius: 8px;
  box-shadow: 0 14px 40px rgba(20, 29, 45, 0.08);
}

.back-button {
  position: absolute;
  top: 18px;
  left: 18px;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  padding: 7px;
  border: 0;
  background: transparent;
  color: #667085;
  cursor: pointer;
}

.back-button:hover { color: #175cd3; }
.back-button svg { width: 20px; height: 20px; }

.mail-mark {
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  margin: 0 auto 14px;
  border-radius: 8px;
  background: #eaf2ff;
  color: #175cd3;
}

.mail-mark svg { width: 24px; height: 24px; }
.product { margin: 0 0 10px; text-align: center; color: #667085; font-size: 13px; }
h1 { margin: 0; text-align: center; font-size: 25px; letter-spacing: 0; }
.hint { min-height: 22px; margin: 9px 0 28px; text-align: center; color: #667085; font-size: 14px; }
.hint strong { color: #344054; font-weight: 600; }
.field { margin-bottom: 18px; }
.field label { display: block; margin-bottom: 7px; font-size: 13px; font-weight: 600; }
.field input {
  width: 100%;
  height: 44px;
  padding: 0 13px;
  box-sizing: border-box;
  border: 1px solid #cfd6df;
  border-radius: 7px;
  outline: none;
  color: #172033;
  font-size: 15px;
}
.field input:focus { border-color: #175cd3; box-shadow: 0 0 0 3px #eaf2ff; }
.field .code-input { text-align: center; font-size: 23px; font-weight: 600; letter-spacing: 8px; }
.verify-options { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin: -3px 0 22px; }
.trust-option { display: flex; align-items: center; gap: 7px; color: #667085; font-size: 13px; }
.trust-option input { width: 14px; height: 14px; accent-color: #175cd3; }
.resend { padding: 0; border: 0; background: transparent; color: #175cd3; font-size: 13px; cursor: pointer; }
.resend:disabled { color: #98a2b3; cursor: default; }
.primary {
  width: 100%;
  height: 44px;
  border: 0;
  border-radius: 7px;
  background: #175cd3;
  color: #fff;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.primary:hover { background: #1849a9; }
.primary:disabled { opacity: 0.65; cursor: not-allowed; }
.expiry { margin: 19px 0 0; text-align: center; color: #98a2b3; font-size: 12px; }

@media (max-width: 520px) {
  .verify-screen { padding: 0; background: #fff; }
  .verify-tool { min-height: 100vh; padding: 70px 24px 32px; border: 0; box-shadow: none; }
}
</style>
