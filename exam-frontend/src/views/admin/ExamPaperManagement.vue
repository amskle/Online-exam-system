<template>
  <div class="management-page">
    <section class="toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="标题">
          <el-input v-model="query.title" clearable placeholder="请输入试卷标题" />
        </el-form-item>
        <el-form-item label="科目">
          <el-select v-model="query.subjectId" clearable placeholder="全部科目" style="width: 150px">
            <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 130px">
            <el-option label="未发布" :value="0" />
            <el-option label="已发布" :value="1" />
            <el-option label="已结束" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="success" @click="openCreate">新增试卷</el-button>
          <el-button type="warning" @click="openAuto">自动组卷</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-table :data="papers" border stripe>
        <el-table-column prop="title" label="试卷标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="subjectName" label="所属科目" min-width="140" />
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column prop="duration" label="时长(分钟)" width="110" />
        <el-table-column prop="maxAttempts" label="考试次数" width="100">
          <template #default="{ row }">{{ row.maxAttempts ?? 1 }} 次</template>
        </el-table-column>
        <el-table-column label="有效期" min-width="240">
          <template #default="{ row }">{{ row.startTime ? `${row.startTime} ~ ${row.endTime || ''}` : '不限' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="primary" @click="openPrint(row)">打印</el-button>
            <el-button size="small" type="danger" @click="removePaper(row.id)">删除</el-button>
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

    <el-dialog v-model="paperDialogVisible" :title="isEditing ? '编辑试卷' : '新增试卷'" width="860px">
      <el-form ref="paperFormRef" :model="paperForm" :rules="paperRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="试卷标题" prop="title">
              <el-input v-model="paperForm.title" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属科目" prop="subjectId">
              <el-select v-model="paperForm.subjectId" @change="syncPaperSubject">
                <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="6">
            <el-form-item label="考试时长" prop="duration">
              <el-input-number v-model="paperForm.duration" :min="1" :max="600" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="试卷总分" prop="totalScore">
              <el-input-number v-model="paperForm.totalScore" :min="1" :max="9999" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="考试次数" prop="maxAttempts">
              <el-input-number v-model="paperForm.maxAttempts" :min="1" :max="99" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="状态">
              <el-select v-model="paperForm.status">
                <el-option label="未发布" :value="0" />
                <el-option label="已发布" :value="1" />
                <el-option label="已结束" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始时间">
              <el-date-picker v-model="paperForm.startTime" value-format="YYYY-MM-DD HH:mm:ss" type="datetime" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间">
              <el-date-picker v-model="paperForm.endTime" value-format="YYYY-MM-DD HH:mm:ss" type="datetime" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">试卷题目</el-divider>
        <div class="paper-question-head">
          <span>已选 {{ paperForm.questions?.length ?? 0 }} 题，当前分值 {{ selectedScore }} 分</span>
          <el-button type="primary" @click="openQuestionPicker">添加题目</el-button>
        </div>
        <el-table :data="paperForm.questions" border>
          <el-table-column type="index" label="序号" width="70" />
          <el-table-column prop="content" label="题目内容" min-width="260" show-overflow-tooltip />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column prop="score" label="基础分值" width="100" />
          <el-table-column label="试卷分值" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.paperScore" :min="1" :max="100" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }">
              <el-button size="small" type="danger" @click="removeSelectedQuestion(row.id)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>
      <template #footer>
        <el-button @click="paperDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitPaper">确定提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="questionPickerVisible" title="选择题目" width="980px" class="question-picker-dialog">
      <div class="question-picker">
        <div class="picker-toolbar">
          <el-input
            v-model="questionFilter.keyword"
            clearable
            placeholder="搜索题目内容"
            style="width: 260px"
          />
          <el-select v-model="questionFilter.type" clearable placeholder="全部题型" style="width: 130px">
            <el-option label="单选题" :value="1" />
            <el-option label="多选题" :value="2" />
            <el-option label="判断题" :value="3" />
            <el-option label="主观题" :value="4" />
          </el-select>
          <el-select v-model="questionFilter.difficulty" clearable placeholder="全部难度" style="width: 130px">
            <el-option label="简单" :value="1" />
            <el-option label="中等" :value="2" />
            <el-option label="困难" :value="3" />
          </el-select>
          <el-checkbox v-model="questionFilter.hideSelected">隐藏已选</el-checkbox>
          <span class="picker-count">已勾选 {{ selectedPickerQuestions.length }} 题 / 当前 {{ filteredQuestionBank.length }} 题</span>
        </div>
      <el-table :data="filteredQuestionBank" border height="520" @selection-change="selectedPickerQuestions = $event">
        <el-table-column type="selection" width="48" />
        <el-table-column prop="subjectName" label="科目" width="130" />
        <el-table-column label="题型" width="90">
          <template #default="{ row }">{{ typeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column prop="content" label="题目内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="难度" width="90">
          <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
        </el-table-column>
        <el-table-column prop="score" label="分值" width="80" />
      </el-table>
      </div>
      <template #footer>
        <el-button @click="questionPickerVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmPickQuestions">确认添加</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="autoDialogVisible" title="自动组卷" width="760px">
      <el-form :model="autoForm" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="试卷标题">
              <el-input v-model="autoForm.title" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属科目">
              <el-select v-model="autoForm.subjectId" @change="syncAutoSubject">
                <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="总分">
              <el-input-number v-model="autoForm.totalScore" :min="1" :max="9999" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="时长">
              <el-input-number v-model="autoForm.duration" :min="1" :max="600" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="考试次数">
              <el-input-number v-model="autoForm.maxAttempts" :min="1" :max="99" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-table :data="autoForm.configs" border>
          <el-table-column label="题型" width="90">
            <template #default="{ row }">{{ typeLabel(row.type) }}</template>
          </el-table-column>
          <el-table-column label="数量" width="110">
            <template #default="{ row }"><el-input-number v-model="row.count" :min="0" :max="50" size="small" /></template>
          </el-table-column>
          <el-table-column label="每题分值" width="130">
            <template #default="{ row }"><el-input-number v-model="row.scorePerQuestion" :min="1" :max="100" size="small" /></template>
          </el-table-column>
          <el-table-column label="简单" width="110">
            <template #default="{ row }"><el-input-number v-model="row.easy" :min="0" :max="50" size="small" /></template>
          </el-table-column>
          <el-table-column label="中等" width="110">
            <template #default="{ row }"><el-input-number v-model="row.medium" :min="0" :max="50" size="small" /></template>
          </el-table-column>
          <el-table-column label="困难" width="110">
            <template #default="{ row }"><el-input-number v-model="row.hard" :min="0" :max="50" size="small" /></template>
          </el-table-column>
        </el-table>
        <div class="auto-summary">
          <span>总题数：{{ autoTotalCount }}</span>
          <span>配置总分：{{ autoTotalScore }}</span>
          <el-tag v-if="autoTotalScore !== autoForm.totalScore" type="warning">与设定总分不一致</el-tag>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="autoDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitAutoPaper">开始组卷</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="printDialogVisible" title="打印预览" width="820px">
      <div v-if="printPaper" class="print-preview">
        <h2>{{ printPaper.title }}</h2>
        <p>{{ printPaper.subjectName }} · 总分 {{ printPaper.totalScore }} · 时长 {{ printPaper.duration }} 分钟 · 限考 {{ printPaper.maxAttempts ?? 1 }} 次</p>
        <div v-for="(group, type) in groupedPrintQuestions" :key="type" class="print-group">
          <h3>{{ typeLabel(Number(type)) }}</h3>
          <div v-for="(question, index) in group" :key="question.id" class="print-question">
            <strong>{{ index + 1 }}. {{ question.content }}（{{ question.paperScore }}分）</strong>
            <p v-if="question.type === 4">答：____________________________________________</p>
            <p v-else>答案区：（　　　）</p>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="printDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="printPaperWindow">确定打印</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  examPaperAddApi,
  examPaperAutoGenerateApi,
  examPaperDeleteApi,
  examPaperDetailApi,
  examPaperListPageApi,
  examPaperUpdateApi,
  questionListPageApi,
  subjectListApi
} from '@/api/admin-api'
import type { ExamPaper, Question, Subject } from '@/types/admin'

type PaperQuestion = Question & { paperScore?: number }

const subjects = ref<Subject[]>([])
const questionBank = ref<PaperQuestion[]>([])
const papers = ref<ExamPaper[]>([])
const total = ref(0)

const query = reactive({ title: '', subjectId: undefined as number | undefined, status: undefined as number | undefined })
const page = reactive({ current: 1, size: 10 })
const paperDialogVisible = ref(false)
const questionPickerVisible = ref(false)
const autoDialogVisible = ref(false)
const printDialogVisible = ref(false)
const isEditing = ref(false)
const paperFormRef = ref<FormInstance>()
const selectedPickerQuestions = ref<PaperQuestion[]>([])
const printPaper = ref<ExamPaper | null>(null)
const questionFilter = reactive({
  keyword: '',
  type: undefined as number | undefined,
  difficulty: undefined as number | undefined,
  hideSelected: false
})

const paperForm = reactive<ExamPaper>({
  id: undefined,
  title: '',
  subjectId: undefined,
  subjectName: '',
  totalScore: 100,
  duration: 120,
  maxAttempts: 1,
  status: 0,
  startTime: '',
  endTime: '',
  questions: []
})

const autoForm = reactive({
  title: '',
  subjectId: undefined as number | undefined,
  subjectName: '',
  totalScore: 100,
  duration: 120,
  maxAttempts: 1,
  configs: [
    { type: 1, count: 10, scorePerQuestion: 5, easy: 4, medium: 4, hard: 2 },
    { type: 2, count: 5, scorePerQuestion: 6, easy: 2, medium: 2, hard: 1 },
    { type: 3, count: 5, scorePerQuestion: 4, easy: 3, medium: 2, hard: 0 },
    { type: 4, count: 0, scorePerQuestion: 10, easy: 0, medium: 0, hard: 0 }
  ]
})

const paperRules: FormRules = {
  title: [{ required: true, message: '请输入试卷标题', trigger: 'blur' }],
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  totalScore: [{ required: true, message: '请输入总分', trigger: 'change' }],
  duration: [{ required: true, message: '请输入时长', trigger: 'change' }],
  maxAttempts: [{ required: true, message: '请输入考试次数', trigger: 'change' }]
}

const selectedScore = computed(() => (paperForm.questions ?? []).reduce((sum, item) => sum + (item.paperScore ?? item.score ?? 0), 0))
const autoTotalCount = computed(() => autoForm.configs.reduce((sum, item) => sum + item.count, 0))
const autoTotalScore = computed(() => autoForm.configs.reduce((sum, item) => sum + item.count * item.scorePerQuestion, 0))
const selectedQuestionIds = computed(() => new Set((paperForm.questions ?? []).map((item) => item.id)))
const filteredQuestionBank = computed(() => {
  const keyword = questionFilter.keyword.trim().toLowerCase()
  return questionBank.value.filter((item) => {
    const content = item.content?.toLowerCase() ?? ''
    return (!keyword || content.includes(keyword))
      && (!questionFilter.type || item.type === questionFilter.type)
      && (!questionFilter.difficulty || item.difficulty === questionFilter.difficulty)
      && (!questionFilter.hideSelected || !selectedQuestionIds.value.has(item.id))
  })
})
const groupedPrintQuestions = computed<Record<number, PaperQuestion[]>>(() => {
  const groups: Record<number, PaperQuestion[]> = {}
  printPaper.value?.questions?.forEach((item) => {
    const key = item.type ?? 0
    groups[key] = groups[key] ?? []
    groups[key].push(item)
  })
  return groups
})

const statusLabel = (status?: number) => ({ 0: '未发布', 1: '已发布', 2: '已结束' }[status ?? 0] ?? '未知')
const statusTag = (status?: number) => (status === 1 ? 'success' : status === 2 ? 'danger' : 'info')
const typeLabel = (type?: number) => ({ 1: '单选题', 2: '多选题', 3: '判断题', 4: '主观题' }[type ?? 0] ?? '未知')
const difficultyLabel = (difficulty?: number) => ({ 1: '简单', 2: '中等', 3: '困难' }[difficulty ?? 0] ?? '未知')

const loadSubjects = async () => {
  const response = await subjectListApi()
  subjects.value = response.data ?? []
}

const loadPapers = async () => {
  const response = await examPaperListPageApi({
    pageNum: page.current,
    pageSize: page.size,
    ...query
  })
  papers.value = response.data?.records ?? []
  total.value = response.data?.total ?? 0
}

const loadQuestions = async () => {
  const response = await questionListPageApi({
    pageNum: 1,
    pageSize: 1000,
    subjectId: paperForm.subjectId
  })
  questionBank.value = (response.data?.records ?? []).map((item) => ({ ...item, paperScore: item.score }))
}

const handleSearch = () => {
  page.current = 1
  loadPapers()
}

const resetQuery = () => {
  Object.assign(query, { title: '', subjectId: undefined, status: undefined })
  page.current = 1
  loadPapers()
}

const resetPaperForm = () => {
  Object.assign(paperForm, {
    id: undefined,
    title: '',
    subjectId: undefined,
    subjectName: '',
    totalScore: 100,
    duration: 120,
    maxAttempts: 1,
    status: 0,
    startTime: '',
    endTime: '',
    questions: []
  })
}

const syncPaperSubject = () => {
  const subject = subjects.value.find((item) => item.id === paperForm.subjectId)
  paperForm.subjectName = subject?.name ?? ''
  paperForm.questions = []
}

const syncAutoSubject = () => {
  const subject = subjects.value.find((item) => item.id === autoForm.subjectId)
  autoForm.subjectName = subject?.name ?? ''
}

const openCreate = () => {
  isEditing.value = false
  resetPaperForm()
  paperDialogVisible.value = true
}

const openEdit = async (row: ExamPaper) => {
  isEditing.value = true
  const response = await examPaperDetailApi(row.id!)
  Object.assign(paperForm, response.data, { questions: response.data?.questions?.map((item) => ({ ...item })) ?? [] })
  paperDialogVisible.value = true
}

const openQuestionPicker = async () => {
  if (!paperForm.subjectId) {
    ElMessage.warning('请先选择试卷科目')
    return
  }
  Object.assign(questionFilter, { keyword: '', type: undefined, difficulty: undefined, hideSelected: false })
  selectedPickerQuestions.value = []
  await loadQuestions()
  questionPickerVisible.value = true
}

const confirmPickQuestions = () => {
  selectedPickerQuestions.value.forEach((question) => {
    if (!paperForm.questions?.some((item) => item.id === question.id)) {
      paperForm.questions?.push({ ...question, paperScore: question.paperScore ?? question.score })
    }
  })
  questionPickerVisible.value = false
  ElMessage.success('题目已加入试卷')
}

const removeSelectedQuestion = (id?: number) => {
  paperForm.questions = paperForm.questions?.filter((item) => item.id !== id) ?? []
}

const buildPaperPayload = () => ({
  ...paperForm,
  questions: (paperForm.questions ?? []).map((item) => ({
    questionId: item.id,
    paperScore: item.paperScore ?? item.score
  }))
})

const submitPaper = async () => {
  await paperFormRef.value?.validate()
  if (!paperForm.questions?.length) {
    ElMessage.warning('请至少添加一道题目')
    return
  }
  if (isEditing.value) {
    await examPaperUpdateApi(buildPaperPayload())
  } else {
    await examPaperAddApi(buildPaperPayload())
  }
  paperDialogVisible.value = false
  ElMessage.success('试卷保存成功')
  loadPapers()
}

const removePaper = async (id?: number) => {
  if (!id) return
  await ElMessageBox.confirm('确定删除该试卷吗？', '删除确认', { type: 'warning' })
  await examPaperDeleteApi(id)
  ElMessage.success('删除成功')
  loadPapers()
}

const openAuto = () => {
  autoForm.title = ''
  autoForm.maxAttempts = 1
  autoDialogVisible.value = true
}

const submitAutoPaper = async () => {
  const enabledConfigs = autoForm.configs.filter((item) => item.count > 0)
  if (!autoForm.title || !autoForm.subjectId) {
    ElMessage.warning('请填写试卷标题和所属科目')
    return
  }
  if (!enabledConfigs.length) {
    ElMessage.warning('至少配置一种题型')
    return
  }
  const invalid = enabledConfigs.find((item) => item.easy + item.medium + item.hard !== item.count)
  if (invalid) {
    ElMessage.warning(`${typeLabel(invalid.type)} 的难度分布合计必须等于题目数量`)
    return
  }
  if (autoTotalScore.value !== autoForm.totalScore) {
    await ElMessageBox.confirm('配置总分与设定总分不一致，是否继续生成？', '自动组卷确认', { type: 'warning' })
  }
  await examPaperAutoGenerateApi({
    title: autoForm.title,
    subjectId: autoForm.subjectId,
    subjectName: autoForm.subjectName,
    totalScore: autoForm.totalScore,
    duration: autoForm.duration,
    maxAttempts: autoForm.maxAttempts,
    typeConfigs: enabledConfigs.map((item) => ({
      type: item.type,
      count: item.count,
      scorePerQuestion: item.scorePerQuestion,
      difficultyDist: { 1: item.easy, 2: item.medium, 3: item.hard }
    }))
  })
  autoDialogVisible.value = false
  ElMessage.success('自动组卷成功')
  loadPapers()
}

const openPrint = async (row: ExamPaper) => {
  const response = await examPaperDetailApi(row.id!)
  printPaper.value = response.data ?? null
  printDialogVisible.value = true
}

const printPaperWindow = () => {
  window.print()
}

watch(() => [page.current, page.size], loadPapers)
onMounted(async () => {
  await loadSubjects()
  await loadPapers()
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

.paper-question-head,
.auto-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.auto-summary {
  justify-content: flex-start;
  margin-top: 14px;
  color: #374151;
}

.print-preview {
  color: #111827;
  line-height: 1.8;
}

.print-preview h2 {
  text-align: center;
  margin: 0;
}

.print-preview > p {
  text-align: center;
  color: #6b7280;
}

.print-group {
  margin-top: 18px;
}

.print-question {
  padding: 10px 0;
  border-bottom: 1px dashed #d1d5db;
}

.question-picker {
  display: grid;
  gap: 12px;
}

.picker-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  padding: 12px;
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.picker-count {
  margin-left: auto;
  color: #6b7280;
  font-size: 13px;
}

:deep(.question-picker-dialog .el-dialog__body) {
  padding-bottom: 10px;
}

:deep(.question-picker-dialog .el-dialog__footer) {
  position: sticky;
  bottom: 0;
  z-index: 2;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}
</style>
