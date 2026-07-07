<template>
  <main class="error-page">
    <section class="error-panel">
      <div class="status-code">401</div>
      <h1>未授权访问</h1>
      <p>当前账号没有权限访问该页面，系统已清除本地无效登录凭证，请重新登录后再试。</p>
      <div class="actions">
        <button class="primary-button" type="button" @click="goLogin">返回登录</button>
        <button class="secondary-button" type="button" @click="goBack">返回上一页</button>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { clearRole, clearToken } from '@/utils/localStorage'

const router = useRouter()

const goLogin = () => {
  router.push('/')
}

const goBack = () => {
  router.back()
}

onMounted(() => {
  clearToken()
  clearRole()
})
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  background: #f5f7fa;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.error-panel {
  width: min(100%, 520px);
  text-align: center;
}

.status-code {
  color: #f59e0b;
  font-size: 112px;
  font-weight: 800;
  line-height: 1;
}

h1 {
  color: #1f2937;
  font-size: 30px;
  font-weight: 700;
  margin: 20px 0 12px;
}

p {
  color: #606266;
  font-size: 16px;
  line-height: 1.8;
  margin: 0;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 14px;
  margin-top: 34px;
}

button {
  min-width: 116px;
  height: 42px;
  border: 0;
  border-radius: 6px;
  font-size: 15px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
}

button:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(31, 41, 55, 0.12);
}

.primary-button {
  color: #fff;
  background: #f59e0b;
}

.primary-button:hover {
  background: #d97706;
}

.secondary-button {
  color: #f59e0b;
  background: #fff7ed;
}

.secondary-button:hover {
  background: #ffedd5;
}

@media (max-width: 480px) {
  .status-code {
    font-size: 82px;
  }

  h1 {
    font-size: 24px;
  }

  .actions {
    flex-direction: column;
    align-items: stretch;
  }

  button {
    width: 100%;
  }
}
</style>
