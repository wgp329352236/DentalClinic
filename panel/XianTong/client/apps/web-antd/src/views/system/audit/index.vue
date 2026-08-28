<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';

import { Button, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getAuditLogDetail, getAuditLogs } from '#/api/config';

import AuditDetailModal from './detail-modal.vue';

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: AuditDetailModal,
  destroyOnClose: true,
});

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      { component: 'Input', fieldName: 'username', label: '操作人', componentProps: { placeholder: '用户名' } },
      {
        component: 'Select', fieldName: 'operationType', label: '操作类型',
        componentProps: { placeholder: '全部', allowClear: true, options: [
          { label: '创建', value: 'create' },
          { label: '更新', value: 'update' },
          { label: '删除', value: 'delete' },
          { label: '登录', value: 'login' },
          { label: '登出', value: 'logout' },
        ]},
      },
      { component: 'Input', fieldName: 'targetResource', label: '资源', componentProps: { placeholder: '目标资源' } },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { type: 'seq', title: '#', width: 50 },
      { field: 'username', title: '操作人', width: 100 },
      { field: 'operationType', title: '操作类型', width: 90, slots: { default: 'opType' } },
      { field: 'targetResource', title: '资源', width: 100 },
      { field: 'targetId', title: '目标 ID', width: 100 },
      { field: 'responseCode', title: '响应码', width: 80 },
      { field: 'ipAddress', title: 'IP', width: 130 },
      { field: 'createdAt', title: '时间', minWidth: 170 },
      { field: 'action', title: '操作', width: 80, fixed: 'right', slots: { default: 'action' } },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const res = await getAuditLogs({ page: page.currentPage, pageSize: page.pageSize, ...formValues });
          return { items: res?.list || [], total: res?.total || 0 };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { refresh: true, zoom: true },
  },
});

function onDetail(row: any) {
  detailModalApi.setData({ id: row.id }).open();
}
</script>

<template>
  <Page auto-content-height>
    <DetailModal />
    <Grid table-title="审计日志">
      <template #opType="{ row }">
        <Tag :color="row.operationType === 'delete' ? 'error' : row.operationType === 'create' ? 'success' : 'blue'">
          {{ row.operationType }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onDetail(row)">详情</Button>
      </template>
    </Grid>
  </Page>
</template>
