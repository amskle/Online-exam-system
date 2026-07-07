<template>
  <div class="admin-page">
    <section class="stats-grid">
      <div v-for="item in overviewStats" :key="item.label" class="stat-card">
        <div class="stat-icon" :style="{ background: item.color }">
          <el-icon><component :is="item.icon" /></el-icon>
        </div>
        <div>
          <p>{{ item.label }}</p>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
    </section>

    <section class="panel">
      <div class="panel-head">
        <div>
          <h2>趋势概览</h2>
          <p>近 7 天注册、考试和题库增长趋势</p>
        </div>
        <el-segmented v-model="trendDays" :options="['7天', '14天', '30天']" />
      </div>
      <div ref="trendChartRef" class="trend-chart"></div>
    </section>

    <section class="dashboard-grid">
      <div class="panel">
        <div class="panel-head compact">
          <h2>题目类型分布</h2>
          <el-button size="small" @click="refresh">刷新</el-button>
        </div>
        <div class="distribution-list">
          <div v-for="item in questionTypeStats" :key="item.name" class="distribution-row">
            <span>{{ item.name }}</span>
            <el-progress :percentage="item.percent" :stroke-width="10" />
          </div>
        </div>
      </div>

      <div class="panel">
        <div class="panel-head compact">
          <h2>各科目题目数</h2>
        </div>
        <div class="subject-bars">
          <div v-for="item in subjectStats" :key="item.name" class="subject-row">
            <span>{{ item.name }}</span>
            <div>
              <i :style="{ width: `${item.width}%` }"></i>
            </div>
            <strong>{{ item.count }}</strong>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { DataAnalysis, Files, Notebook, Reading, User, UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts/core'
import { GridComponent, LegendComponent, TooltipComponent, type GridComponentOption, type LegendComponentOption, type TooltipComponentOption } from 'echarts/components'
import { LineChart, type LineSeriesOption } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { dashboardOverviewApi, dashboardTrendApi } from '@/api/admin-api'
import type { DashboardOverview, TrendStats } from '@/types/admin'

const trendDays = ref('7天')
const trendChartRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
type EChartsOption = echarts.ComposeOption<GridComponentOption | LegendComponentOption | TooltipComponentOption | LineSeriesOption>
echarts.use([GridComponent, LegendComponent, TooltipComponent, LineChart, CanvasRenderer])
const overview = ref<DashboardOverview>({
  userCount: 0,
  adminCount: 0,
  paperCount: 0,
  recordCount: 0,
  questionCount: 0,
  subjectCount: 0,
  questionTypeStats: [],
  subjectQuestionStats: [],
  trendStats: []
})

const overviewStats = computed(() => [
  { label: '用户总数', value: overview.value.userCount, color: '#2563eb', icon: User },
  { label: '管理员数', value: overview.value.adminCount, color: '#dc2626', icon: UserFilled },
  { label: '试卷总数', value: overview.value.paperCount, color: '#16a34a', icon: Files },
  { label: '考试记录', value: overview.value.recordCount, color: '#f59e0b', icon: DataAnalysis },
  { label: '题目总数', value: overview.value.questionCount, color: '#7c3aed', icon: Reading },
  { label: '科目总数', value: overview.value.subjectCount, color: '#0891b2', icon: Notebook }
])

const trendData = ref<TrendStats[]>([])
const currentTrendDays = computed(() => Number.parseInt(trendDays.value, 10))

const questionTypeStats = computed(() => {
  const total = overview.value.questionTypeStats.reduce((sum, item) => sum + item.value, 0)
  return overview.value.questionTypeStats.map((item) => ({
    name: item.name,
    percent: total ? Math.round((item.value / total) * 100) : 0
  }))
})

const subjectStats = computed(() => {
  const max = Math.max(...overview.value.subjectQuestionStats.map((item) => item.value), 1)
  return overview.value.subjectQuestionStats.map((item) => ({
    name: item.name,
    count: item.value,
    width: Math.max(6, Math.round((item.value / max) * 100))
  }))
})

const loadOverview = async () => {
  const response = await dashboardOverviewApi()
  if (response.data) {
    overview.value = response.data
    trendData.value = response.data.trendStats ?? trendData.value
  }
}

const loadTrend = async () => {
  const response = await dashboardTrendApi(currentTrendDays.value)
  trendData.value = response.data ?? []
  await nextTick()
  renderTrendChart()
}

const renderTrendChart = () => {
  if (!trendChartRef.value) return
  trendChart = trendChart ?? echarts.init(trendChartRef.value)
  const dates = trendData.value.map((item) => item.date)
  const option: EChartsOption = {
    color: ['#2563eb', '#f59e0b', '#16a34a'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' }
    },
    legend: {
      top: 0,
      right: 8,
      data: ['用户注册', '考试记录', '题目新增']
    },
    grid: {
      left: 34,
      right: 24,
      top: 42,
      bottom: dates.length > 10 ? 58 : 36,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: dates,
      axisLabel: {
        color: '#64748b',
        rotate: dates.length > 10 ? 45 : 0
      },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: '#64748b' },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [
      buildLineSeries('用户注册', trendData.value.map((item) => item.users), '#2563eb'),
      buildLineSeries('考试记录', trendData.value.map((item) => item.exams), '#f59e0b'),
      buildLineSeries('题目新增', trendData.value.map((item) => item.questions), '#16a34a')
    ]
  }
  trendChart.setOption(option)
}

const buildLineSeries = (name: string, data: number[], color: string): LineSeriesOption => ({
  name,
  type: 'line',
  smooth: true,
  symbolSize: 7,
  data,
  lineStyle: { width: 3, color },
  areaStyle: {
    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
      { offset: 0, color: `${color}33` },
      { offset: 1, color: `${color}05` }
    ])
  }
})

const refresh = async () => {
  await loadOverview()
  await loadTrend()
  ElMessage.success('仪表盘数据已刷新')
}

const handleResize = () => {
  trendChart?.resize()
}

watch(trendDays, loadTrend)

onMounted(async () => {
  await loadOverview()
  await loadTrend()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  trendChart = null
})
</script>

<style scoped>
.admin-page {
  display: grid;
  gap: 18px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 14px;
}

.stat-card,
.panel {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.04);
}

.stat-card {
  min-height: 104px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 18px;
}

.stat-icon {
  width: 46px;
  height: 46px;
  color: #fff;
  border-radius: 8px;
  display: grid;
  place-items: center;
  font-size: 22px;
}

.stat-card p {
  color: #6b7280;
  font-size: 13px;
  margin: 0 0 8px;
}

.stat-card strong {
  color: #111827;
  font-size: 26px;
}

.panel {
  padding: 18px;
}

.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
}

.panel-head.compact {
  margin-bottom: 14px;
}

.panel-head h2 {
  color: #111827;
  font-size: 18px;
  margin: 0;
}

.panel-head p {
  color: #6b7280;
  font-size: 13px;
  margin: 6px 0 0;
}

.trend-chart {
  height: 260px;
  border-top: 1px solid #f1f5f9;
  padding-top: 12px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.distribution-list,
.subject-bars {
  display: grid;
  gap: 14px;
}

.distribution-row {
  display: grid;
  grid-template-columns: 80px 1fr;
  align-items: center;
  gap: 12px;
  color: #374151;
}

.subject-row {
  display: grid;
  grid-template-columns: 110px 1fr 42px;
  align-items: center;
  gap: 12px;
}

.subject-row span {
  color: #374151;
  font-size: 13px;
}

.subject-row div {
  height: 10px;
  background: #e5e7eb;
  border-radius: 999px;
  overflow: hidden;
}

.subject-row i {
  display: block;
  height: 100%;
  background: linear-gradient(90deg, #2dd4bf, #0f766e);
  border-radius: inherit;
}

.subject-row strong {
  color: #111827;
}

@media (max-width: 1400px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 1000px) {
  .stats-grid,
  .dashboard-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stats-grid,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
