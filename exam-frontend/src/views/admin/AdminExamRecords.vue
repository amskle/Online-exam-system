<template>
  <div class="management-page">
    <section class="toolbar">
      <el-form :inline="true" :model="query">
        <el-form-item label="试卷标题">
          <el-input v-model="query.paperTitle" clearable placeholder="请输入试卷标题" />
        </el-form-item>
        <el-form-item label="考生">
          <el-input v-model="query.username" clearable placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部状态" style="width: 130px">
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
      <el-table :data="records" border stripe>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="paperTitle" label="试卷标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="得分" width="110">
          <template #default="{ row }">
            <el-tag :type="(row.score ?? 0) >= (row.passScore ?? 60) ? 'success' : 'danger'">{{ row.score ?? 0 }} 分</el-tag>
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
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="openDetail(row)">详情</el-button>
            <el-button size="small" type="danger" @click="removeRecord(row)">删除</el-button>
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

    <el-dialog v-model="detailVisible" title="考试记录详情与主观题批改" width="860px">
      <div v-if="currentRecord" class="record-detail">
        <div class="summary">
          <div>
            <span>考生</span>
            <strong>{{ currentRecord.username }}</strong>
          </div>
          <div>
            <span>试卷</span>
            <strong>{{ currentRecord.paperTitle }}</strong>
          </div>
          <div>
            <span>当前得分</span>
            <strong>{{ currentRecord.score ?? 0 }} / {{ currentRecord.totalScore ?? 0 }}</strong>
          </div>
          <div>
            <span>考试次数</span>
            <strong>第 {{ currentRecord.attemptCount ?? 1 }} 次</strong>
          </div>
        </div>

        <div v-for="answer in currentRecord.answers" :key="answer.id" class="answer-card">
          <div class="answer-head">
            <h3>{{ answer.questionContent }}</h3>
            <el-tag>{{ typeLabel(answer.type) }}</el-tag>
          </div>

          <div v-if="answer.type !== 4" class="objective-options">
            <span
              v-for="option in parseOptions(answer.options)"
              :key="option"
              :class="optionClass(answer, option)"
            >
              {{ option }}
            </span>
          </div>

          <div class="answer-row">
            <span>考生答案：{{ answer.userAnswer || '未作答' }}</span>
            <span>正确答案：{{ answer.correctAnswer }}</span>
          </div>

          <div v-if="answer.type === 4" class="grade-box">
            <p>作答内容：{{ answer.userAnswer }}</p>
            <el-input-number v-model="answer.score" :min="0" :max="answer.fullScore" />
            <el-radio-group v-model="answer.judgement">
              <el-radio-button label="正确" />
              <el-radio-button label="错误" />
            </el-radio-group>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="confirmGrade">确认批改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { examRecordDeleteApi, examRecordDetailApi, examRecordGradeApi, examRecordListPageApi } from '@/api/admin-api'
import type { ExamRecord, ExamRecordAnswer } from '@/types/admin'

const records = ref<ExamRecord[]>([])
const total = ref(0)
const query = reactive({ paperTitle: '', username: '', status: undefined as number | undefined })
const page = reactive({ current: 1, size: 10 })
const detailVisible = ref(false)
const currentRecord = ref<ExamRecord | null>(null)

const loadRecords = async () => {
  const response = await examRecordListPageApi({
    pageNum: page.current,
    pageSize: page.size,
    ...query
  })
  records.value = response.data?.records ?? []
  total.value = response.data?.total ?? 0
}

const handleSearch = () => {
  page.current = 1
  loadRecords()
}

const resetQuery = () => {
  Object.assign(query, { paperTitle: '', username: '', status: undefined })
  page.current = 1
  loadRecords()
}

const openDetail = async (row: ExamRecord) => {
  if (!row.id) return
  const response = await examRecordDetailApi(row.id)
  currentRecord.value = response.data ?? null
  detailVisible.value = true
}

const removeRecord = async (row: ExamRecord) => {
  if (!row.id) return
  await ElMessageBox.confirm(`确定删除「${row.paperTitle ?? '该考试'}」的考试记录吗？删除后答题明细也会同步删除。`, '删除确认', {
    type: 'warning'
  })
  await examRecordDeleteApi(row.id)
  ElMessage.success('考试记录已删除')
  loadRecords()
}

const typeLabel = (type?: number) => ({ 1: '单选题', 2: '多选题', 3: '判断题', 4: '主观题' }[type ?? 0] ?? '未知')

const parseOptions = (options?: string) => {
  if (!options) return []
  try {
    const parsed = JSON.parse(options)
    if (Array.isArray(parsed)) {
      return parsed.map((item) => item.label ?? item.content ?? String(item))
    }
  } catch {
    return options.split(',').filter(Boolean)
  }
  return []
}

const optionClass = (answer: ExamRecordAnswer, option: string) => {
  const correctAnswers = (answer.correctAnswer ?? '').split(',')
  const userAnswers = (answer.userAnswer ?? '').split(',')
  return {
    option: true,
    correct: correctAnswers.includes(option),
    wrong: userAnswers.includes(option) && !correctAnswers.includes(option)
  }
}

const confirmGrade = async () => {
  if (!currentRecord.value) return
  await examRecordGradeApi({
    recordId: currentRecord.value.id,
    answers: (currentRecord.value.answers ?? [])
      .filter((answer) => answer.type === 4)
      .map((answer) => ({
        answerId: answer.id,
        score: answer.score,
        judgement: answer.judgement
      }))
  })
  detailVisible.value = false
  ElMessage.success('主观题批改成功，得分已刷新')
  loadRecords()
}

watch(() => [page.current, page.size], loadRecords)
onMounted(loadRecords)
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

.record-detail {
  display: grid;
  gap: 14px;
}

.summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary div,
.answer-card {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
  background: #f8fafc;
}

.summary span {
  display: block;
  color: #6b7280;
  font-size: 12px;
  margin-bottom: 6px;
}

.summary strong {
  color: #111827;
}

.answer-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.answer-head h3 {
  font-size: 15px;
  color: #111827;
  margin: 0;
}

.objective-options {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 12px 0;
}

.option {
  min-width: 42px;
  height: 34px;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  display: grid;
  place-items: center;
  background: #fff;
  color: #374151;
}

.option.correct {
  border-color: #22c55e;
  background: #dcfce7;
  color: #166534;
}

.option.wrong {
  border-color: #ef4444;
  background: #fee2e2;
  color: #991b1b;
}

.answer-row,
.grade-box {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  color: #374151;
}

.grade-box p {
  flex-basis: 100%;
  margin: 0;
}
</style>
