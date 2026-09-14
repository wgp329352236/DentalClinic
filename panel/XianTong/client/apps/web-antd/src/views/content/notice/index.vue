<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteNotice, getNotices } from '#/api/content';

import NoticeFormModal from './form-modal.vue';

const linkTypeMap: Record<string, string> = {
  none: '不跳转',
  page: '小程序页面',
  webview: '外部网页',
};

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: NoticeFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      { field: 'content', title: '公告内容', minWidth: 260 },
      {
        field: 'linkType',
        title: '跳转',
        width: 110,
        slots: { default: 'link' },
      },
      { field: 'sortOrder', title: '排序', width: 80 },
      {
        field: 'startAt',
        title: '生效排期',
        width: 210,
        slots: { default: 'schedule' },
      },
      {
        field: 'status',
        title: '状态',
        width: 90,
        slots: { default: 'status' },
      },
      {
        field: 'action',
        title: '操作',
        width: 140,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getNotices();
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

function onCreate() {
  formModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  formModalApi.setData({ mode: 'edit', row }).open();
}

async function onDelete(row: any) {
  Modal.confirm({
    title: '删除公告',
    content: `确定删除公告「${row.content}」？`,
    okType: 'danger',
    async onOk() {
      await deleteNotice(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="公告管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建公告
        </Button>
      </template>
      <template #link="{ row }">
        <span :title="row.linkTarget ?? ''">
          {{ linkTypeMap[row.linkType] ?? row.linkType }}
        </span>
      </template>
      <template #schedule="{ row }">
        <span v-if="!row.startAt && !row.endAt">长期有效</span>
        <span v-else>
          {{ row.startAt?.slice(0, 10) || '不限' }} ~
          {{ row.endAt?.slice(0, 10) || '不限' }}
        </span>
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 1 ? 'green' : 'default'">
          {{ row.status === 1 ? '启用' : '停用' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" danger @click="onDelete(row)">
          删除
        </Button>
      </template>
    </Grid>
  </Page>
</template>
