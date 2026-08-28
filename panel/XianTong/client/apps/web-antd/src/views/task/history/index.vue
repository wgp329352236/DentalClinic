<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';

import { Button, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getTaskDetail, getTaskHistory } from '#/api/task';

import TaskDetailModal from './detail-modal.vue';

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: TaskDetailModal,
  destroyOnClose: true,
});

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      { component: 'Input', fieldName: 'keyword', label: '关键词', componentProps: { placeholder: '任务ID或游戏代码' } },
      {
        component: 'Select', fieldName: 'type', label: '类型',
        componentProps: { placeholder: '全部', allowClear: true, options: [
          { label: 'Pull', value: 'pull' },
          { label: 'Push', value: 'push' },
          { label: 'Export', value: 'export' },
        ]},
      },
      {
        component: 'Select', fieldName: 'status', label: '状态',
        componentProps: { placeholder: '全部', allowClear: true, options: [
          { label: '运行中', value: 'running' },
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
      { field: 'taskId', title: '任务 ID', minWidth: 180 },
      { field: 'taskType', title: '类型', width: 80 },
      { field: 'gameCode', title: '游戏', width: 100 },
      { field: 'status', title: '状态', width: 90, slots: { default: 'status' } },
      { field: 'startTime', title: '开始时间', minWidth: 170 },
      { field: 'endTime', title: '结束时间', minWidth: 170 },
      { field: 'duration', title: '耗时', width: 90 },
      { field: 'action', title: '操作', width: 80, fixed: 'right', slots: { default: 'action' } },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const res = await getTaskHistory({ page: page.currentPage, pageSize: page.pageSize, ...formValues });
          return { items: res.list, total: res.total };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { refresh: true, zoom: true },
  },
});

function onDetail(row: any) {
  detailModalApi.setData({ taskId: row.taskId }).open();
}
</script>

<template>
  <Page auto-content-height>
    <DetailModal />
    <Grid table-title="任务历史">
      <template #status="{ row }">
        <Tag :color="row.status === 'success' ? 'success' : row.status === 'failed' ? 'error' : 'processing'">
          {{ row.status === 'success' ? '成功' : row.status === 'failed' ? '失败' : '运行中' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onDetail(row)">详情</Button>
      </template>
    </Grid>
  </Page>
</template>
