<template>
  <div class="user-home-container">
    <div>{{ username }}</div>
    <h1>欢迎来到用户主页</h1>
    <p>这是用户登录后的主页内容。</p>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/localstorage'
import router from '@/router'
const account = ref('')
const username = ref('')
const tokenAuth = async (token: string) => {
  try {
    const data = await request.get(`/user/${token}/auth`);
    console.log(JSON.stringify(data));
    account.value = data.data.account || ''
    username.value = data.data.username || ''
  } catch (error: any) {
    router.push('/')
    ElMessage.error(`身份验证异常`)
    return
  }
}
onMounted(async() => {
  const token = getToken();
  if (!token) {
    router.push('/')
    ElMessage.error(`身份验证异常`)
    return
  }
  await tokenAuth(token)
})
</script>
