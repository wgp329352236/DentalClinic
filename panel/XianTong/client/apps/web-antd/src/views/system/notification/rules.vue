<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteNotificationRule, getNotificationRules } from '#/api/config';

import RuleFormModal from './rule-form-modal.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: RuleFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { type: 'seq', title: '#', width: 50 },
      { field: 'ruleName', title: '规则名称', minWidth: 150 },
      { field: 'conditionType', title: '条件类型', width: 120 },
      { field: 'threshold', title: '阈值', width: 80 },
      { field: 'webhookUrl', title: 'Webhook', minWidth: 200 },
      { field: 'isEnabled', title: '状态', width: 80, slots: { default: 'enabled' } },
      { field: 'action', title: '操作', width: 150, fixed: 'right', slots: { default: 'action' } },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getNotificationRules();
          return { items: Array.isArray(data) ? data : [], total: Array.isArray(data) ? data.length : 0 };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

function onCreate() { formModalApi.setData({ mode: 'create' }).open(); }
function onEdit(row: any) { formModalApi.setData({ mode: 'edit', row }).open(); }

async function onDelete(row: any) {
  Modal.confirm({
    title: '删除规则', content: `确定删除规则"${row.ruleName}"？`, okType: 'danger',
    async onOk() { await deleteNotificationRule(row.id); message.success('删除成功'); gridApi.query(); },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="通知规则">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate"><Plus class="size-5" />新建规则</Button>
      </template>
      <template #enabled="{ row }">
        <Tag :color="row.isEnabled ? 'success' : 'default'">{{ row.isEnabled ? '启用' : '禁用' }}</Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" danger @click="onDelete(row)">删除</Button>
      </template>
    </Grid>
  </Page>
</template>
