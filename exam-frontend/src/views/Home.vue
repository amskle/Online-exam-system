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
  <div class="view active" id="view-login">
    <div class="login-container">
      <div class="login-panel">
        <div class="logo">
           <div class="logo-mark">😛</div>
        </div>
        <div class="welcome-text">
          <h2>欢迎回来</h2>
        </div>
        <div class="input-group">
          <label for="username">账号:</label>
          <el-input style="margin-bottom: 12px;" v-model="account" placeholder="请输入用户名" />
        </div>
        <div class="input-group">
          <label for="password">密码:</label>
          <el-input style="margin-bottom: 12px;" v-model="password" type="password" placeholder="请输入密码" show-password />
        </div>
        <div class="form-footer">
          <label
            style="display:flex;align-items:center;gap:8px;font-size:13px;color:var(--text-secondary);cursor:pointer;">
            <input type="checkbox" checked style="accent-color:var(--primary);">记住我
          </label>
          <a href="#">忘记密码？</a>
        </div>
        <div style="display: flex; justify-content: center; margin-top: 12px;">
          <el-button @click="gologin" type="primary" style="width: 100%;">登录</el-button>
        </div>
      </div>
    </div>
  </div>
</template>



<style scoped>
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #F5F6FA 0%, #E8E8F0 100%);
  position: relative;
  overflow: hidden;
}

.login-container::before {
  content: '';
  position: absolute;
  width: 600px;
  height: 600px;
  background: radial-gradient(circle, rgba(108, 92, 231, 0.08) 0%, transparent 70%);
  top: -200px;
  right: -200px;
  border-radius: 50%;
}

.login-container::after {
  content: '';
  position: absolute;
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(0, 206, 201, 0.06) 0%, transparent 70%);
  bottom: -100px;
  left: -100px;
  border-radius: 50%;
}

.login-panel {
  background: var(--surface-glass);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  border: 1px solid rgba(255, 255, 255, 0.6);
  border-radius: var(--radius-lg);
  padding: 48px 40px;
  width: 420px;
  max-width: 90vw;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.06);
  position: relative;
  z-index: 1;
}

.login-panel .logo {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 28px;
}

.login-panel .logo .logo-mark {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
}

.login-panel .logo h1 {
  font-size: 22px;
  font-weight: 700;
}

.login-panel .logo p {
  font-size: 13px;
  color: var(--text-muted);
}

.login-panel .welcome-text {
  margin-bottom: 24px;
}

.login-panel .welcome-text h2 {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 4px;
}


.input-group {
  margin-bottom: 16px;
}

.input-group label {
  display: block;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 6px;
}
</style>
