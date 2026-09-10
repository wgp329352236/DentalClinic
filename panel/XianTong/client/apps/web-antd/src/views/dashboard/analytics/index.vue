<script lang="ts" setup>
import type { EchartsUIType } from '@vben/plugins/echarts';

import type {
  ClinicComparison,
  CustomerLevelDistribution,
  CustomerRetention,
  CustomerSourceDistribution,
  DashboardSummary,
  ReferralStats,
  TrendDataPoint,
} from '#/api/dashboard';

import { computed, nextTick, onMounted, ref } from 'vue';

import { Page } from '@vben/common-ui';
import { EchartsUI, useEcharts } from '@vben/plugins/echarts';

import {
  Button,
  Card,
  Col,
  Empty,
  RadioButton,
  RadioGroup,
  Row,
  Space,
  Statistic,
  Table,
} from 'ant-design-vue';

import {
  getDashboardClinicComparison,
  getDashboardCustomerLevels,
  getDashboardCustomerSources,
  getDashboardReferral,
  getDashboardRetention,
  getDashboardSummary,
  getDashboardTrends,
} from '#/api/dashboard';

defineOptions({ name: 'DashboardAnalytics' });

const loading = ref(true);
const trendLoading = ref(false);
const trendDays = ref(7);

const summary = ref<DashboardSummary>({
  todayAppointments: 0,
  todayArrivals: 0,
  todayNewCustomers: 0,
  todayRevenue: 0,
});
const trends = ref<TrendDataPoint[]>([]);
const clinics = ref<ClinicComparison[]>([]);
const levels = ref<CustomerLevelDistribution[]>([]);
const sources = ref<CustomerSourceDistribution[]>([]);
const referral = ref<ReferralStats>({
  conversionRate: 0,
  monthlyNewCustomers: 0,
  monthlyRevenue: 0,
  topReferrers: [],
});
const retention = ref<CustomerRetention>({
  churnRate: 0,
  repeatVisitRate: 0,
});

const trendChartRef = ref<EchartsUIType>();
const clinicChartRef = ref<EchartsUIType>();
const sourceChartRef = ref<EchartsUIType>();
const levelChartRef = ref<EchartsUIType>();

const { renderEcharts: renderTrend } = useEcharts(trendChartRef);
const { renderEcharts: renderClinic } = useEcharts(clinicChartRef);
const { renderEcharts: renderSource } = useEcharts(sourceChartRef);
const { renderEcharts: renderLevel } = useEcharts(levelChartRef);

/**
 * 图表配置类型直接取自 renderEcharts 的入参，而不是插件导出的 ECOption。
 *
 * 仓里存在两份 echarts 类型声明（pnpm 未去重），ECOption 与 renderEcharts 期望的
 * EChartsOption 被 TS 判为「同名但不相关」的两个类型，直接传会报 TS2345。
 * 用 Parameters 反查入参类型既能得到字面量收窄（type: 'line' 不会被推成 string），
 * 又天然与调用点兼容，且不必让业务包直接依赖 echarts。
 */
type ChartOption = Parameters<typeof renderTrend>[0];

const referrerColumns = [
  { dataIndex: 'rank', title: '排名', width: 70 },
  { dataIndex: 'userId', title: '客户ID', width: 100 },
  { dataIndex: 'nickname', title: '昵称' },
  { dataIndex: 'referralCount', title: '成功推荐人数', width: 130 },
];

const referrerRows = computed(() =>
  referral.value.topReferrers.map((item, index) => ({
    nickname: item.nickname || `客户 #${item.userId}`,
    rank: index + 1,
    referralCount: item.referralCount,
    userId: item.userId,
  })),
);

// 趋势图与门店对比图都存在「次数」与「金额」两种量纲，
// 金额动辄上万会把次数曲线压成一条贴底的直线，所以拆成左右双 Y 轴。
function trendOption(): ChartOption {
  return {
    grid: { bottom: 32, containLabel: true, left: 16, right: 24, top: 48 },
    legend: { data: ['预约数', '新客数', '营收(元)'], top: 8 },
    series: [
      {
        data: trends.value.map((item) => item.appointmentCount),
        name: '预约数',
        smooth: true,
        type: 'line',
      },
      {
        data: trends.value.map((item) => item.newCustomerCount),
        name: '新客数',
        smooth: true,
        type: 'line',
      },
      {
        data: trends.value.map((item) => item.revenue),
        name: '营收(元)',
        smooth: true,
        type: 'line',
        yAxisIndex: 1,
      },
    ],
    tooltip: { trigger: 'axis' },
    xAxis: {
      // 只留 MM-DD：30/90 天时完整日期会让横轴标签互相重叠
      data: trends.value.map((item) => item.date.slice(5)),
      type: 'category',
    },
    yAxis: [
      { minInterval: 1, name: '数量', type: 'value' },
      { name: '营收(元)', type: 'value' },
    ],
  };
}

function clinicOption(): ChartOption {
  const names = clinics.value.map((item) => item.clinicName);
  return {
    grid: { bottom: 24, containLabel: true, left: 16, right: 24, top: 48 },
    legend: { data: ['预约量', '新客数', '营收(元)'], top: 8 },
    series: [
      {
        barMaxWidth: 28,
        data: clinics.value.map((item) => item.appointmentCount),
        name: '预约量',
        type: 'bar',
      },
      {
        barMaxWidth: 28,
        data: clinics.value.map((item) => item.newCustomerCount),
        name: '新客数',
        type: 'bar',
      },
      {
        barMaxWidth: 28,
        data: clinics.value.map((item) => item.revenue),
        name: '营收(元)',
        type: 'bar',
        yAxisIndex: 1,
      },
    ],
    tooltip: { axisPointer: { type: 'shadow' }, trigger: 'axis' },
    xAxis: {
      axisLabel: { interval: 0, rotate: names.length > 4 ? 25 : 0 },
      data: names,
      type: 'category',
    },
    yAxis: [
      { minInterval: 1, name: '数量', type: 'value' },
      { name: '营收(元)', type: 'value' },
    ],
  };
}

function sourceOption(): ChartOption {
  return {
    legend: { bottom: 0 },
    series: [
      {
        avoidLabelOverlap: true,
        data: sources.value.map((item) => ({
          name: item.sourceLabel,
          value: item.count,
        })),
        label: { formatter: '{b}\n{d}%' },
        name: '客户来源',
        radius: ['40%', '65%'],
        type: 'pie',
      },
    ],
    tooltip: { formatter: '{b}: {c} 人 ({d}%)', trigger: 'item' },
  };
}

function levelOption(): ChartOption {
  return {
    grid: { bottom: 24, containLabel: true, left: 16, right: 24, top: 32 },
    series: [
      {
        barMaxWidth: 44,
        data: levels.value.map((item) => item.count),
        label: { position: 'top', show: true },
        name: '客户数',
        type: 'bar',
      },
    ],
    tooltip: { axisPointer: { type: 'shadow' }, trigger: 'axis' },
    xAxis: {
      data: levels.value.map((item) => item.levelName),
      type: 'category',
    },
    yAxis: { minInterval: 1, name: '客户数', type: 'value' },
  };
}

async function renderCharts() {
  // Card 在 loading 期间渲染的是骨架屏，图表容器高度为 0，此时 setOption 画不出来，
  // 必须等骨架屏撤掉、容器拿到真实高度后再渲染。
  await nextTick();
  // 门店/来源/等级三张图在无数据时会被模板里的 <Empty> 顶掉，容器 ref 随之变成
  // undefined。useEcharts 拿不到容器时会每 30ms 无限重试且永不 resolve，会让下面
  // 的 Promise.all 挂死并留下常驻定时器，所以必须先判空再渲染。
  const skipped = Promise.resolve(null);
  await Promise.all([
    renderTrend(trendOption()),
    clinics.value.length > 0 ? renderClinic(clinicOption()) : skipped,
    sources.value.length > 0 ? renderSource(sourceOption()) : skipped,
    levels.value.length > 0 ? renderLevel(levelOption()) : skipped,
  ]);
}

async function loadAll() {
  loading.value = true;
  // 用 allSettled 而不是 all：看板由 7 个独立接口拼成，
  // 任一接口失败（如推荐统计）不应该让整页其余指标全部空白。
  const [sum, trend, clinic, level, source, ref, ret] =
    await Promise.allSettled([
      getDashboardSummary(),
      getDashboardTrends(trendDays.value),
      getDashboardClinicComparison(),
      getDashboardCustomerLevels(),
      getDashboardCustomerSources(),
      getDashboardReferral(),
      getDashboardRetention(),
    ]);

  if (sum.status === 'fulfilled') summary.value = sum.value;
  if (trend.status === 'fulfilled') trends.value = trend.value;
  if (clinic.status === 'fulfilled') clinics.value = clinic.value;
  if (level.status === 'fulfilled') levels.value = level.value;
  if (source.status === 'fulfilled') sources.value = source.value;
  if (ref.status === 'fulfilled') referral.value = ref.value;
  if (ret.status === 'fulfilled') retention.value = ret.value;

  loading.value = false;
  await renderCharts();
}

/** 切换 7/30 天时只重拉趋势，不必刷新整页。 */
async function onTrendDaysChange() {
  trendLoading.value = true;
  try {
    trends.value = await getDashboardTrends(trendDays.value);
    await renderTrend(trendOption());
  } catch {
    // 失败时保留上一次的数据，错误提示由 requestClient 的拦截器统一弹出
  } finally {
    trendLoading.value = false;
  }
}

onMounted(loadAll);
</script>

<template>
  <Page
    description="今日实时指标、经营趋势、客户结构、门店对比与推荐裂变效果（需求 4.10）"
    title="工作台"
  >
    <template #extra>
      <Button :loading="loading" @click="loadAll">刷新</Button>
    </template>

    <!-- 实时数据 -->
    <Row :gutter="[16, 16]">
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :value="summary.todayAppointments"
            suffix="个"
            title="今日预约数"
          />
        </Card>
      </Col>
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :value="summary.todayArrivals"
            suffix="人"
            title="今日到店数"
          />
        </Card>
      </Col>
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :precision="2"
            :value="summary.todayRevenue"
            prefix="¥"
            title="今日营收"
          />
        </Card>
      </Col>
      <Col :lg="6" :sm="12" :xs="24">
        <Card :loading="loading">
          <Statistic
            :value="summary.todayNewCustomers"
            suffix="人"
            title="今日新增客户"
          />
        </Card>
      </Col>
    </Row>

    <!-- 趋势数据 -->
    <Card class="mt-4" title="经营趋势">
      <template #extra>
        <RadioGroup
          v-model:value="trendDays"
          :disabled="trendLoading"
          button-style="solid"
          size="small"
          @change="onTrendDaysChange"
        >
          <RadioButton :value="7">近 7 天</RadioButton>
          <RadioButton :value="30">近 30 天</RadioButton>
        </RadioGroup>
      </template>
      <EchartsUI ref="trendChartRef" height="320px" />
    </Card>

    <!-- 门店对比 + 客户来源 -->
    <Row :gutter="[16, 16]" class="mt-4">
      <Col :lg="16" :xs="24">
        <Card title="门店对比（本月）">
          <template #extra>
            <span class="text-muted-foreground text-xs">
              客户满意度待评价功能上线后补充
            </span>
          </template>
          <Empty
            v-if="!loading && clinics.length === 0"
            description="暂无门店数据"
          />
          <EchartsUI v-else ref="clinicChartRef" height="320px" />
        </Card>
      </Col>
      <Col :lg="8" :xs="24">
        <Card title="客户来源占比">
          <Empty
            v-if="!loading && sources.length === 0"
            description="暂无客户数据"
          />
          <EchartsUI v-else ref="sourceChartRef" height="320px" />
        </Card>
      </Col>
    </Row>

    <!-- 客户等级分布 + 留存 + 推荐概览 -->
    <Row :gutter="[16, 16]" class="mt-4">
      <Col :lg="8" :xs="24">
        <Card title="客户等级分布">
          <Empty
            v-if="!loading && levels.length === 0"
            description="暂无等级数据"
          />
          <EchartsUI v-else ref="levelChartRef" height="280px" />
        </Card>
      </Col>
      <Col :lg="8" :xs="24">
        <Card title="客户留存">
          <Row :gutter="16">
            <Col :span="12">
              <Statistic
                :precision="1"
                :value="retention.repeatVisitRate"
                suffix="%"
                title="复诊率"
              />
            </Col>
            <Col :span="12">
              <Statistic
                :precision="1"
                :value="retention.churnRate"
                suffix="%"
                title="流失率"
              />
            </Col>
          </Row>
          <p class="text-muted-foreground mt-4 text-xs leading-5">
            复诊率 = 完成就诊 2 次及以上的客户占比；流失率 = 近 90
            天无登录且建档超过 90 天的客户占比。
          </p>
        </Card>
      </Col>
      <Col :lg="8" :xs="24">
        <Card title="推荐概览（本月）">
          <Space :size="24" direction="vertical">
            <Statistic
              :precision="1"
              :value="referral.conversionRate"
              suffix="%"
              title="推荐注册转化率"
            />
            <Statistic
              :value="referral.monthlyNewCustomers"
              suffix="人"
              title="推荐带来的新客"
            />
            <Statistic
              :precision="2"
              :value="referral.monthlyRevenue"
              prefix="¥"
              title="推荐带来的营收"
            />
          </Space>
        </Card>
      </Col>
    </Row>

    <!-- 推荐人 TOP10 -->
    <Card class="mt-4 mb-4" title="推荐人 TOP10">
      <Empty
        v-if="!loading && referrerRows.length === 0"
        description="暂无推荐记录"
      />
      <Table
        v-else
        :columns="referrerColumns"
        :data-source="referrerRows"
        :loading="loading"
        :pagination="false"
        row-key="userId"
        size="small"
      />
    </Card>
  </Page>
</template>
