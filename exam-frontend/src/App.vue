<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { clearAllAuth, getUserId } from '@/utils/localStorage'
import { onKicked, closeChannel } from '@/utils/sessionSync'

const router = useRouter()

onMounted(() => {
  onKicked((newUserId) => {
    const currentUserId = getUserId()
    if (currentUserId && currentUserId === newUserId) {
      clearAllAuth()
      ElMessage.warning('账号已在其他标签页登录，当前页面已失效')
      router.push('/')
    }
  })
})

onUnmounted(() => {
  closeChannel()
})
</script>

<template>
  <router-view />
</template>
