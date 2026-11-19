<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteMessageTemplate,
  getMessageTemplates,
} from '#/api/messageTemplate';

import TemplateFormModal from './form-modal.vue';

const [TemplateModalRef, templateModalApi] = useVbenModal({
  connectedComponent: TemplateFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      { field: 'title', title: '模板标题', minWidth: 150 },
      {
        field: 'templateKey',
        title: '业务标识',
        width: 200,
        slots: { default: 'templateKey' },
      },
      { field: 'templateId', title: '微信模板ID', width: 180 },
      { field: 'triggerEvent', title: '触发事件', width: 160 },
      { field: 'content', title: '模板内容', minWidth: 240 },
      {
        field: 'status',
        title: '状态',
        width: 90,
        slots: { default: 'status' },
      },
      {
        field: 'action',
        title: '操作',
        width: 130,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getMessageTemplates();
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

function onCreate() {
  templateModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  templateModalApi.setData({ mode: 'edit', row }).open();
}

function onDelete(row: any) {
  Modal.confirm({
    title: '删除消息模板',
    content: `确定删除模板「${row.title}」？删除后对应通知将降级为站内信。`,
    okType: 'danger',
    async onOk() {
      await deleteMessageTemplate(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <TemplateModalRef @success="gridApi.query()" />
    <Grid table-title="消息模板">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建模板
        </Button>
      </template>
      <template #templateKey="{ row }">
        <Tag :color="row.builtIn ? 'purple' : 'default'">
          {{ row.templateKey }}
        </Tag>
        <Tag v-if="row.builtIn" color="gold">内置</Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 1 ? 'green' : 'default'">
          {{ row.status === 1 ? '启用' : '停用' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button
          v-if="!row.builtIn"
          type="link"
          size="small"
          danger
          @click="onDelete(row)"
        >
          删除
        </Button>
      </template>
    </Grid>
  </Page>
</template>
