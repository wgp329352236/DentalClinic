<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import {
  deleteCustomerTag,
  getCustomerTags,
} from '#/api/customerTag';

import TagFormModal from './tag-modal.vue';

const [TagModalRef, tagModalApi] = useVbenModal({
  connectedComponent: TagFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      {
        field: 'tagName',
        title: '标签名',
        minWidth: 140,
        slots: { default: 'tagName' },
      },
      { field: 'color', title: '颜色标识', width: 110 },
      { field: 'remark', title: '备注', minWidth: 180 },
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
          const data = await getCustomerTags();
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

function onCreate() {
  tagModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  tagModalApi.setData({ mode: 'edit', row }).open();
}

function onDelete(row: any) {
  Modal.confirm({
    title: '删除标签',
    content: `确定删除标签「${row.tagName}」？该标签会从所有客户身上移除。`,
    okType: 'danger',
    async onOk() {
      await deleteCustomerTag(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}

</script>

<template>
  <Page auto-content-height>
    <TagModalRef @success="gridApi.query()" />
    <Grid table-title="客户标签">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建标签
        </Button>
      </template>
      <template #tagName="{ row }">
        <Tag :color="row.color">{{ row.tagName }}</Tag>
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
