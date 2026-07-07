<template>
  <div class="main-container">
    <header class="header-container">
      <div class="logo">
        <img class="image" src="A:\Online-exam-system\exam-frontend\src\assets\logo.png" alt="" srcset="">
        <div class="logo-text">欢迎{{ baseUser?.username }}</div>
      </div>
      <div class="user-info-container">
        <el-dropdown :hide-on-click="false">
          <span class="el-dropdown-link">
            <el-avatar :size="30" v-if="baseUser?.avatar" :src="getImageUrl(baseUser.avatar)" />
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
  <el-dialog v-model="dialogUpdateInfoVisible" title="修改个人信息" width="400" :before-close="handleInfoDialogClose">
    <div>
      <div>
        <p>头像</p>
        <el-upload class="avatar-uploader" action="http://localhost:8077/files/upload" :show-file-list="false"
          :before-upload="beforeAvatarUpload" :on-success="handleUploadSuccess">
          <img v-if="imageUrl" :src="imageUrl" class="avatar" />

          <el-icon v-else class="avatar-uploader-icon">
            <Plus />
          </el-icon>
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
        <p>性别</p>
        <el-radio-group v-model="updateInfoDTO.gender" size="large" fill="#409eff">
          <el-radio-button label="男" :value="Number(1)" />
          <el-radio-button label="女" :value="Number(2)" />
        </el-radio-group>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleInfoDialogClose">取消操作</el-button>
        <el-button type="primary" :loading-icon="Eleme" :loading="loadingUpdateInfo" @click="handleUpdateInfoOperation">
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
import { BaseUserVO, UserUpdatePasswordDTO, BaseUserUpdateDTO } from '@/types/user'
import { ElMessage, ElMessageBox, UploadProps } from 'element-plus'
import type { Action } from 'element-plus'
import { updatePasswordApi, userTokenAuthApi, userUpdateInfoApi, uploadAvatarApi } from '@/api/user-api'
const router = useRouter()
const baseUser = ref<BaseUserVO>({})
const updatePasswordDTO = ref<UserUpdatePasswordDTO>({})
const updateInfoDTO = ref<BaseUserUpdateDTO>({})
const handleInfoDialogClose = () => {
  dialogUpdateInfoVisible.value = false
  updateInfoDTO.value = {}
  imageUrl.value = ''
}
const handlePasswordDialogClose = () => {
  dialogUpdatePasswordVisible.value = false
  updatePasswordDTO.value.password = ''
}
const dialogUpdatePasswordVisible = ref(false)
const loadingUpdatePassword = ref(false)
const loadingUpdateInfo = ref(false)
const dialogUpdateInfoVisible = ref(false)
const imageUrl = ref('')
const getImageUrl = (filePath: string) => {
  return `${import.meta.env.VITE_API_BASE_URL}${filePath}`
}
// const handleAvatarChange: UploadProps['onChange'] = async (response: any) => {
//   try {
//     if (response) {
//       imageUrl.value = getImageUrl(response.data.filePath)
//       const baseUserUpdateDTO = {
//         id: baseUser.value.id,
//         avatar: response.data.filePath
//       }
//       await uploadAvatarApi(baseUserUpdateDTO)
//       ElMessage.success(`头像设置成功`)
//     }
//   } catch (erreo: any) {
//     ElMessage.error(`${erreo.message}`)
//   }
// }

const handleUploadSuccess = async (response: any) => {
  imageUrl.value = getImageUrl(response.data.filePath)
  updateInfoDTO.value.avatar = response.data.filePath

  baseUser.value.avatar = response.data.filePath

  await uploadAvatarApi({
    id: baseUser.value.id,
    avatar: response.data.filePath
  })

  ElMessage.success("头像设置成功")
}
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

const handleUpdateInfoOperation = async () => {
  try {
    const baseUserUpdateDTO = {
      ...baseUser.value
    }
    await userUpdateInfoApi(baseUserUpdateDTO);
    ElMessage.success(`个人信息修改成功`)
    handleInfoDialogClose()
  } catch (error: any) {
    ElMessage.error(`${error.message}`)
  } finally {
    loadingUpdateInfo.value = false
  }
}
// 修改信息
const updateInfo = () => {
  updateInfoDTO.value = { ...baseUser.value }
  imageUrl.value = getImageUrl(baseUser.value?.avatar ?? '')
  dialogUpdateInfoVisible.value = true
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

const beforeAvatarUpload: UploadProps['beforeUpload'] = (rawFile) => {
  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp', 'image/gif', 'image/bmp', 'image/svg+xml']
  if (!allowedTypes.includes(rawFile.type)) {
    ElMessage.error('不支持的图片格式，请上传 JPG/PNG/WEBP/GIF/BMP/SVG 格式')
    return false
  } else if (rawFile.size / 1024 / 1024 > 2) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}
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

.avatar-uploader {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 100px;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
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
  display: block;
  object-fit: cover;
}

.avatar-uploader .el-upload {
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
}

.avatar-uploader .el-upload:hover {
  border-color: var(--el-color-primary);
}

.el-icon.avatar-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 178px;
  height: 178px;
  text-align: center;
}
</style>