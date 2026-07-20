<template>
  <div class="student-page">
    <section class="page-head">
      <div>
        <h2>考试列表</h2>
        <p>查看已发布试卷，选择需要参加的考试。</p>
      </div>
      <el-form :inline="true" :model="query" class="search-form">
        <el-form-item label="试卷标题">
          <el-input v-model="query.title" clearable placeholder="请输入试卷标题" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section v-loading="loading" class="paper-grid">
      <el-empty v-if="!papers.length && !loading" description="暂无可参加的考试" />
      <article v-for="paper in papers" :key="paper.id" class="paper-card">
        <div class="paper-card-head">
          <el-tag type="success">已发布</el-tag>
          <span>{{ paper.subjectName || '未设置科目' }}</span>
        </div>
        <h3>{{ paper.title }}</h3>
        <div class="paper-meta">
          <span>总分：{{ paper.totalScore ?? 0 }}</span>
          <span>时长：{{ paper.duration ?? 0 }} 分钟</span>
          <span>限考：{{ paper.maxAttempts ?? 1 }} 次</span>
          <span>创建：{{ paper.createTime || '-' }}</span>
        </div>
        <div class="paper-time">
          <span>有效期</span>
          <strong>{{ formatRange(paper.startTime, paper.endTime) }}</strong>
        </div>
        <el-button type="primary" class="start-btn" :loading="startingId === paper.id" @click="startExam(paper)">开始考试</el-button>
      </article>
    </section>

    <el-pagination
      v-model:current-page="page.current"
      v-model:page-size="page.size"
      class="pagination"
      layout="total, sizes, prev, pager, next, jumper"
      :page-sizes="[6, 12, 24, 48]"
      :total="total"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { studentExamPaperListApi } from '@/api/student-api'
import type { ExamPaper } from '@/types/admin'

const router = useRouter()
const loading = ref(false)
const papers = ref<ExamPaper[]>([])
const total = ref(0)
const startingId = ref<number>()
const query = reactive({ title: '' })
const page = reactive({ current: 1, size: 6 })

const loadPapers = async () => {
  try {
    loading.value = true
    const response = await studentExamPaperListApi({
      pageNum: page.current,
      pageSize: page.size,
      title: query.title
    })
    papers.value = response.data?.records ?? []
    total.value = response.data?.total ?? 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.current = 1
  loadPapers()
}

const resetQuery = () => {
  query.title = ''
  page.current = 1
  loadPapers()
}

const formatRange = (start?: string, end?: string) => {
  if (!start && !end) return '不限时间'
  return `${start || '不限'} ~ ${end || '不限'}`
}

const parseDateTime = (value?: string) => value ? new Date(value.replace(' ', 'T')).getTime() : NaN

const startExam = (paper: ExamPaper) => {
  if (!paper.id) return
  const now = Date.now()
  const startTime = parseDateTime(paper.startTime)
  const endTime = parseDateTime(paper.endTime)
  if (!Number.isNaN(startTime) && now < startTime) {
    ElMessage.warning('考试尚未开始')
    return
  }
  if (!Number.isNaN(endTime) && now >= endTime) {
    ElMessage.warning('考试已结束，无法参加')
    return
  }
  const route = router.resolve({ path: `/exam/${paper.id}` })
  window.open(route.href, '_blank')
}

watch(() => [page.current, page.size], loadPapers)
onMounted(loadPapers)
</script>

<style scoped>
.student-page {
  display: grid;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
}

.page-head h2 {
  margin: 0;
  color: #111827;
  font-size: 22px;
}

.page-head p {
  margin: 6px 0 0;
  color: #6b7280;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.paper-grid {
  min-height: 360px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.paper-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
  min-height: 238px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
}

.paper-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #6b7280;
  font-size: 13px;
}

.paper-card h3 {
  min-height: 52px;
  margin: 0;
  color: #111827;
  font-size: 18px;
  line-height: 1.45;
}

.paper-meta {
  display: grid;
  gap: 7px;
  color: #4b5563;
  font-size: 13px;
}

.paper-time {
  display: grid;
  gap: 5px;
  margin-top: auto;
  color: #6b7280;
  font-size: 12px;
}

.paper-time strong {
  color: #374151;
  font-weight: 500;
}

.start-btn {
  width: 100%;
}

.pagination {
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .page-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
