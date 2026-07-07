<template>
  <div class="exam-page">
    <header class="exam-header">
      <div>
        <h2>{{ paper?.title || '在线考试' }}</h2>
        <p>
          {{ paper?.subjectName || '未设置科目' }} · 总分 {{ paper?.totalScore ?? 0 }} · 时长 {{ paper?.duration ?? 0 }} 分钟
          · 第 {{ record?.attemptCount ?? 1 }} / {{ paper?.maxAttempts ?? 1 }} 次
        </p>
      </div>
      <div class="header-actions">
        <div class="timer" :class="{ danger: remainingSeconds <= 300 }">{{ timeText }}</div>
        <el-button type="primary" :loading="submitting" @click="confirmSubmit">交卷</el-button>
      </div>
    </header>

    <main v-loading="loading" class="exam-body">
      <section class="question-list">
        <el-empty v-if="!questions.length && !loading" description="试卷暂无题目" />
        <article v-for="(question, index) in questions" :id="`question-${question.id}`" :key="question.id" class="question-card">
          <div class="question-head">
            <h3>{{ index + 1 }}. {{ question.content }}</h3>
            <div>
              <el-tag>{{ typeLabel(question.type) }}</el-tag>
              <el-tag class="score-tag" type="info">{{ question.paperScore ?? question.score ?? 0 }} 分</el-tag>
            </div>
          </div>

          <el-radio-group v-if="question.type === 1" v-model="answers[question.id!]" class="option-group">
            <el-radio v-for="option in parseOptions(question.options)" :key="option.key" :label="option.key">
              {{ option.key }}. {{ option.text }}
            </el-radio>
          </el-radio-group>

          <el-checkbox-group v-else-if="question.type === 2" v-model="answers[question.id!] as string[]" class="option-group">
            <el-checkbox v-for="option in parseOptions(question.options)" :key="option.key" :label="option.key">
              {{ option.key }}. {{ option.text }}
            </el-checkbox>
          </el-checkbox-group>

          <el-radio-group v-else-if="question.type === 3" v-model="answers[question.id!]" class="option-group judge-group">
            <el-radio label="正确">正确</el-radio>
            <el-radio label="错误">错误</el-radio>
          </el-radio-group>

          <el-input
            v-else
            v-model="answers[question.id!] as string"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            placeholder="请输入主观题答案"
          />
        </article>
      </section>

      <aside class="answer-sheet">
        <h3>答题卡</h3>
        <div class="sheet-grid">
          <button
            v-for="(question, index) in questions"
            :key="question.id"
            type="button"
            :class="{ answered: isAnswered(question.id), current: currentAnchor === question.id }"
            @click="scrollToQuestion(question.id)"
          >
            {{ index + 1 }}
          </button>
        </div>
        <div class="sheet-summary">
          <span>已答 {{ answeredCount }} / {{ questions.length }}</span>
          <span>离开提醒 {{ leaveWarnings }} 次</span>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { studentExamPaperDetailApi, studentExamRecordStartApi, studentExamRecordSubmitApi } from '@/api/student-api'
import type { ExamPaper, ExamRecord, Question } from '@/types/admin'

type AnswerValue = string | string[]
type PaperQuestion = Question & { paperScore?: number }

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const submitting = ref(false)
const submitted = ref(false)
const paper = ref<ExamPaper | null>(null)
const record = ref<ExamRecord | null>(null)
const questions = ref<PaperQuestion[]>([])
const answers = reactive<Record<number, AnswerValue>>({})
const remainingSeconds = ref(0)
const leaveWarnings = ref(0)
const currentAnchor = ref<number>()
let timer: ReturnType<typeof setInterval> | undefined

const timeText = computed(() => {
  const hour = Math.floor(remainingSeconds.value / 3600)
  const minute = Math.floor((remainingSeconds.value % 3600) / 60)
  const second = remainingSeconds.value % 60
  return `${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:${String(second).padStart(2, '0')}`
})

const answeredCount = computed(() => questions.value.filter((question) => isAnswered(question.id)).length)

const loadExam = async () => {
  const paperId = Number(route.params.id)
  if (!paperId) {
    ElMessage.error('试卷不存在')
    router.replace('/user-home/dashboards')
    return
  }
  try {
    loading.value = true
    const recordResponse = await studentExamRecordStartApi(paperId)
    record.value = recordResponse.data ?? null
    const paperResponse = await studentExamPaperDetailApi(paperId)
    paper.value = paperResponse.data ?? null
    questions.value = (paper.value?.questions ?? []) as PaperQuestion[]
    questions.value.forEach((question) => {
      if (!question.id) return
      answers[question.id] = question.type === 2 ? [] : ''
    })
    remainingSeconds.value = Math.max((paper.value?.duration ?? 0) * 60, 0)
    startTimer()
    await nextTick()
    currentAnchor.value = questions.value[0]?.id
  } catch (error: any) {
    ElMessage.warning(error?.message || error?.response?.data?.message || '无法进入考试')
    router.replace('/user-home/dashboards')
  } finally {
    loading.value = false
  }
}

const startTimer = () => {
  if (timer) clearInterval(timer)
  timer = setInterval(() => {
    if (submitted.value) return
    if (remainingSeconds.value <= 1) {
      remainingSeconds.value = 0
      clearInterval(timer)
      autoSubmit()
      return
    }
    remainingSeconds.value -= 1
  }, 1000)
}

const parseOptions = (options?: string) => {
  if (!options) return []
  try {
    const parsed = JSON.parse(options)
    if (Array.isArray(parsed)) {
      return parsed.map((item, index) => ({
        key: item.label ?? String.fromCharCode(65 + index),
        text: item.content ?? item.text ?? String(item)
      }))
    }
  } catch {
    return options.split(',').filter(Boolean).map((item, index) => ({ key: String.fromCharCode(65 + index), text: item }))
  }
  return []
}

const typeLabel = (type?: number) => ({ 1: '单选题', 2: '多选题', 3: '判断题', 4: '主观题' }[type ?? 0] ?? '未知')

const isAnswered = (questionId?: number) => {
  if (!questionId) return false
  const value = answers[questionId]
  if (Array.isArray(value)) return value.length > 0
  return Boolean(value?.trim())
}

const normalizeAnswer = (value: AnswerValue | undefined) => {
  if (Array.isArray(value)) return [...value].sort().join(',')
  return value?.trim() ?? ''
}

const buildSubmitPayload = () => ({
  recordId: record.value?.id,
  paperId: paper.value?.id,
  answers: questions.value.map((question) => ({
    questionId: question.id,
    userAnswer: question.id ? normalizeAnswer(answers[question.id]) : ''
  }))
})

const submitExam = async () => {
  if (submitted.value || !record.value?.id) return
  try {
    submitting.value = true
    await studentExamRecordSubmitApi(buildSubmitPayload())
    submitted.value = true
    if (timer) clearInterval(timer)
    ElMessage.success('交卷成功')
    router.replace('/user-home/records')
  } finally {
    submitting.value = false
  }
}

const confirmSubmit = async () => {
  const unanswered = questions.value.length - answeredCount.value
  const message = unanswered > 0 ? `还有 ${unanswered} 道题未作答，确定交卷吗？` : '确定现在交卷吗？'
  await ElMessageBox.confirm(message, '交卷确认', {
    confirmButtonText: '确定交卷',
    cancelButtonText: '继续作答',
    type: unanswered > 0 ? 'warning' : 'info'
  })
  submitExam()
}

const autoSubmit = () => {
  if (submitted.value) return
  ElMessage.warning('考试时间已结束，系统已自动交卷')
  submitExam()
}

const scrollToQuestion = (questionId?: number) => {
  if (!questionId) return
  currentAnchor.value = questionId
  document.getElementById(`question-${questionId}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (submitted.value) return
  event.preventDefault()
  event.returnValue = ''
}

const handleVisibilityChange = () => {
  if (document.hidden && !submitted.value) {
    leaveWarnings.value += 1
    ElMessage.warning(`检测到离开考试页面，请保持考试页面可见（第 ${leaveWarnings.value} 次）`)
  }
}

onMounted(() => {
  loadExam()
  window.addEventListener('beforeunload', handleBeforeUnload)
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  if (timer) clearInterval(timer)
  window.removeEventListener('beforeunload', handleBeforeUnload)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.exam-page {
  min-height: 100vh;
  background: #f5f7fa;
}

.exam-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 16px 24px;
}

.exam-header h2 {
  margin: 0;
  color: #111827;
  font-size: 20px;
}

.exam-header p {
  margin: 6px 0 0;
  color: #6b7280;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.timer {
  min-width: 124px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
  padding: 9px 12px;
  text-align: center;
  font-weight: 700;
  font-variant-numeric: tabular-nums;
}

.timer.danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #dc2626;
}

.exam-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 18px;
  max-width: 1180px;
  margin: 0 auto;
  padding: 22px;
}

.question-list {
  display: grid;
  gap: 16px;
}

.question-card,
.answer-sheet {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
}

.question-card {
  scroll-margin-top: 96px;
}

.question-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 14px;
}

.question-head h3 {
  margin: 0;
  color: #111827;
  font-size: 16px;
  line-height: 1.7;
}

.score-tag {
  margin-left: 6px;
}

.option-group {
  display: grid;
  gap: 10px;
}

.option-group :deep(.el-radio),
.option-group :deep(.el-checkbox) {
  min-height: 38px;
  align-items: center;
  margin-right: 0;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px 10px;
}

.judge-group {
  grid-template-columns: repeat(2, minmax(120px, 1fr));
}

.answer-sheet {
  position: sticky;
  top: 94px;
  align-self: start;
}

.answer-sheet h3 {
  margin: 0 0 14px;
  color: #111827;
}

.sheet-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 8px;
}

.sheet-grid button {
  height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  background: #fff;
  color: #374151;
  cursor: pointer;
}

.sheet-grid button.answered {
  border-color: #409eff;
  background: #ecf5ff;
  color: #1d4ed8;
}

.sheet-grid button.current {
  border-color: #1d4ed8;
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.15);
}

.sheet-summary {
  display: grid;
  gap: 6px;
  margin-top: 14px;
  color: #6b7280;
  font-size: 13px;
}

@media (max-width: 900px) {
  .exam-body {
    grid-template-columns: 1fr;
  }

  .answer-sheet {
    position: static;
  }
}

@media (max-width: 620px) {
  .exam-header {
    align-items: stretch;
    flex-direction: column;
  }

  .header-actions {
    justify-content: space-between;
  }
}
</style>
