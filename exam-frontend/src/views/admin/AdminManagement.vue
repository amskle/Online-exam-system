<template>
  <div class="management-page">
    <section class="toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="账号">
          <el-input v-model="query.account" clearable placeholder="请输入管理员账号" />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="query.username" clearable placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="query.phone" clearable placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.loginStatus" clearable placeholder="全部" style="width: 120px">
            <el-option label="正常" :value="false" />
            <el-option label="已停用" :value="true" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="openCreate">新增管理员</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-table :data="pagedAdmins" border stripe>
        <el-table-column label="头像" width="88">
          <template #default="{ row }">
            <el-avatar :src="getAvatarUrl(row.avatar)">{{ row.username?.slice(0, 1) || '管' }}</el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="account" label="账号" min-width="130" />
        <el-table-column prop="username" label="姓名" min-width="120" />
        <el-table-column label="性别" width="90">
          <template #default="{ row }">{{ row.gender === 1 ? '男' : '女' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.loginStatus ? 'danger' : 'success'">{{ row.loginStatus ? '已停用' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.loginStatus ? 'success' : 'warning'" @click="toggleStatus(row)">
              {{ row.loginStatus ? '启用' : '停用' }}
            </el-button>
            <el-button size="small" type="danger" @click="removeRow(row.id)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page.current"
        v-model:page-size="page.size"
        class="pagination"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
      />
    </section>

    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑管理员' : '新增管理员'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-form-item v-if="!isEditing" label="账号" prop="account">
          <el-input v-model="form.account" />
        </el-form-item>
        <el-form-item v-if="!isEditing" label="密码" prop="password">
          <el-input v-model="form.password" show-password type="password" />
        </el-form-item>
        <el-form-item label="姓名" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="isEditing" label="状态">
          <el-switch v-model="form.loginStatus" active-text="停用" inactive-text="正常" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { adminUserAddApi, adminUserDeleteApi, adminUserListApi, adminUserStatusApi, adminUserUpdateApi } from '@/api/admin-api'

interface AdminRow {
  id?: number
  account: string
  password?: string
  username: string
  avatar?: string
  gender: number
  phone: string
  loginStatus: boolean
}

const admins = ref<AdminRow[]>([])
const total = ref(0)
const query = reactive({ account: '', username: '', phone: '', loginStatus: undefined as boolean | undefined })
const page = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const isEditing = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<AdminRow>({ id: 0, account: '', password: '', username: '', gender: 1, phone: '', loginStatus: false })

const phoneValidator = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号'))
    return
  }
  callback()
}

const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, min: 6, max: 20, message: '密码长度为 6-20 位', trigger: 'blur' }],
  username: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [{ required: true, validator: phoneValidator, trigger: 'blur' }]
}

const pagedAdmins = computed(() => admins.value)

const loadAdmins = async () => {
  const response = await adminUserListApi({
    pageNum: page.current,
    pageSize: page.size,
    role: 3,
    ...query
  })
  admins.value = (response.data?.records ?? []).map((item) => ({
    id: item.id,
    account: item.account ?? '',
    password: item.password,
    username: item.username ?? '',
    avatar: item.avatar,
    gender: item.gender ?? 1,
    phone: item.phone ?? '',
    loginStatus: item.loginStatus ?? false
  }))
  total.value = response.data?.total ?? 0
}

const handleSearch = () => {
  page.current = 1
  loadAdmins()
}

const handleReset = () => {
  Object.assign(query, { account: '', username: '', phone: '', loginStatus: undefined })
  page.current = 1
  loadAdmins()
}

const resetForm = () => {
  Object.assign(form, { id: 0, account: '', password: '', username: '', gender: 1, phone: '', loginStatus: false })
}

const openCreate = () => {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: AdminRow) => {
  isEditing.value = true
  Object.assign(form, row, { password: '' })
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  if (isEditing.value) {
    await adminUserUpdateApi({ ...form, role: 3 })
  } else {
    await adminUserAddApi({ ...form, role: 3 })
  }
  dialogVisible.value = false
  ElMessage.success('管理员保存成功')
  loadAdmins()
}

const toggleStatus = async (row: AdminRow) => {
  await ElMessageBox.confirm(`确定${row.loginStatus ? '启用' : '停用'}该管理员吗？`, '操作确认', { type: 'warning' })
  await adminUserStatusApi(row.id!, !row.loginStatus)
  ElMessage.success('状态已更新')
  loadAdmins()
}

const removeRow = async (id?: number) => {
  if (!id) return
  await ElMessageBox.confirm('确定删除该管理员账号吗？', '删除确认', { type: 'warning' })
  await adminUserDeleteApi(id)
  ElMessage.success('删除成功')
  loadAdmins()
}

watch(() => [page.current, page.size], loadAdmins)
onMounted(loadAdmins)

const getAvatarUrl = (avatar?: string) => {
  if (!avatar) return ''
  if (/^https?:\/\//.test(avatar)) return avatar
  return `${import.meta.env.VITE_API_BASE_URL}${avatar}`
}
</script>

<style scoped>
.management-page {
  display: grid;
  gap: 16px;
}

.toolbar,
.table-panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.toolbar :deep(.el-form-item) {
  margin-bottom: 0;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
