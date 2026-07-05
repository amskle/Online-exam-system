<template>
  <div class="main-container">
    <header class="header-container">
      <div class="logo">
        <img class="image" src="../assets/logo.png" alt="" srcset="">
        <div class="logo-text">欢迎{{ baseUser?.username }}</div>
      </div>
      <div class="user-info-container">
        <el-dropdown :hide-on-click="false">
          <span class="el-dropdown-link">
            <el-avatar :size="30" v-if="baseUser?.avatar" :src="baseUser?.avatar ?? ''" />
            <el-avatar :size="30" v-else> {{ baseUser?.username ?? '用户' }} </el-avatar>
            <div>{{ baseUser?.username ?? '用户' }}</div>
            <el-icon>
              <ArrowDownBold />
            </el-icon>
            <el-icon class="el-icon--right"><arrow-down /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="updateInfo" :icon="EditPen">修改信息</el-dropdown-item>
              <el-dropdown-item @click="updatePassword" :icon="TurnOff">修改密码</el-dropdown-item>
              <el-dropdown-item @click="loginout" :icon="Operation">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>
  </div>
  <el-dialog v-model="dialogUpdatePasswordVisible" title="修改密码" width="400" :before-close="handlePasswordDialogClose">
    <div>
      <el-input v-model="updatePasswordDTO.password" type="password" placeholder="请输入新密码" show-password />
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handlePasswordDialogClose">取消操作</el-button>
        <el-button type="primary" :loading-icon="Eleme" :loading="loadingUpdatePassword" @click="handleUpdatePassword">
          确定修改
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { EditPen, TurnOff, Operation, ArrowDownBold, Eleme } from '@element-plus/icons-vue'
import { getToken, clearToken, clearRole, clearRoleName } from '@/utils/localStorage'
import { useRouter } from 'vue-router'
import { BaseUserVO, UserUpdatePasswordDTO } from '@/types/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { Action } from 'element-plus'
import { updatePasswordApi, userTokenAuthApi } from '@/api/user-api'
const router = useRouter()
const baseUser = ref<BaseUserVO>({})
const updatePasswordDTO = ref<UserUpdatePasswordDTO>({})
const handlePasswordDialogClose = () => {
  dialogUpdatePasswordVisible.value = false
  updatePasswordDTO.value.password = ''
}
const dialogUpdatePasswordVisible = ref(false)
const loadingUpdatePassword = ref(false)
const handleUpdatePassword = async () => {
  try {
    loadingUpdatePassword.value = true
    if (!baseUser.value.id) {
      return
    }
    await updatePasswordApi(baseUser.value.id, updatePasswordDTO.value);
    loadingUpdatePassword.value = true
    router.push('/')
    clearRole()
    clearRoleName()
    clearToken()
    ElMessage.success(`修改密码成功，请重新登录`)
    return
  } catch (error: any) {
    ElMessage.error(`${error.message}`)
  } finally {
    loadingUpdatePassword.value = false
  }
}
// 修改信息
const updateInfo = () => {
  ElMessage.success(`修改信息`)
}
// 修改密码
const updatePassword = () => {
  dialogUpdatePasswordVisible.value = true
}
const loginout = () => {
  ElMessageBox.alert('确定退出登录吗？', '操作确认', {
    confirmButtonText: 'OK',
    callback: (action: Action) => {
      if (action === 'confirm') {
        clearRole()
        clearToken()
        clearRoleName()
        router.push('/')
        ElMessage.success(`退出登录成功`)
      }
    },
  })
}

const tokenAuth = async (token: string) => {
  try {
    const response = await userTokenAuthApi(token);
    baseUser.value = response?.data ?? {};
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

<style scoped>
.main-container {
  background-color: #f5f5f5;
  width: 100%;
  min-height: 100vh;
}

.header-container {
  width: 100%;
  height: 80px;
  line-height: 80px;
  padding-inline: 50px;
  background-color: #fff;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  justify-content: left;
  gap: 4px;
}

.image {
  width: 40px;
  height: 40px;
}

.logo-text {
  font-size: 24px;
  font-weight: 600;
}

.user-info-container {
  display: flex;
  justify-content: center;
  align-items: center;
}

.el-dropdown-link {
  cursor: pointer;
  color: black;
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>