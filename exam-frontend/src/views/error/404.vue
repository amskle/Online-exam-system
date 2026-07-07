<template>
  <main class="error-page">
    <section class="error-panel">
      <div class="status-code">404</div>
      <h1>页面不存在</h1>
      <p>你访问的页面不存在或已被移除，请返回首页重新开始。</p>
      <button class="primary-button" type="button" @click="goHome">返回首页</button>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

const goHome = () => {
  router.push('/')
}
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
  color: #409eff;
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

.primary-button {
  min-width: 116px;
  height: 42px;
  color: #fff;
  background: #409eff;
  border: 0;
  border-radius: 6px;
  font-size: 15px;
  cursor: pointer;
  margin-top: 34px;
  transition: transform 0.18s ease, box-shadow 0.18s ease, background-color 0.18s ease;
}

.primary-button:hover {
  background: #337ecc;
  transform: translateY(-1px);
  box-shadow: 0 8px 18px rgba(31, 41, 55, 0.12);
}

@media (max-width: 480px) {
  .status-code {
    font-size: 82px;
  }

  h1 {
    font-size: 24px;
  }

  .primary-button {
    width: 100%;
  }
}
</style>
