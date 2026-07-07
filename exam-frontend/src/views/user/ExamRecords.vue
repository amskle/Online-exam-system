<template>
  <div class="student-page">
    <section class="toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="试卷标题">
          <el-input v-model="query.paperTitle" clearable placeholder="请输入试卷标题" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 140px">
            <el-option label="进行中" :value="0" />
            <el-option label="已交卷" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="table-panel">
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="paperTitle" label="试卷标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="分数" width="130">
          <template #default="{ row }">
            <el-tag :type="scoreTag(row)">{{ row.score ?? 0 }} / {{ row.totalScore ?? 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="考试次数" width="100">
          <template #default="{ row }">第 {{ row.attemptCount ?? 1 }} 次</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'">{{ row.status === 1 ? '已交卷' : '进行中' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" min-width="170" />
        <el-table-column prop="submitTime" label="交卷时间" min-width="170" />
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">详情</el-button>
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

    <el-dialog v-model="detailVisible" title="考试记录详情" width="860px">
      <div v-if="currentRecord" class="record-detail">
        <div class="summary">
          <div>
            <span>试卷</span>
            <strong>{{ currentRecord.paperTitle }}</strong>
          </div>
          <div>
            <span>得分</span>
            <strong>{{ currentRecord.score ?? 0 }} / {{ currentRecord.totalScore ?? 0 }}</strong>
          </div>
          <div>
            <span>考试次数</span>
            <strong>第 {{ currentRecord.attemptCount ?? 1 }} 次</strong>
          </div>
          <div>
            <span>交卷时间</span>
            <strong>{{ currentRecord.submitTime || '-' }}</strong>
          </div>
        </div>

        <article v-for="(answer, index) in currentRecord.answers" :key="answer.id" class="answer-card">
          <div class="answer-head">
            <h3>{{ index + 1 }}. {{ answer.questionContent }}</h3>
            <div>
              <el-tag>{{ typeLabel(answer.type) }}</el-tag>
              <el-tag class="score-tag" :type="(answer.score ?? 0) > 0 ? 'success' : 'info'">
                {{ answer.score ?? 0 }} / {{ answer.fullScore ?? 0 }}
              </el-tag>
            </div>
          </div>
          <div v-if="answer.type !== 4" class="options">
            <span v-for="option in parseOptions(answer.options)" :key="option.key" :class="optionClass(answer, option.key)">
              {{ option.key }}. {{ option.text }}
            </span>
          </div>
          <div class="answer-row">
            <span>我的答案：{{ answer.userAnswer || '未作答' }}</span>
            <span>正确答案：{{ answer.correctAnswer || '-' }}</span>
            <span>判定：{{ answer.judgement || '-' }}</span>
          </div>
        </article>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { studentExamRecordDetailApi, studentExamRecordListApi } from '@/api/student-api'
import type { ExamRecord, ExamRecordAnswer } from '@/types/admin'

const loading = ref(false)
const records = ref<ExamRecord[]>([])
const total = ref(0)
const query = reactive({ paperTitle: '', status: undefined as number | undefined })
const page = reactive({ current: 1, size: 10 })
const detailVisible = ref(false)
const currentRecord = ref<ExamRecord | null>(null)

const loadRecords = async () => {
  try {
    loading.value = true
    const response = await studentExamRecordListApi({
      pageNum: page.current,
      pageSize: page.size,
      paperTitle: query.paperTitle,
      status: query.status
    })
    records.value = response.data?.records ?? []
    total.value = response.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.current = 1
  loadRecords()
}

const resetQuery = () => {
  Object.assign(query, { paperTitle: '', status: undefined })
  page.current = 1
  loadRecords()
}

const openDetail = async (row: ExamRecord) => {
  if (!row.id) return
  const response = await studentExamRecordDetailApi(row.id)
  currentRecord.value = response.data ?? null
  detailVisible.value = true
}

const scoreTag = (row: ExamRecord) => ((row.score ?? 0) >= (row.passScore ?? 60) ? 'success' : 'danger')
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

const optionClass = (answer: ExamRecordAnswer, optionKey: string) => {
  const correctAnswers = splitAnswer(answer.correctAnswer)
  const userAnswers = splitAnswer(answer.userAnswer)
  return {
    option: true,
    correct: correctAnswers.includes(optionKey),
    wrong: userAnswers.includes(optionKey) && !correctAnswers.includes(optionKey)
  }
}

watch(() => [page.current, page.size], loadRecords)
onMounted(loadRecords)
</script>

<style scoped>
.student-page {
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

.record-detail {
  display: grid;
  gap: 14px;
}

.summary {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1fr 1.2fr;
  gap: 12px;
}

.summary div {
  background: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px;
}

.summary span,
.answer-row span {
  display: block;
  color: #6b7280;
  font-size: 13px;
}

.summary strong {
  display: block;
  margin-top: 5px;
  color: #111827;
}

.answer-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
}

.answer-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.answer-head h3 {
  margin: 0;
  color: #111827;
  font-size: 15px;
  line-height: 1.6;
}

.score-tag {
  margin-left: 6px;
}

.options {
  display: grid;
  gap: 8px;
  margin: 12px 0;
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

.answer-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

@media (max-width: 760px) {
  .summary,
  .answer-row {
    grid-template-columns: 1fr;
  }
}
</style>
