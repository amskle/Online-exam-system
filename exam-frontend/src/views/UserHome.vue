<template>
  <div class="user-home-container">
    <div>{{ username }}</div>
    <h1>欢迎来到用户主页</h1>
    <p>这是用户登录后的主页内容。</p>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getToken } from '@/utils/localStorage'
import { userTokenAuthApi } from '@/api/user-api'
const router = useRouter()
const account = ref('')
const username = ref('')
const tokenAuth = async (token: string) => {
  try {
    const response = await userTokenAuthApi(token);
    account.value = response?.data?.account ?? ''
    username.value = response?.data?.username ?? ''
  } catch (error: any) {
    router.push('/')
    ElMessage.error(`身份验证异常`)
    return
  }
}
onMounted(async () => {
  const token = getToken();
  if (!token) {
    router.push('/')
    ElMessage.error(`身份验证异常`)
    return
  }
  await tokenAuth(token)
})
</script>
