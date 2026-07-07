<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { loginApi, registerApi } from '@/api/user-api'
import { setRole, setRoleName, setToken, saveRememberedLogin, getRememberedLogin, clearRememberedLogin, RoleEnum } from '@/utils/localStorage'

// ===== 登录逻辑（原样保留） =====
const account = ref('')
const password = ref('')
const router = useRouter()
const gologin = async () => {
  try {
    const response = await loginApi({
      account: account.value,
      password: password.value
    })

    const role = (response?.data?.role ?? 0) as RoleEnum
    setToken(response?.data?.token ?? '')
    setRole(role)
    setRoleName(response?.data?.roleName ?? '')

    if (rememberMe.value) {
      saveRememberedLogin(account.value, password.value)
    } else {
      clearRememberedLogin()
    }
    ElMessage.success('登录成功')
    if (role === RoleEnum.ADMIN) {
      router.push('/admin-home/dashboards')
      return
    }
    if (role === RoleEnum.TEACHER) {
      router.push('/admin-home/questions')
      return
    }
    router.push('/user-home/dashboards')
  } catch (error: any) {
    ElMessage.warning(`${error.message}`)
  }
}

// ===== 注册相关 =====
const mode = ref<'login' | 'register'>('login')
const isLogin = computed(() => mode.value === 'login')

const registerAccount = ref('')
const registerPassword = ref('')
const registerUsername = ref('')
const registerRole = ref(1) // 1=学生, 2=教师
const rememberMe = ref(false)
const switchTab = (tab: 'login' | 'register') => {
  mode.value = tab
}

const pickRole = (role: number) => {
  registerRole.value = role
}

const goRegister = async () => {
  if (!registerUsername.value.trim()) {
    ElMessage.warning('请输入姓名')
    return
  }
  if (registerUsername.value.trim().length > 20) {
    ElMessage.warning('姓名长度不能超过20位')
    return
  }
  if (!registerAccount.value.trim()) {
    ElMessage.warning('请输入账号')
    return
  }
  if (registerAccount.value.trim().length < 3 || registerAccount.value.trim().length > 30) {
    ElMessage.warning('账号长度必须在3-30位之间')
    return
  }
  if (!registerPassword.value.trim()) {
    ElMessage.warning('请输入密码')
    return
  }

  try {
    await registerApi({
      account: registerAccount.value.trim(),
      password: registerPassword.value.trim(),
      username: registerUsername.value.trim(),
      role: registerRole.value
    })
    ElMessage.success('注册成功，请登录')
    mode.value = 'login'
    registerAccount.value = ''
    registerPassword.value = ''
    registerUsername.value = ''
    registerRole.value = 1
  } catch (error: any) {
    ElMessage.warning(`${error.message}`)
  }
}

// ===== 密码显隐切换 =====
const loginPwdVisible = ref(false)
const regPwdVisible = ref(false)

// ===== 答题卡倒计时装饰 =====
const timerText = ref('42:18')
let timerInterval: ReturnType<typeof setInterval> | null = null
let totalSeconds = 42 * 60 + 18

onMounted(() => {
  const remembered = getRememberedLogin()
  if (remembered) {
    account.value = remembered.account
    password.value = remembered.password
    rememberMe.value = true
  }
  timerInterval = setInterval(() => {
    totalSeconds = totalSeconds > 0 ? totalSeconds - 1 : 42 * 60 + 18
    const m = Math.floor(totalSeconds / 60)
    const s = totalSeconds % 60
    timerText.value = `${m < 10 ? '0' : ''}${m}:${s < 10 ? '0' : ''}${s}`
  }, 1000)
})

onUnmounted(() => {
  if (timerInterval) clearInterval(timerInterval)
})
</script>

<template>
  <div class="screen">
    <div class="shell">
      <!-- ===== 左栏：品牌展示 ===== -->
      <div class="brand">
        <div class="brand-top">
          <div class="logo">
            <!-- <div class="logo-mark" src=""></div> -->
            <img class="logo-mark" src="../assets/logo.png" alt="">
            <div class="logo-text">狗子在线考试</div>
          </div>

          <h1 class="headline">
            让每一场考试<br><em>公平、可信、可追溯</em>
          </h1>
          <p class="sub">面向高校与机构的在线考试系统 · 支持在线组卷、自动阅卷与全流程监考记录</p>

          <!-- 答题卡视觉 -->
          <div class="sheet">
            <div class="sheet-head">
              <span class="sheet-label">Answer sheet</span>
              <span class="sheet-timer">{{ timerText }}</span>
            </div>
            <div class="row" data-r="1">
              <span class="row-num">01</span>
              <div class="bubbles">
                <div class="bubble"></div>
                <div class="bubble"></div>
                <div class="bubble"></div>
                <div class="bubble"></div>
              </div>
            </div>
            <div class="row" data-r="2">
              <span class="row-num">02</span>
              <div class="bubbles">
                <div class="bubble"></div>
                <div class="bubble"></div>
                <div class="bubble"></div>
                <div class="bubble"></div>
              </div>
            </div>
            <div class="row" data-r="3">
              <span class="row-num">03</span>
              <div class="bubbles">
                <div class="bubble"></div>
                <div class="bubble"></div>
                <div class="bubble"></div>
                <div class="bubble"></div>
              </div>
            </div>
            <div class="row" data-r="4">
              <span class="row-num">04</span>
              <div class="bubbles">
                <div class="bubble"></div>
                <div class="bubble"></div>
                <div class="bubble"></div>
                <div class="bubble"></div>
              </div>
            </div>
          </div>
        </div>

        <!-- 统计数据 -->
        <div class="brand-foot">
          <div class="stat">
            <div class="stat-num">128</div>
            <div class="stat-label">今日考试场次</div>
          </div>
          <div class="stat">
            <div class="stat-num">99.6%</div>
            <div class="stat-label">系统稳定率</div>
          </div>
          <div class="stat">
            <div class="stat-num">3</div>
            <div class="stat-label">角色权限体系</div>
          </div>
        </div>
      </div>

      <!-- ===== 右栏：认证面板 ===== -->
      <div class="auth">
        <div class="auth-inner">
          <!-- 分段切换 -->
          <div class="segment">
            <button :class="{ active: isLogin }" type="button" @click="switchTab('login')">登录</button>
            <button :class="{ active: !isLogin }" type="button" @click="switchTab('register')">注册</button>
          </div>

          <!-- 登录面板 -->
          <div v-show="isLogin" class="panel">
            <h2 class="auth-title">欢迎回来</h2>
            <p class="auth-hint">登录以进入你的考试空间</p>

            <div class="field">
              <label>账号</label>
              <div class="input-wrap">
                <input v-model="account" type="text" placeholder="请输入用户名" autocomplete="username"
                  @keyup.enter="gologin" />
              </div>
            </div>

            <div class="field">
              <label>密码</label>
              <div class="input-wrap">
                <input v-model="password" :type="loginPwdVisible ? 'text' : 'password'" placeholder="请输入密码"
                  autocomplete="current-password" @keyup.enter="gologin" />
                <span class="toggle-eye" @click="loginPwdVisible = !loginPwdVisible">
                  {{ loginPwdVisible ? '隐藏' : '显示' }}
                </span>
              </div>
            </div>

            <div class="row-between">
              <label class="remember">
                <input type="checkbox" v-model="rememberMe" /> 记住我
              </label>
              <a class="forgot">忘记密码？</a>
            </div>

            <button class="btn-primary" type="button" @click="gologin">登录</button>

            <p class="switch-line">还没有账号？<a @click="switchTab('register')">立即注册</a></p>
          </div>

          <!-- 注册面板 -->
          <div v-show="!isLogin" class="panel">
            <h2 class="auth-title">创建账号</h2>
            <p class="auth-hint">选择身份并完善信息以开始使用</p>

            <div class="field">
              <label>身份</label>
              <div class="field-role">
                <div class="role-opt" :class="{ active: registerRole === 1 }" @click="pickRole(1)">学生</div>
                <div class="role-opt" :class="{ active: registerRole === 2 }" @click="pickRole(2)">教师</div>
              </div>
            </div>

            <div class="field">
              <label>姓名</label>
              <div class="input-wrap">
                <input v-model="registerUsername" type="text" placeholder="请输入真实姓名" autocomplete="off"
                  @keyup.enter="goRegister" />
              </div>
            </div>

            <div class="field">
              <label>账号</label>
              <div class="input-wrap">
                <input v-model="registerAccount" type="text" placeholder="请设置登录用户名" autocomplete="off"
                  @keyup.enter="goRegister" />
              </div>
            </div>

            <div class="field">
              <label>密码</label>
              <div class="input-wrap">
                <input v-model="registerPassword" :type="regPwdVisible ? 'text' : 'password'" placeholder="至少 6 位"
                  autocomplete="new-password" @keyup.enter="goRegister" />
                <span class="toggle-eye" @click="regPwdVisible = !regPwdVisible">
                  {{ regPwdVisible ? '隐藏' : '显示' }}
                </span>
              </div>
            </div>

            <button class="btn-primary" type="button" @click="goRegister">注册</button>

            <p class="switch-line">已有账号？<a @click="switchTab('login')">直接登录</a></p>
          </div>

          <p class="foot">狗子 · 在线考试系统 © 2026</p>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ===== CSS 变量 ===== */
.screen {
  --ink: #0F172A;
  --ink-2: #1E293B;
  --slate: #64748B;
  --slate-light: #94A3B8;
  --paper: #F8FAFC;
  --paper-2: #F1F5F9;
  --line: #E2E8F0;
  --line-2: #E8EDF3;
  --accent: #2563EB;
  --accent-dark: #1D4ED8;
  --accent-tint: #EFF4FF;
  --ok: #10B981;
  --white: #FFFFFF;
}

.screen {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px;
  background: var(--paper);
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  color: var(--ink);
}

/* ===== 主容器 ===== */
.shell {
  width: 100%;
  max-width: 1040px;
  min-height: 600px;
  background: var(--white);
  border-radius: 20px;
  border: 1px solid var(--line);
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  overflow: hidden;
}

/* ===== 左栏 ===== */
.brand {
  background: var(--ink);
  position: relative;
  padding: 56px 52px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  overflow: hidden;
}

.brand::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(var(--line-2) 1px, transparent 1px),
    linear-gradient(90deg, var(--line-2) 1px, transparent 1px);
  background-size: 28px 28px;
  opacity: 0.045;
}

.brand-top {
  position: relative;
  z-index: 1;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 64px;
}

.logo-mark {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  /* background: var(--accent); */
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: 'Manrope', 'Inter', sans-serif;
  font-weight: 700;
  font-size: 16px;
  /* color: var(--white); */
  flex-shrink: 0;
}

.logo-text {
  font-family: 'Manrope', 'Inter', sans-serif;
  font-weight: 700;
  font-size: 17px;
  letter-spacing: 0.2px;
  color: var(--white);
}

.headline {
  font-family: 'Manrope', 'Inter', sans-serif;
  font-weight: 700;
  font-size: 34px;
  line-height: 1.28;
  color: var(--white);
  letter-spacing: -0.3px;
  margin: 0 0 16px;
}

.headline em {
  font-style: normal;
  color: #93C5FD;
}

.sub {
  font-size: 14.5px;
  line-height: 1.7;
  color: #94A3B8;
  max-width: 340px;
  margin: 0 0 44px;
}

/* 答题卡 */
.sheet {
  position: relative;
  z-index: 1;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 22px 24px;
}

.sheet-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.sheet-label {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: #64748B;
  text-transform: uppercase;
}

.sheet-timer {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  font-size: 12px;
  color: #93C5FD;
  display: flex;
  align-items: center;
  gap: 6px;
}

.sheet-timer::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34D399;
  display: inline-block;
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {

  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.35;
  }
}

.row {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 9px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.row:last-child {
  border-bottom: none;
}

.row-num {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
  font-size: 11px;
  color: #475569;
  width: 16px;
  flex-shrink: 0;
}

.bubbles {
  display: flex;
  gap: 8px;
}

.bubble {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid rgba(255, 255, 255, 0.16);
  flex-shrink: 0;
  position: relative;
}

.row[data-r="1"] .bubble:nth-child(2),
.row[data-r="2"] .bubble:nth-child(4),
.row[data-r="3"] .bubble:nth-child(1) {
  border-color: var(--accent);
  background: var(--accent);
}

.row[data-r="1"] .bubble:nth-child(2)::after,
.row[data-r="2"] .bubble:nth-child(4)::after,
.row[data-r="3"] .bubble:nth-child(1)::after {
  content: '';
  position: absolute;
  inset: 5px;
  border-radius: 50%;
  background: var(--white);
  opacity: 0.9;
}

/* 底部统计 */
.brand-foot {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 28px;
  margin-top: 40px;
}

.stat-num {
  font-family: 'Manrope', 'Inter', sans-serif;
  font-weight: 700;
  font-size: 20px;
  color: var(--white);
}

.stat-label {
  font-size: 12px;
  color: #64748B;
  margin-top: 2px;
}

/* ===== 右栏 ===== */
.auth {
  padding: 56px 52px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.auth-inner {
  width: 100%;
  max-width: 340px;
  margin: 0 auto;
}

/* 分段切换 */
.segment {
  display: flex;
  background: var(--paper-2);
  border-radius: 10px;
  padding: 4px;
  margin-bottom: 28px;
}

.segment button {
  flex: 1;
  border: none;
  background: transparent;
  padding: 9px 0;
  font-family: 'Inter', sans-serif;
  font-size: 14px;
  font-weight: 500;
  color: var(--slate);
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.segment button.active {
  background: var(--white);
  color: var(--ink);
  font-weight: 600;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.06);
}

/* 面板切换 */
.panel {
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(4px);
  }

  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.auth-title {
  font-family: 'Manrope', 'Inter', sans-serif;
  font-weight: 700;
  font-size: 24px;
  color: var(--ink);
  margin: 0 0 6px;
}

.auth-hint {
  font-size: 13.5px;
  color: var(--slate);
  margin: 0 0 28px;
}

/* 表单字段 */
.field {
  margin-bottom: 16px;
}

.field label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--ink-2);
  margin-bottom: 7px;
}

.field-role {
  display: flex;
  gap: 8px;
}

.role-opt {
  flex: 1;
  text-align: center;
  padding: 9px 0;
  border: 1px solid var(--line);
  border-radius: 9px;
  font-size: 13px;
  color: var(--slate);
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.role-opt.active {
  border-color: var(--accent);
  background: var(--accent-tint);
  color: var(--accent-dark);
  font-weight: 600;
}

.input-wrap {
  position: relative;
}

.input-wrap input {
  width: 100%;
  padding: 11px 14px;
  border: 1px solid var(--line);
  border-radius: 9px;
  font-size: 14px;
  font-family: 'Inter', sans-serif;
  color: var(--ink);
  background: var(--white);
  outline: none;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
  box-sizing: border-box;
}

.input-wrap input::placeholder {
  color: var(--slate-light);
}

.input-wrap input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px var(--accent-tint);
}

.toggle-eye {
  position: absolute;
  right: 14px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  color: var(--slate-light);
  cursor: pointer;
  font-family: 'Inter', sans-serif;
  user-select: none;
}

.toggle-eye:hover {
  color: var(--accent);
}

/* 行内选项 */
.row-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 2px 0 22px;
}

.remember {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 13px;
  color: var(--slate);
  cursor: pointer;
  user-select: none;
}

.remember input {
  width: 14px;
  height: 14px;
  accent-color: var(--accent);
  cursor: pointer;
}

.forgot {
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
  cursor: pointer;
}

.forgot:hover {
  text-decoration: underline;
}

/* 主要按钮 */
.btn-primary {
  width: 100%;
  padding: 12px 0;
  background: var(--accent);
  color: var(--white);
  border: none;
  border-radius: 9px;
  font-size: 14.5px;
  font-weight: 600;
  font-family: 'Inter', sans-serif;
  cursor: pointer;
  transition: background 0.15s ease;
  letter-spacing: 0.2px;
}

.btn-primary:hover {
  background: var(--accent-dark);
}

/* 切换链接 */
.switch-line {
  text-align: center;
  font-size: 13px;
  color: var(--slate);
  margin-top: 18px;
}

.switch-line a {
  color: var(--accent);
  font-weight: 600;
  text-decoration: none;
  cursor: pointer;
}

.switch-line a:hover {
  text-decoration: underline;
}

/* 底部版权 */
.foot {
  text-align: center;
  font-size: 11.5px;
  color: var(--slate-light);
  margin-top: 36px;
}

/* ===== 响应式 ===== */
@media (max-width: 860px) {
  .shell {
    grid-template-columns: 1fr;
    max-width: 420px;
    min-height: auto;
  }

  .brand {
    display: none;
  }

  .auth {
    padding: 44px 32px;
  }
}
</style>
