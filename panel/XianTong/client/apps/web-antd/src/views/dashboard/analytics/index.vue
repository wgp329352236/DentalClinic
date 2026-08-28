<script lang="ts" setup>
import { ref, onMounted } from 'vue';

import { Page } from '@vben/common-ui';

import { Card, Col, Row, Statistic, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getDashboardGames, getDashboardSummary, getPushRecords } from '#/api/dashboard';

const summary = ref<any>({});
const loading = ref(true);

async function fetchSummary() {
  try {
    summary.value = await getDashboardSummary();
  } catch (_) {
    summary.value = {};
  }
  loading.value = false;
}

onMounted(fetchSummary);

// 游戏数据量表格
const [GamesGrid] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'gameCode', title: '游戏代码', minWidth: 100 },
      { field: 'gameName', title: '游戏名称', minWidth: 130 },
      { field: 'pullCount7d', title: '7日拉取量', width: 100 },
      { field: 'pushCount7d', title: '7日推送量', width: 100 },
      { field: 'lastPullTime', title: '最后拉取', minWidth: 170 },
      { field: 'lastPushTime', title: '最后推送', minWidth: 170 },
      { field: 'stale', title: '状态', width: 90, slots: { default: 'stale' } },
    ],
    height: 300,
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getDashboardGames();
          return { items: Array.isArray(data) ? data : [], total: Array.isArray(data) ? data.length : 0 };
        },
      },
    },
    toolbarConfig: { refresh: true },
  },
});

// 推送记录表格
const [PushGrid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      { component: 'Input', fieldName: 'gameCode', label: '游戏', componentProps: { placeholder: '游戏代码' } },
      {
        component: 'Select', fieldName: 'status', label: '状态',
        componentProps: { placeholder: '全部', allowClear: true, options: [
          { label: '成功', value: 'success' },
          { label: '失败', value: 'failed' },
        ]},
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { type: 'seq', title: '#', width: 50 },
      { field: 'gameCode', title: '游戏', width: 100 },
      { field: 'platform', title: '平台', width: 80 },
      { field: 'status', title: '状态', width: 80, slots: { default: 'pushStatus' } },
      { field: 'recordCount', title: '记录数', width: 80 },
      { field: 'pushTime', title: '推送时间', minWidth: 170 },
      { field: 'errorMessage', title: '错误信息', minWidth: 200 },
    ],
    height: 300,
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const res = await getPushRecords({ page: page.currentPage, pageSize: page.pageSize, ...formValues });
          return { items: res?.list || [], total: res?.total || 0 };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { refresh: true },
  },
});
</script>

<template>
  <Page title="数据概览" auto-content-height>
    <!-- 统计卡片 -->
    <Row :gutter="[16, 16]" class="mb-4">
      <Col :xs="24" :sm="12" :lg="6">
        <Card :loading="loading">
          <Statistic title="总游戏数" :value="summary.totalGames || 0" />
        </Card>
      </Col>
      <Col :xs="24" :sm="12" :lg="6">
        <Card :loading="loading">
          <Statistic title="今日拉取量" :value="summary.pulledToday || 0" />
        </Card>
      </Col>
      <Col :xs="24" :sm="12" :lg="6">
        <Card :loading="loading">
          <Statistic title="今日推送量" :value="summary.pushedToday || 0" />
        </Card>
      </Col>
      <Col :xs="24" :sm="12" :lg="6">
        <Card :loading="loading">
          <Statistic title="成功率" :value="(summary.successRate || 0) * 100" suffix="%" :precision="1" />
        </Card>
      </Col>
    </Row>

    <!-- 游戏数据量 -->
    <Card title="游戏数据量（近 7 天）" class="mb-4" style="margin-bottom: 18px">
      <GamesGrid>
        <template #stale="{ row }">
          <Tag :color="row.stale ? 'error' : 'success'">{{ row.stale ? '⚠️ 过期' : '正常' }}</Tag>
        </template>
      </GamesGrid>
    </Card>

    <!-- 推送记录 -->
    <Card title="推送记录">
      <PushGrid>
        <template #pushStatus="{ row }">
          <Tag :color="row.status === 'success' ? 'success' : 'error'">{{ row.status === 'success' ? '成功' : '失败' }}</Tag>
        </template>
      </PushGrid>
    </Card>
  </Page>
</template>
