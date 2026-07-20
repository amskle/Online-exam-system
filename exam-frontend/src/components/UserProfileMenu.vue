<template>
  <el-dropdown :hide-on-click="false">
    <span class="profile-trigger">
      <el-avatar :size="30" v-if="baseUser.avatar" :src="getImageUrl(baseUser.avatar)" />
      <el-avatar :size="30" v-else>{{ baseUser.username ?? '用户' }}</el-avatar>
      <span>{{ baseUser.username ?? '用户' }}</span>
      <el-icon><ArrowDownBold /></el-icon>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item @click="updateInfo" :icon="EditPen">修改信息</el-dropdown-item>
        <el-dropdown-item @click="updatePassword" :icon="TurnOff">修改密码</el-dropdown-item>
        <el-dropdown-item @click="logout" :icon="Operation">退出登录</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>

  <el-dialog v-model="dialogUpdatePasswordVisible" title="修改密码" width="400" :before-close="handlePasswordDialogClose">
    <el-input v-model="updatePasswordDTO.password" type="password" placeholder="请输入新密码" show-password />
    <template #footer>
      <el-button @click="handlePasswordDialogClose">取消操作</el-button>
      <el-button type="primary" :loading="loadingUpdatePassword" @click="handleUpdatePassword">确定修改</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="dialogUpdateInfoVisible" title="修改个人信息" width="400" :before-close="handleInfoDialogClose">
    <div class="profile-form">
      <div>
        <p>头像</p>
        <el-upload
          class="avatar-uploader"
          :action="`${apiBaseUrl}/files/upload`"
          :show-file-list="false"
          :before-upload="beforeAvatarUpload"
          :on-success="handleUploadSuccess"
        >
          <img v-if="imageUrl" :src="imageUrl" class="avatar" />
          <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
        </el-upload>
      </div>
      <div>
        <p>用户名</p>
        <el-input v-model="updateInfoDTO.username" placeholder="请输入用户名" />
      </div>
      <div>
        <p>手机号</p>
        <el-input v-model="updateInfoDTO.phone" placeholder="请输入手机号" />
      </div>
      <div>
        <p>邮箱</p>
        <el-input v-model="updateInfoDTO.email" placeholder="请输入邮箱" />
      </div>
      <div>
        <p>性别</p>
        <el-radio-group v-model="updateInfoDTO.gender" size="large" fill="#409eff">
          <el-radio-button label="男" :value="Number(1)" />
          <el-radio-button label="女" :value="Number(2)" />
        </el-radio-group>
      </div>
    </div>
    <template #footer>
      <el-button @click="handleInfoDialogClose">取消操作</el-button>
      <el-button type="primary" :loading="loadingUpdateInfo" @click="handleUpdateInfoOperation">确定修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowDownBold, EditPen, Operation, Plus, TurnOff } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type Action, type UploadProps } from 'element-plus'
import { clearAllAuth, getToken } from '@/utils/localStorage'
import { updatePasswordApi, uploadAvatarApi, userTokenAuthApi, userUpdateInfoApi } from '@/api/user-api'
import type { BaseUserUpdateDTO, BaseUserVO, UserUpdatePasswordDTO } from '@/types/user'

const router = useRouter()
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
const baseUser = ref<BaseUserVO>({})
const updatePasswordDTO = ref<UserUpdatePasswordDTO>({})
const updateInfoDTO = ref<BaseUserUpdateDTO>({})
const dialogUpdatePasswordVisible = ref(false)
const dialogUpdateInfoVisible = ref(false)
const loadingUpdatePassword = ref(false)
const loadingUpdateInfo = ref(false)
const imageUrl = ref('')

const getImageUrl = (filePath?: string) => {
  if (!filePath) return ''
  if (/^https?:\/\//.test(filePath)) return filePath
  return `${apiBaseUrl}${filePath}`
}

const loadUser = async () => {
  const token = getToken()
  if (!token) return
  const response = await userTokenAuthApi(token)
  baseUser.value = response.data ?? {}
}

const handleInfoDialogClose = () => {
  dialogUpdateInfoVisible.value = false
  updateInfoDTO.value = {}
  imageUrl.value = ''
}

const handlePasswordDialogClose = () => {
  dialogUpdatePasswordVisible.value = false
  updatePasswordDTO.value.password = ''
}

const handleUploadSuccess = async (response: any) => {
  imageUrl.value = getImageUrl(response.data.filePath)
  updateInfoDTO.value.avatar = response.data.filePath
  baseUser.value.avatar = response.data.filePath
  await uploadAvatarApi({ id: baseUser.value.id, avatar: response.data.filePath })
  ElMessage.success('头像设置成功')
}

const handleUpdatePassword = async () => {
  if (!baseUser.value.id) return
  try {
    loadingUpdatePassword.value = true
    await updatePasswordApi(baseUser.value.id, updatePasswordDTO.value)
    clearAllAuth()
    router.push('/')
    ElMessage.success('修改密码成功，请重新登录')
  } finally {
    loadingUpdatePassword.value = false
  }
}

const handleUpdateInfoOperation = async () => {
  try {
    loadingUpdateInfo.value = true
    await userUpdateInfoApi({ ...baseUser.value, ...updateInfoDTO.value })
    await loadUser()
    ElMessage.success('个人信息修改成功')
    handleInfoDialogClose()
  } finally {
    loadingUpdateInfo.value = false
  }
}

const updateInfo = () => {
  updateInfoDTO.value = { ...baseUser.value }
  imageUrl.value = getImageUrl(baseUser.value.avatar)
  dialogUpdateInfoVisible.value = true
}

const updatePassword = () => {
  dialogUpdatePasswordVisible.value = true
}

const logout = () => {
  ElMessageBox.alert('确定退出登录吗？', '操作确认', {
    confirmButtonText: 'OK',
    callback: (action: Action) => {
      if (action === 'confirm') {
        clearAllAuth()
        router.push('/')
        ElMessage.success('退出登录成功')
      }
    }
  })
}

const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp', 'image/svg+xml']
  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error('不支持的图片格式，请上传 JPG/PNG/WEBP/GIF/BMP/SVG 格式')
    return false
  }
  if (rawFile.size / 1024 / 1024 > 2) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

onMounted(loadUser)
</script>

<style scoped>
.profile-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: #111827;
  cursor: pointer;
}

.profile-form {
  display: grid;
  gap: 14px;
}

.profile-form p {
  margin: 0 0 8px;
  color: #374151;
}

.avatar-uploader {
  width: 100px;
  height: 100px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  cursor: pointer;
}

.avatar-uploader:hover {
  border-color: #409eff;
}

.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
}

.avatar {
  width: 100px;
  height: 100px;
  object-fit: cover;
}
</style>
