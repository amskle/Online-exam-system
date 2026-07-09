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
          <p>从首位用户创建日期至今的注册、考试和题库增长趋势</p>
        </div>
        <el-button size="small" @click="refresh">刷新</el-button>
      </div>
      <div ref="trendChartRef" class="trend-chart"></div>
    </section>

    <section class="panel">
      <div class="panel-head score-head">
        <div>
          <h2>学生成绩统计</h2>
          <p>按百分制分率统计已产生分数的考试记录</p>
        </div>
        <div class="score-actions">
          <el-radio-group v-model="scoreChartMode" size="small">
            <el-radio-button label="distribution">分数段</el-radio-button>
            <el-radio-button label="ranking">学生排行</el-radio-button>
          </el-radio-group>
          <el-button size="small" @click="refresh">刷新</el-button>
        </div>
      </div>
      <div class="score-summary">
        <div v-for="item in scoreSummary" :key="item.label" class="score-metric">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>
      <div ref="scoreChartRef" class="score-chart"></div>
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
import { BarChart, LineChart, type BarSeriesOption, type LineSeriesOption } from 'echarts/charts'
import { CanvasRenderer } from 'echarts/renderers'
import { dashboardOverviewApi, dashboardScoreStatsApi, dashboardTrendApi } from '@/api/admin-api'
import type { DashboardOverview, StudentScoreStats, TrendStats } from '@/types/admin'

const trendChartRef = ref<HTMLDivElement>()
const scoreChartRef = ref<HTMLDivElement>()
let trendChart: echarts.ECharts | null = null
let scoreChart: echarts.ECharts | null = null
type EChartsOption = echarts.ComposeOption<GridComponentOption | LegendComponentOption | TooltipComponentOption | LineSeriesOption | BarSeriesOption>
echarts.use([GridComponent, LegendComponent, TooltipComponent, BarChart, LineChart, CanvasRenderer])
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
const scoreChartMode = ref<'distribution' | 'ranking'>('distribution')
const scoreStats = ref<StudentScoreStats>({
  recordCount: 0,
  averageScoreRate: 0,
  highestScoreRate: 0,
  lowestScoreRate: 0,
  passCount: 0,
  passRate: 0,
  scoreDistribution: [],
  topStudentScores: []
})

const formatPercent = (value: number) => `${value.toFixed(1)}%`

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

const scoreSummary = computed(() => [
  { label: '考试记录', value: scoreStats.value.recordCount },
  { label: '平均分率', value: formatPercent(scoreStats.value.averageScoreRate) },
  { label: '通过率', value: formatPercent(scoreStats.value.passRate) },
  { label: '最高分率', value: `${scoreStats.value.highestScoreRate}%` },
  { label: '最低分率', value: `${scoreStats.value.lowestScoreRate}%` }
])

const loadOverview = async () => {
  const response = await dashboardOverviewApi()
  if (response.data) {
    overview.value = response.data
    trendData.value = response.data.trendStats ?? trendData.value
  }
}

const loadTrend = async () => {
  const response = await dashboardTrendApi()
  trendData.value = response.data ?? []
  await nextTick()
  renderTrendChart()
}

const loadScoreStats = async () => {
  const response = await dashboardScoreStatsApi()
  if (response.data) {
    scoreStats.value = response.data
  }
  await nextTick()
  renderScoreChart()
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

const renderScoreChart = () => {
  if (!scoreChartRef.value) return
  scoreChart = scoreChart ?? echarts.init(scoreChartRef.value)
  const source = scoreChartMode.value === 'distribution'
    ? scoreStats.value.scoreDistribution
    : scoreStats.value.topStudentScores
  const names = source.map((item) => item.name)
  const values = source.map((item) => item.value)
  const isRanking = scoreChartMode.value === 'ranking'
  const option: EChartsOption = {
    color: [isRanking ? '#0f766e' : '#2563eb'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      left: 34,
      right: 24,
      top: 28,
      bottom: names.length > 6 ? 58 : 36,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: names,
      axisLabel: {
        color: '#64748b',
        rotate: names.length > 6 ? 35 : 0
      },
      axisLine: { lineStyle: { color: '#e5e7eb' } },
      axisTick: { show: false }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      max: isRanking ? 100 : undefined,
      axisLabel: {
        color: '#64748b',
        formatter: isRanking ? '{value}%' : '{value}'
      },
      splitLine: { lineStyle: { color: '#eef2f7' } }
    },
    series: [
      {
        name: isRanking ? '平均分率' : '人数',
        type: 'bar',
        data: values,
        barMaxWidth: 42,
        itemStyle: {
          borderRadius: [6, 6, 0, 0]
        }
      }
    ]
  }
  scoreChart.setOption(option, true)
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
  await loadScoreStats()
  ElMessage.success('仪表盘数据已刷新')
}

const handleResize = () => {
  trendChart?.resize()
  scoreChart?.resize()
}

watch(scoreChartMode, () => {
  renderScoreChart()
})

onMounted(async () => {
  await loadOverview()
  await loadTrend()
  await loadScoreStats()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  scoreChart?.dispose()
  trendChart = null
  scoreChart = null
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

.score-head {
  align-items: flex-start;
}

.score-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.score-summary {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.score-metric {
  min-height: 72px;
  border: 1px solid #eef2f7;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
}

.score-metric span {
  display: block;
  color: #64748b;
  font-size: 13px;
  margin-bottom: 8px;
}

.score-metric strong {
  color: #111827;
  font-size: 24px;
  line-height: 1;
}

.score-chart {
  height: 300px;
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

  .score-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .stats-grid,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .score-head {
    display: grid;
    gap: 12px;
  }

  .score-actions {
    justify-content: flex-start;
  }

  .score-summary {
    grid-template-columns: 1fr;
  }
}
</style>
