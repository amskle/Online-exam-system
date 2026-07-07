<template>
  <el-container class="admin-layout">
    <el-aside class="admin-aside" width="224px">
      <div class="brand">
        <img src="@/assets/logo.png" alt="logo" />
        <div>
          <strong>在线考试系统</strong>
          <span>{{ roleLabel }}工作台</span>
        </div>
      </div>

      <el-menu
        class="admin-menu"
        :default-active="activePath"
        router
        background-color="#111827"
        text-color="#cbd5e1"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div>
          <h1>{{ currentTitle }}</h1>
          <p>{{ currentDescription }}</p>
        </div>
        <div class="header-actions">
          <el-tag :type="isAdmin ? 'danger' : 'success'" size="large">{{ roleLabel }}</el-tag>
          <UserProfileMenu />
          <el-button :icon="SwitchButton" @click="logout">退出登录</el-button>
        </div>
      </el-header>

      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataAnalysis,
  DocumentChecked,
  Files,
  Notebook,
  Reading,
  SwitchButton,
  User,
  UserFilled
} from '@element-plus/icons-vue'
import { clearAllAuth, getRole, RoleEnum } from '@/utils/localStorage'
import UserProfileMenu from '@/components/UserProfileMenu.vue'

const route = useRoute()
const router = useRouter()
const role = computed(() => getRole())
const isAdmin = computed(() => role.value === RoleEnum.ADMIN)

const menus = [
  {
    path: '/admin-home/dashboards',
    title: '仪表盘',
    description: '系统概览统计、趋势图表与数据分布',
    icon: DataAnalysis,
    roles: [RoleEnum.ADMIN]
  },
  {
    path: '/admin-home/users',
    title: '用户管理',
    description: '考生账号的新增、编辑、状态维护与详情查看',
    icon: User,
    roles: [RoleEnum.ADMIN]
  },
  {
    path: '/admin-home/admins',
    title: '管理员管理',
    description: '管理员账号的增删改查与登录状态管理',
    icon: UserFilled,
    roles: [RoleEnum.ADMIN]
  },
  {
    path: '/admin-home/subjects',
    title: '科目管理',
    description: '维护考试科目与题库分类基础数据',
    icon: Notebook,
    roles: [RoleEnum.ADMIN]
  },
  {
    path: '/admin-home/questions',
    title: '题目管理',
    description: '维护单选、多选、判断和主观题题库',
    icon: Reading,
    roles: [RoleEnum.ADMIN, RoleEnum.TEACHER]
  },
  {
    path: '/admin-home/examPapers',
    title: '试卷管理',
    description: '试卷增删改查、人工组卷、自动组卷和打印预览',
    icon: Files,
    roles: [RoleEnum.ADMIN, RoleEnum.TEACHER]
  },
  {
    path: '/admin-home/examRecords',
    title: '考试记录管理',
    description: '查看考试记录详情并批改主观题',
    icon: DocumentChecked,
    roles: [RoleEnum.ADMIN, RoleEnum.TEACHER]
  }
]

const visibleMenus = computed(() => menus.filter((item) => role.value && item.roles.includes(role.value)))
const activePath = computed(() => route.path)
const currentMenu = computed(() => menus.find((item) => item.path === route.path))
const currentTitle = computed(() => currentMenu.value?.title ?? '后台管理')
const currentDescription = computed(() => currentMenu.value?.description ?? '在线考试系统管理工作台')
const roleLabel = computed(() => (isAdmin.value ? '管理员' : '老师'))

const logout = () => {
  clearAllAuth()
  router.push('/')
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: #f5f7fa;
}

.admin-aside {
  background: #111827;
  color: #fff;
  box-shadow: 2px 0 12px rgba(15, 23, 42, 0.08);
}

.brand {
  height: 72px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 18px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand img {
  width: 38px;
  height: 38px;
  object-fit: contain;
}

.brand strong,
.brand span {
  display: block;
}

.brand strong {
  font-size: 16px;
  line-height: 1.3;
}

.brand span {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 12px;
}

.admin-menu {
  border-right: 0;
  padding: 10px;
}

.admin-menu :deep(.el-menu-item) {
  border-radius: 6px;
  height: 44px;
  margin-bottom: 6px;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: #2563eb;
}

.admin-header {
  height: 72px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
}

.admin-header h1 {
  color: #111827;
  font-size: 22px;
  line-height: 1.2;
  margin: 0 0 6px;
}

.admin-header p {
  color: #6b7280;
  font-size: 13px;
  margin: 0;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.admin-main {
  padding: 22px;
}

@media (max-width: 900px) {
  .admin-layout {
    display: block;
  }

  .admin-aside {
    width: 100% !important;
  }

  .admin-menu {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .admin-header {
    height: auto;
    gap: 14px;
    align-items: flex-start;
    flex-direction: column;
    padding: 16px;
  }
}
</style>
