<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getNotificationHistory } from '#/api/config';

const [Grid] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { type: 'seq', title: '#', width: 50 },
      { field: 'ruleName', title: '规则名称', minWidth: 150 },
      {
        field: 'deliveryStatus',
        title: '发送状态',
        width: 90,
        slots: { default: 'status' },
      },
      { field: 'triggeredAt', title: '触发时间', minWidth: 170 },
      { field: 'messageContent', title: '通知内容', minWidth: 200 },
      { field: 'errorMessage', title: '错误信息', minWidth: 150 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any) => {
          const res = await getNotificationHistory({
            page: page.currentPage,
            pageSize: page.pageSize,
          });
          return { items: res?.list || [], total: res?.total || 0 };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { refresh: true, zoom: true },
  },
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="通知历史">
      <template #status="{ row }">
        <Tag :color="row.deliveryStatus === 'sent' ? 'success' : 'error'">
          {{ row.deliveryStatus === 'sent' ? '已发送' : '失败' }}
        </Tag>
      </template>
    </Grid>
  </Page>
</template>
