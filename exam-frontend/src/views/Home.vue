<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { setRole, setToken, setRoleName } from '@/utils/localStorage'
import { loginApi } from '@/api/user-api'

const account = ref('')
const password = ref('')
const router = useRouter()
const gologin = async () => {
  try {
    const response = await loginApi({
      account: account.value,
      password: password.value
    })
    // 可选链
    setToken(response?.data?.token ?? '')
    setRole(response?.data?.role ?? 0)
    setRoleName(response?.data?.roleName ?? '')

    router.push('/user_home')
    ElMessage.success('登录成功')
  } catch (error: any) {
    ElMessage.warning(`${error.message}`)
  }

}
</script>

<template>
  <div class="login-screen">
    <!-- 浮动装饰球 -->
    <div class="deco-orb deco-orb--1"></div>
    <div class="deco-orb deco-orb--2"></div>
    <div class="deco-orb deco-orb--3"></div>

    <div class="login-card">
      <!-- Logo -->
      <div class="login-logo">
        <div class="login-logo__mark">⚡</div>
        <div class="login-logo__text">
          <h1>在线考试系统</h1>
          <p>Online Examination System</p>
        </div>
      </div>

      <!-- 欢迎语 -->
      <div class="login-welcome">
        <h2>欢迎回来</h2>
        <p>请输入您的账号和密码登录</p>
      </div>

      <!-- 表单 -->
      <div class="login-form">
        <div class="form-field">
          <label>账号</label>
          <div class="field-wrapper">
            <span class="field-icon">👤</span>
            <input
              class="field-input"
              type="text"
              v-model="account"
              placeholder="请输入用户名"
              @keyup.enter="gologin"
            />
          </div>
        </div>

        <div class="form-field">
          <label>密码</label>
          <div class="field-wrapper">
            <span class="field-icon">🔒</span>
            <input
              class="field-input"
              type="password"
              v-model="password"
              placeholder="请输入密码"
              @keyup.enter="gologin"
            />
          </div>
        </div>

        <div class="form-options">
          <label class="remember-me">
            <input type="checkbox" checked />
            <span>记住我</span>
          </label>
          <a href="#" class="forgot-link">忘记密码？</a>
        </div>

        <button class="login-btn" @click="gologin">
          <span>登 录</span>
          <span class="login-btn__arrow">→</span>
        </button>
      </div>

      <!-- 底部装饰 -->
      <div class="login-footer">
        <div class="login-footer__line"></div>
        <span>安全加密登录</span>
        <div class="login-footer__line"></div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ========== 登录全屏容器 ========== */
.login-screen {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #F5F6FA 0%, #E8E8F0 50%, #F0EEFF 100%);
  position: relative;
  overflow: hidden;
}

/* ========== 浮动装饰球 ========== */
.deco-orb {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.deco-orb--1 {
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(108,92,231,0.10) 0%, transparent 70%);
  top: -220px;
  right: -180px;
  animation: float 8s ease-in-out infinite;
}

.deco-orb--2 {
  width: 450px;
  height: 450px;
  background: radial-gradient(circle, rgba(0,206,201,0.08) 0%, transparent 70%);
  bottom: -120px;
  left: -120px;
  animation: float 10s ease-in-out infinite reverse;
}

.deco-orb--3 {
  width: 200px;
  height: 200px;
  background: radial-gradient(circle, rgba(162,155,254,0.12) 0%, transparent 70%);
  top: 50%;
  left: 10%;
  animation: float 6s ease-in-out infinite 1s;
}

@keyframes float {
  0%, 100% { transform: translateY(0) scale(1); }
  50% { transform: translateY(-20px) scale(1.03); }
}

/* ========== 登录卡片 ========== */
.login-card {
  background: rgba(255,255,255,0.72);
  backdrop-filter: blur(28px);
  -webkit-backdrop-filter: blur(28px);
  border: 1px solid rgba(255,255,255,0.65);
  border-radius: 28px;
  padding: 52px 44px 40px;
  width: 440px;
  max-width: 92vw;
  box-shadow:
    0 24px 64px rgba(0,0,0,0.06),
    0 4px 16px rgba(108,92,231,0.04);
  position: relative;
  z-index: 1;
  animation: cardEnter 0.6s cubic-bezier(0.16, 1, 0.3, 1);
}

@keyframes cardEnter {
  from {
    opacity: 0;
    transform: translateY(32px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* ========== Logo ========== */
.login-logo {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 32px;
}

.login-logo__mark {
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  color: #fff;
  box-shadow: 0 4px 16px rgba(108,92,231,0.25);
  flex-shrink: 0;
}

.login-logo__text h1 {
  font-size: 22px;
  font-weight: 700;
  color: var(--text);
  line-height: 1.3;
}

.login-logo__text p {
  font-size: 12px;
  color: var(--text-muted);
  letter-spacing: 0.3px;
}

/* ========== 欢迎语 ========== */
.login-welcome {
  margin-bottom: 28px;
}

.login-welcome h2 {
  font-size: 28px;
  font-weight: 800;
  color: var(--text);
  margin-bottom: 6px;
  letter-spacing: -0.3px;
}

.login-welcome p {
  color: var(--text-secondary);
  font-size: 14px;
}

/* ========== 表单 ========== */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.form-field label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.field-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.field-icon {
  position: absolute;
  left: 14px;
  font-size: 16px;
  z-index: 2;
  pointer-events: none;
}

.field-input {
  width: 100%;
  padding: 13px 16px 13px 44px;
  border: 1.5px solid var(--border);
  border-radius: var(--radius-sm);
  font-family: inherit;
  font-size: 14px;
  background: var(--surface);
  color: var(--text);
  transition: var(--transition);
  outline: none;
}

.field-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px rgba(108,92,231,0.10);
}

.field-input::placeholder {
  color: var(--text-muted);
}

/* ========== 表单选项 ========== */
.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.remember-me {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  cursor: pointer;
}

.remember-me input[type="checkbox"] {
  accent-color: var(--primary);
  width: 16px;
  height: 16px;
  cursor: pointer;
}

.forgot-link {
  font-size: 13px;
  color: var(--primary);
  text-decoration: none;
  font-weight: 500;
  transition: var(--transition);
}

.forgot-link:hover {
  color: var(--primary-dark);
  text-decoration: underline;
}

/* ========== 登录按钮 ========== */
.login-btn {
  width: 100%;
  padding: 14px 24px;
  border: none;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, var(--primary), var(--primary-light));
  color: #fff;
  font-family: inherit;
  font-size: 16px;
  font-weight: 700;
  cursor: pointer;
  transition: var(--transition);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  box-shadow: 0 4px 16px rgba(108,92,231,0.30);
  margin-top: 4px;
}

.login-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 28px rgba(108,92,231,0.35);
}

.login-btn:active {
  transform: translateY(0);
}

.login-btn__arrow {
  transition: transform 0.3s ease;
  font-size: 18px;
}

.login-btn:hover .login-btn__arrow {
  transform: translateX(4px);
}

/* ========== 底部装饰 ========== */
.login-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 28px;
  font-size: 12px;
  color: var(--text-muted);
}

.login-footer__line {
  flex: 1;
  height: 1px;
  background: var(--border);
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .login-card {
    padding: 36px 24px 32px;
    border-radius: 20px;
  }

  .login-welcome h2 {
    font-size: 24px;
  }

  .login-logo__mark {
    width: 40px;
    height: 40px;
    font-size: 20px;
  }
}
</style>
