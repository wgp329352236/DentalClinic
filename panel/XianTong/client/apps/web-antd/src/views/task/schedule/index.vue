<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getScheduledTasks } from '#/api/task';

const [Grid] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { type: 'seq', title: '#', width: 50 },
      { field: 'taskName', title: '任务名称', minWidth: 180 },
      { field: 'cronExpression', title: 'Cron 表达式', minWidth: 150 },
      { field: 'lastExecuteTime', title: '上次执行时间', minWidth: 170 },
      { field: 'lastResult', title: '上次结果', width: 90, slots: { default: 'lastResult' } },
      { field: 'nextExecuteTime', title: '下次执行时间', minWidth: 170 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getScheduledTasks();
          return { items: Array.isArray(data) ? data : [], total: Array.isArray(data) ? data.length : 0 };
        },
      },
    },
    toolbarConfig: { refresh: true, zoom: true },
  },
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="定时任务">
      <template #lastResult="{ row }">
        <Tag :color="row.lastResult === 'success' ? 'success' : row.lastResult === 'failed' ? 'error' : 'default'">
          {{ row.lastResult === 'success' ? '成功' : row.lastResult === 'failed' ? '失败' : row.lastResult || '-' }}
        </Tag>
      </template>
    </Grid>
  </Page>
</template>
