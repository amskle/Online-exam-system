<template>
  <div class="management-page">
    <section class="toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="科目">
          <el-select v-model="query.subjectId" clearable placeholder="全部科目" style="width: 150px">
            <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="query.type" clearable placeholder="全部题型" style="width: 130px">
            <el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="query.difficulty" clearable placeholder="全部难度" style="width: 130px">
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="openCreate">新增题目</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-table :data="questions" border stripe>
        <el-table-column prop="subjectName" label="所属科目" min-width="140" />
        <el-table-column label="题型" width="110">
          <template #default="{ row }">
            <el-tag>{{ typeLabel(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="题目内容" min-width="280" show-overflow-tooltip />
        <el-table-column label="难度" width="100">
          <template #default="{ row }">
            <el-tag :type="difficultyTag(row.difficulty)">{{ difficultyLabel(row.difficulty) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="90" />
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="removeQuestion(row.id)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="isEditing ? '编辑题目' : '新增题目'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="科目" prop="subject">
              <el-select v-model="form.subjectId" placeholder="请选择科目" @change="syncSubjectName">
                <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="题型" prop="type">
              <el-select v-model="form.type" placeholder="请选择题型" @change="handleTypeChange">
                <el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="form.difficulty" placeholder="请选择难度">
                <el-option label="简单" :value="1" />
                <el-option label="中等" :value="2" />
                <el-option label="困难" :value="3" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="题目内容" prop="content">
          <el-input v-model="form.content" type="textarea" maxlength="5000" show-word-limit :rows="4" />
        </el-form-item>
        <el-form-item v-if="needsOptions" label="选项 JSON">
          <el-input v-model="form.options" type="textarea" :rows="4" placeholder='如：[{"label":"A","content":"选项内容"}]' />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model="form.answer" placeholder="单选填 A，多选填 A,B，判断填 正确/错误，主观题填参考答案" />
        </el-form-item>
        <el-form-item label="答案解析">
          <el-input v-model="form.analysis" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="分值" prop="score">
          <el-input-number v-model="form.score" :min="1" :max="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitQuestion">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { questionAddApi, questionDeleteApi, questionListPageApi, questionUpdateApi, subjectListApi } from '@/api/admin-api'
import type { Subject } from '@/types/admin'

interface QuestionRow {
  id?: number
  subjectId?: number
  subjectName?: string
  type: number
  content: string
  difficulty: number
  score: number
  answer: string
  options?: string
  analysis?: string
  createTime?: string
}

const subjects = ref<Subject[]>([])
const questionTypes = [
  { label: '单选题', value: 1 },
  { label: '多选题', value: 2 },
  { label: '判断题', value: 3 },
  { label: '主观题', value: 4 }
]

const questions = ref<QuestionRow[]>([])
const total = ref(0)

const query = reactive({ subjectId: undefined as number | undefined, type: undefined as number | undefined, difficulty: undefined as number | undefined })
const page = reactive({ current: 1, size: 10 })
const dialogVisible = ref(false)
const isEditing = ref(false)
const formRef = ref<FormInstance>()
const form = reactive<QuestionRow>({
  id: 0,
  subjectId: undefined,
  subjectName: '',
  type: 1,
  content: '',
  difficulty: 1,
  score: 5,
  answer: '',
  options: '',
  analysis: '',
  createTime: ''
})

const rules: FormRules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  content: [{ required: true, message: '请输入题目内容', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }],
  score: [{ required: true, message: '请输入分值', trigger: 'change' }]
}

const needsOptions = computed(() => form.type === 1 || form.type === 2)

const loadSubjects = async () => {
  const response = await subjectListApi()
  subjects.value = response.data ?? []
}

const loadQuestions = async () => {
  const response = await questionListPageApi({
    pageNum: page.current,
    pageSize: page.size,
    ...query
  })
  questions.value = response.data?.records as QuestionRow[] ?? []
  total.value = response.data?.total ?? 0
}

const difficultyTag = (difficulty: number) => {
  if (difficulty === 1) return 'success'
  if (difficulty === 2) return 'warning'
  return 'danger'
}

const difficultyLabel = (difficulty: number) => ({ 1: '简单', 2: '中等', 3: '困难' }[difficulty] ?? '未知')
const typeLabel = (type: number) => ({ 1: '单选题', 2: '多选题', 3: '判断题', 4: '主观题' }[type] ?? '未知')

const handleSearch = () => {
  page.current = 1
  loadQuestions()
}

const resetQuery = () => {
  Object.assign(query, { subjectId: undefined, type: undefined, difficulty: undefined })
  page.current = 1
  loadQuestions()
}

const resetForm = () => {
  Object.assign(form, { id: 0, subjectId: undefined, subjectName: '', type: 1, content: '', difficulty: 1, score: 5, answer: '', options: '', analysis: '', createTime: '' })
}

const handleTypeChange = () => {
  if (!needsOptions.value) form.options = ''
}

const syncSubjectName = () => {
  const subject = subjects.value.find((item) => item.id === form.subjectId)
  form.subjectName = subject?.name ?? ''
}

const openCreate = () => {
  isEditing.value = false
  resetForm()
  dialogVisible.value = true
}

const openEdit = (row: QuestionRow) => {
  isEditing.value = true
  Object.assign(form, row)
  dialogVisible.value = true
}

const submitQuestion = async () => {
  await formRef.value?.validate()
  if (!needsOptions.value) form.options = ''
  if (isEditing.value) {
    await questionUpdateApi(form)
  } else {
    await questionAddApi(form)
  }
  dialogVisible.value = false
  ElMessage.success('题目保存成功')
  loadQuestions()
}

const removeQuestion = async (id?: number) => {
  if (!id) return
  await ElMessageBox.confirm('确定删除该题目吗？', '删除确认', { type: 'warning' })
  await questionDeleteApi(id)
  ElMessage.success('删除成功')
  loadQuestions()
}

watch(() => [page.current, page.size], loadQuestions)
onMounted(async () => {
  await loadSubjects()
  await loadQuestions()
})
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
