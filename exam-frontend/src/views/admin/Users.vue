<template>
  <div class="management-page">
    <section class="toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="账号">
          <el-input v-model="query.account" clearable placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="用户名">
          <el-input v-model="query.username" clearable placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="query.phone" clearable placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.loginStatus" clearable placeholder="全部" style="width: 120px">
            <el-option label="正常" :value="false" />
            <el-option label="已封号" :value="true" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button type="success" @click="openCreate">新增考生</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-table :data="pagedUsers" border stripe>
        <el-table-column prop="account" label="账号" min-width="130" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column label="权限" width="100">
          <template #default="{ row }">
            <el-tag :type="roleTag(row.role)">{{ roleLabel(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="头像" width="88">
          <template #default="{ row }">
            <el-avatar :src="getAvatarUrl(row.avatar)">{{ row.username.slice(0, 1) }}</el-avatar>
          </template>
        </el-table-column>
        <el-table-column label="性别" width="90">
          <template #default="{ row }">{{ row.gender === 1 ? '男' : '女' }}</template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.loginStatus ? 'danger' : 'success'">{{ row.loginStatus ? '已封号' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" :type="row.loginStatus ? 'success' : 'warning'" @click="toggleStatus(row)">
              {{ row.loginStatus ? '解封' : '封号' }}
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

    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑考生' : '新增考生'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="86px">
        <el-form-item v-if="!isEditing" label="账号" prop="account">
          <el-input v-model="form.account" />
        </el-form-item>
        <el-form-item v-if="!isEditing" label="密码" prop="password">
          <el-input v-model="form.password" show-password type="password" />
        </el-form-item>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-radio-group v-model="form.gender">
            <el-radio :value="1">男</el-radio>
            <el-radio :value="2">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="isEditing" label="状态">
          <el-switch v-model="form.loginStatus" active-text="封号" inactive-text="正常" />
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

interface UserRow {
  id?: number
  account: string
  password?: string
  username: string
  avatar?: string
  gender: number
  phone: string
  loginStatus: boolean
  role: number
}

const users = ref<UserRow[]>([])
const total = ref(0)

const query = reactive({ account: '', username: '', phone: '', loginStatus: undefined as boolean | undefined })
const page = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const isEditing = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<UserRow>({ id: 0, account: '', password: '', username: '', gender: 1, phone: '', loginStatus: false, role: 1 })

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
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  phone: [{ required: true, validator: phoneValidator, trigger: 'blur' }]
}

const pagedUsers = computed(() => users.value)

const loadUsers = async () => {
  const response = await adminUserListApi({
    pageNum: page.current,
    pageSize: page.size,
    role: 1,
    ...query
  })
  users.value = (response.data?.records ?? []).map((item) => ({
    id: item.id,
    account: item.account ?? '',
    password: item.password,
    username: item.username ?? '',
    avatar: item.avatar,
    gender: item.gender ?? 1,
    phone: item.phone ?? '',
    loginStatus: item.loginStatus ?? false,
    role: item.role ?? 1
  }))
  total.value = response.data?.total ?? 0
}

const resetForm = () => {
  Object.assign(form, { id: 0, account: '', password: '', username: '', gender: 1, phone: '', loginStatus: false, role: 1 })
}

const handleSearch = () => {
  page.current = 1
  loadUsers()
}

const handleReset = () => {
  Object.assign(query, { account: '', username: '', phone: '', loginStatus: undefined })
  page.current = 1
  loadUsers()
}

const openCreate = () => {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: UserRow) => {
  isEditing.value = true
  Object.assign(form, row, { password: '' })
  dialogVisible.value = true
}

const submitForm = async () => {
  await formRef.value?.validate()
  if (isEditing.value) {
    await adminUserUpdateApi({ ...form })
    ElMessage.success('考生信息已更新')
  } else {
    await adminUserAddApi({ ...form, role: 1 })
    ElMessage.success('考生已新增')
  }
  dialogVisible.value = false
  loadUsers()
}

const toggleStatus = async (row: UserRow) => {
  await ElMessageBox.confirm(`确定${row.loginStatus ? '解封' : '封号'}该考生吗？`, '操作确认', { type: 'warning' })
  await adminUserStatusApi(row.id!, !row.loginStatus)
  ElMessage.success('状态已更新')
  loadUsers()
}

const removeRow = async (id?: number) => {
  if (!id) return
  await ElMessageBox.confirm('确定删除该考生账号吗？', '删除确认', { type: 'warning' })
  await adminUserDeleteApi(id)
  ElMessage.success('删除成功')
  loadUsers()
}

watch(() => [page.current, page.size], loadUsers)
onMounted(loadUsers)

const getAvatarUrl = (avatar?: string) => {
  if (!avatar) return ''
  if (/^https?:\/\//.test(avatar)) return avatar
  return `${import.meta.env.VITE_API_BASE_URL}${avatar}`
}

const roleLabel = (role?: number) => ({ 1: '学生', 2: '老师', 3: '管理员' }[role ?? 0] ?? '未知')

const roleTag = (role?: number) => {
  if (role === 1) return 'success'
  if (role === 2) return 'warning'
  if (role === 3) return 'danger'
  return 'info'
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
