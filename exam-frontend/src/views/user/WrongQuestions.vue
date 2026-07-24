<template>
  <div class="student-page">
    <section class="toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="掌握状态">
          <el-select v-model="query.mastered" clearable placeholder="全部错题" style="width: 150px">
            <el-option label="未掌握" :value="false" />
            <el-option label="已掌握" :value="true" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">筛选</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section v-loading="loading" class="wrong-list">
      <el-empty v-if="!wrongQuestions.length && !loading" description="暂无错题记录" />
      <article v-for="item in wrongQuestions" :key="item.id" class="wrong-card">
        <div class="wrong-head">
          <div>
            <el-tag>{{ typeLabel(item.type) }}</el-tag>
            <el-tag class="subject-tag" type="info">{{ item.subjectName || '未设置科目' }}</el-tag>
          </div>
          <div class="wrong-actions">
            <el-button type="primary" size="small" @click="askAiTutor(item)">🤖 AI 答疑</el-button>
            <el-switch
              :model-value="item.mastered"
              active-text="已掌握"
              inactive-text="未掌握"
              @change="(value: string | number | boolean) => updateMastered(item, Boolean(value))"
            />
            <el-button type="danger" size="small" @click="removeWrongQuestion(item)">删除</el-button>
          </div>
        </div>
        <h3>{{ item.content }}</h3>
        <div v-if="item.type !== 4" class="options">
          <span v-for="option in parseOptions(item.options)" :key="option.key" :class="optionClass(item, option.key)">
            {{ option.key }}. {{ option.text }}
          </span>
        </div>
        <div class="answer-grid">
          <div>
            <span>我的答案</span>
            <strong>{{ item.userAnswer || '未作答' }}</strong>
          </div>
          <div>
            <span>正确答案</span>
            <strong>{{ item.correctAnswer || '-' }}</strong>
          </div>
          <div>
            <span>错误次数</span>
            <strong>{{ item.wrongCount ?? 1 }} 次</strong>
          </div>
          <div>
            <span>最近错误</span>
            <strong>{{ item.lastWrongTime || '-' }}</strong>
          </div>
        </div>
        <p v-if="item.analysis" class="analysis">解析：{{ item.analysis }}</p>
      </article>
    </section>

    <el-pagination
      v-model:current-page="page.current"
      v-model:page-size="page.size"
      class="pagination"
      layout="total, sizes, prev, pager, next, jumper"
      :page-sizes="[5, 10, 20, 50]"
      :total="total"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch, inject } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { studentWrongQuestionDeleteApi, studentWrongQuestionListApi, studentWrongQuestionMasteredApi } from '@/api/student-api'
import type { WrongQuestion } from '@/types/admin'

const loading = ref(false)
const wrongQuestions = ref<WrongQuestion[]>([])
const total = ref(0)
const query = reactive({ mastered: undefined as boolean | undefined })
const page = reactive({ current: 1, size: 5 })

/** 共享的 Tutor 上下文 — 由 StudentLayout provide，用于触发 AI 答疑 */
interface TutorCtxInject {
  questionId: number | null
  questionContent: string
  studentAnswer: string
  triggerOpen: number
}
const tutorCtx = inject<TutorCtxInject | null>('tutorContext', null)

/** 点击某道错题的 "AI 答疑" */
const askAiTutor = (item: WrongQuestion) => {
  if (!tutorCtx || !item.id) return
  tutorCtx.questionId = item.id
  tutorCtx.questionContent = item.content ?? ''
  tutorCtx.studentAnswer = item.userAnswer ?? ''
  tutorCtx.triggerOpen++
}

const loadWrongQuestions = async () => {
  try {
    loading.value = true
    const response = await studentWrongQuestionListApi({
      pageNum: page.current,
      pageSize: page.size,
      mastered: query.mastered
    })
    wrongQuestions.value = response.data?.records ?? []
    total.value = response.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.current = 1
  loadWrongQuestions()
}

const resetQuery = () => {
  query.mastered = undefined
  page.current = 1
  loadWrongQuestions()
}

const updateMastered = async (item: WrongQuestion, mastered: boolean) => {
  if (!item.id) return
  await studentWrongQuestionMasteredApi(item.id, mastered)
  item.mastered = mastered
  ElMessage.success(mastered ? '已标记为掌握' : '已标记为未掌握')
}

const removeWrongQuestion = async (item: WrongQuestion) => {
  if (!item.id) return
  await ElMessageBox.confirm('确定删除这道错题吗？删除后将不再出现在错题集中。', '删除确认', {
    type: 'warning'
  })
  await studentWrongQuestionDeleteApi(item.id)
  ElMessage.success('错题已删除')
  loadWrongQuestions()
}

const typeLabel = (type?: number) => ({ 1: '单选题', 2: '多选题', 3: '判断题', 4: '主观题' }[type ?? 0] ?? '未知')

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

const splitAnswer = (value?: string) => (value ?? '').split(',').map((item) => item.trim()).filter(Boolean)

const optionClass = (item: WrongQuestion, optionKey: string) => {
  const correctAnswers = splitAnswer(item.correctAnswer)
  const userAnswers = splitAnswer(item.userAnswer)
  return {
    option: true,
    correct: correctAnswers.includes(optionKey),
    wrong: userAnswers.includes(optionKey) && !correctAnswers.includes(optionKey)
  }
}

watch(() => [page.current, page.size], loadWrongQuestions)
onMounted(loadWrongQuestions)
</script>

<style scoped>
.student-page {
  display: grid;
  gap: 16px;
}

.toolbar {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.toolbar :deep(.el-form-item) {
  margin-bottom: 0;
}

.wrong-list {
  min-height: 360px;
  display: grid;
  gap: 14px;
}

.wrong-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.wrong-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.wrong-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.subject-tag {
  margin-left: 6px;
}

.wrong-card h3 {
  margin: 14px 0;
  color: #111827;
  font-size: 16px;
  line-height: 1.7;
}

.options {
  display: grid;
  gap: 8px;
  margin-bottom: 14px;
}

.option {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px 10px;
  color: #374151;
}

.option.correct {
  border-color: #67c23a;
  background: #f0f9eb;
  color: #2f7d20;
}

.option.wrong {
  border-color: #f56c6c;
  background: #fef0f0;
  color: #b42318;
}

.answer-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}

.answer-grid div {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 10px;
}

.answer-grid span {
  display: block;
  color: #6b7280;
  font-size: 12px;
}

.answer-grid strong {
  display: block;
  margin-top: 5px;
  color: #111827;
  font-size: 14px;
}

.analysis {
  margin: 14px 0 0;
  color: #4b5563;
  line-height: 1.7;
}

.pagination {
  justify-content: flex-end;
}

@media (max-width: 820px) {
  .answer-grid {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 560px) {
  .answer-grid {
    grid-template-columns: 1fr;
  }
}
</style>
