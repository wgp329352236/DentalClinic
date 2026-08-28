<script lang="ts" setup>
import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Alert, Button, Input, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { clearDlq, discardDlqItem, getDlqList, retryAllDlq, retryDlqItem } from '#/api/queue';

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      { component: 'Input', fieldName: 'keyword', label: '关键词', componentProps: { placeholder: '搜索任务类型或游戏代码' } },
      {
        component: 'Select', fieldName: 'status', label: '状态',
        componentProps: { placeholder: '全部', allowClear: true, options: [
          { label: '待处理', value: 'pending' },
          { label: '已重试', value: 'retried' },
          { label: '已丢弃', value: 'discarded' },
        ]},
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { type: 'seq', title: '#', width: 50 },
      { field: 'taskType', title: '任务类型', minWidth: 120 },
      { field: 'gameCode', title: '游戏代码', width: 100 },
      { field: 'errorMessage', title: '错误信息', minWidth: 200 },
      { field: 'retryCount', title: '重试次数', width: 80 },
      { field: 'status', title: '状态', width: 90, slots: { default: 'status' } },
      { field: 'createdAt', title: '创建时间', minWidth: 170 },
      { field: 'action', title: '操作', width: 150, fixed: 'right', slots: { default: 'action' } },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const res = await getDlqList({ page: page.currentPage, pageSize: page.pageSize, ...formValues });
          return { items: res.list, total: res.total };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

async function onRetry(row: any) {
  Modal.confirm({
    title: '重试任务',
    content: `确定重试该死信任务？`,
    async onOk() {
      await retryDlqItem(row.id);
      message.success('已加入重试队列');
      gridApi.query();
    },
  });
}

async function onDiscard(row: any) {
  Modal.confirm({
    title: '丢弃任务',
    content: `确定丢弃该死信任务？丢弃后不可恢复。`,
    okType: 'danger',
    async onOk() {
      await discardDlqItem(row.id);
      message.success('已丢弃');
      gridApi.query();
    },
  });
}

async function onRetryAll() {
  Modal.confirm({
    title: '全部重试',
    content: '确定重试所有待处理的死信任务？',
    async onOk() {
      await retryAllDlq();
      message.success('已全部加入重试队列');
      gridApi.query();
    },
  });
}

// 清空需要输入 CLEAR 确认
const clearInput = ref('');
const showClearModal = ref(false);

function onClear() {
  clearInput.value = '';
  showClearModal.value = true;
}

async function confirmClear() {
  if (clearInput.value !== 'CLEAR') {
    message.error('请输入 CLEAR 确认');
    return;
  }
  await clearDlq();
  message.success('已清空死信队列');
  showClearModal.value = false;
  gridApi.query();
}
</script>

<template>
  <Page auto-content-height>
    <Modal v-model:open="showClearModal" title="清空死信队列" @ok="confirmClear">
      <Alert type="error" show-icon message="此操作将清空所有死信任务，不可恢复！" class="mb-4" />
      <p class="mb-2 text-sm">请输入 <code class="rounded bg-gray-100 px-1 dark:bg-gray-700">CLEAR</code> 确认：</p>
      <Input v-model:value="clearInput" placeholder="请输入 CLEAR" />
    </Modal>

    <Grid table-title="死信队列">
      <template #toolbar-tools>
        <div class="flex gap-2">
          <Button @click="onRetryAll">全部重试</Button>
          <Button danger @click="onClear">清空</Button>
        </div>
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 'pending' ? 'warning' : row.status === 'retried' ? 'success' : 'default'">
          {{ row.status === 'pending' ? '待处理' : row.status === 'retried' ? '已重试' : '已丢弃' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button v-if="row.status === 'pending'" type="link" size="small" @click="onRetry(row)">重试</Button>
        <Button v-if="row.status === 'pending'" type="link" size="small" danger @click="onDiscard(row)">丢弃</Button>
      </template>
    </Grid>
  </Page>
</template>
