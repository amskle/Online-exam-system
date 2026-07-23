<template>
  <el-container class="student-layout">
    <el-header class="student-header">
      <div class="brand">
        <img src="@/assets/logo.png" alt="logo" />
        <div>
          <strong>在线考试系统</strong>
          <span>学生考试中心</span>
        </div>
      </div>
      <el-menu mode="horizontal" router :default-active="activePath" class="student-menu">
        <el-menu-item index="/user-home/dashboards">考试列表</el-menu-item>
        <el-menu-item index="/user-home/records">考试记录</el-menu-item>
        <el-menu-item index="/user-home/wrong-questions">错题集</el-menu-item>
      </el-menu>
      <UserProfileMenu />
    </el-header>
    <el-main class="student-main">
      <router-view />
    </el-main>

    <!-- 智能学习伙伴 -->
    <FloatingTutor />
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import UserProfileMenu from '@/components/UserProfileMenu.vue'
import FloatingTutor from '@/components/FloatingTutor.vue'

const route = useRoute()
const activePath = computed(() => route.path)
</script>

<style scoped>
.student-layout {
  min-height: 100vh;
  background: #f5f7fa;
}

.student-header {
  height: 72px;
  display: grid;
  grid-template-columns: 260px 1fr auto;
  align-items: center;
  gap: 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand img {
  width: 38px;
  height: 38px;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  color: #111827;
  font-size: 16px;
}

.brand span {
  color: #6b7280;
  font-size: 12px;
}

.student-menu {
  border-bottom: 0;
}

.student-main {
  padding: 22px;
}

@media (max-width: 860px) {
  .student-header {
    height: auto;
    grid-template-columns: 1fr;
    padding: 14px;
  }
}
</style>
