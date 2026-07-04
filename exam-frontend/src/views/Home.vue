<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import { useRouter } from 'vue-router'
import { setRole, setToken, setRoleName } from '@/utils/localstorage'
const account = ref('')
const password = ref('')
const router = useRouter()
const gologin = async () => {
  console.log("立即登录");
  try {
    const data = await request.post('/user/login', {
      "account": account.value,
      "password": password.value
    })
    // console.log(data);
    setToken(data.data.token || '')
    setRole(data.data.role || '')
    setRoleName(data.data.roleName || '')

    router.push('/user_home')

  } catch (error: any) {
    ElMessage.warning(`${error.message}`)
  }

}
</script>

<template>
  <div class="login-container">
    <div class="login-panel">
      <h2>欢迎回来</h2>
      <p>输入账号，系统自动识别身份</p>
      <label for="username" style=" font-size: 13px;">账号:</label>
      <el-input style="margin-bottom: 12px;" v-model="account" placeholder="请输入用户名" />
      <br>
      <label for="password" style=" font-size: 13px;">密码:</label>
      <el-input style="margin-bottom: 12px;" v-model="password" type="password" placeholder="请输入密码" show-password />
      <div style="display: flex; justify-content: center; margin-top: 12px;">
        <el-button @click="gologin" type="primary" style="width: 100%;">登录</el-button>
      </div>
    </div>
  </div>
</template>



<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  background-color: rgb(210, 230, 236);
  width: 100%;
  height: 100vh;
}

.login-panel {
  width: 420px;
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  background-color: rgba(255, 255, 255, 0.70);
  padding: 32px 24px;
  border-radius: 8px;
}
</style>
