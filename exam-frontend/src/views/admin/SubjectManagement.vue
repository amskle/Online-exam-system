<template>
  <div class="management-page">
    <section class="toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="科目名称">
          <el-input v-model="query.name" clearable placeholder="请输入科目名称" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="openCreate">新增科目</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-table :data="subjects" border stripe>
        <el-table-column prop="name" label="科目名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeSubject(row.id)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑科目' : '新增科目'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="科目名称" prop="name">
          <el-input v-model="form.name" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="科目描述">
          <el-input v-model="form.description" type="textarea" maxlength="200" show-word-limit :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitSubject">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { subjectAddApi, subjectDeleteApi, subjectListPageApi, subjectUpdateApi } from '@/api/admin-api'

interface SubjectRow {
  id?: number
  name: string
  description: string
  createTime?: string
}

const subjects = ref<SubjectRow[]>([])
const total = ref(0)

const query = reactive({ name: '' })
const page = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const isEditing = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<SubjectRow>({ id: 0, name: '', description: '', createTime: '' })
const rules: FormRules = {
  name: [{ required: true, message: '请输入科目名称', trigger: 'blur' }]
}

const loadSubjects = async () => {
  const response = await subjectListPageApi({
    pageNum: page.current,
    pageSize: page.size,
    name: query.name
  })
  subjects.value = response.data?.records as SubjectRow[] ?? []
  total.value = response.data?.total ?? 0
}

const handleSearch = () => {
  page.current = 1
  loadSubjects()
}

const resetQuery = () => {
  query.name = ''
  page.current = 1
  loadSubjects()
}

const openCreate = () => {
  isEditing.value = false
  Object.assign(form, { id: 0, name: '', description: '', createTime: '' })
  dialogVisible.value = true
}

const openEdit = (row: SubjectRow) => {
  isEditing.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

const submitSubject = async () => {
  await formRef.value?.validate()
  if (isEditing.value) {
    await subjectUpdateApi(form)
  } else {
    await subjectAddApi(form)
  }
  dialogVisible.value = false
  ElMessage.success('保存成功')
  loadSubjects()
}

const removeSubject = async (id?: number) => {
  if (!id) return
  await ElMessageBox.confirm('确定删除该科目吗？', '删除确认', { type: 'warning' })
  await subjectDeleteApi(id)
  ElMessage.success('删除成功')
  loadSubjects()
}

watch(() => [page.current, page.size], loadSubjects)
onMounted(loadSubjects)
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
