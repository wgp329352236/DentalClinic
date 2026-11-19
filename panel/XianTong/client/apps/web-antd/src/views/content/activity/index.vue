<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Image, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteActivity, getActivities } from '#/api/content';

import ActivityFormModal from './form-modal.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: ActivityFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      {
        field: 'coverImage',
        title: '封面',
        width: 110,
        slots: { default: 'cover' },
      },
      { field: 'title', title: '活动名称', minWidth: 180 },
      {
        field: 'startAt',
        title: '活动时间',
        width: 210,
        slots: { default: 'schedule' },
      },
      {
        field: 'bannerId',
        title: '首页轮播',
        width: 110,
        slots: { default: 'banner' },
      },
      { field: 'sortOrder', title: '排序', width: 80 },
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
          const data = await getActivities();
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
    title: '删除活动',
    content: `确定删除活动「${row.title}」？已绑定轮播图的活动需要先删除轮播图。`,
    okType: 'danger',
    async onOk() {
      await deleteActivity(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="活动管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建活动
        </Button>
      </template>
      <template #cover="{ row }">
        <Image
          v-if="row.coverImage"
          :src="row.coverImage"
          :height="40"
          :width="80"
        />
        <span v-else class="text-muted-foreground">未上传</span>
      </template>
      <template #schedule="{ row }">
        <span v-if="!row.startAt && !row.endAt">长期有效</span>
        <span v-else>
          {{ row.startAt?.slice(0, 10) || '不限' }} ~
          {{ row.endAt?.slice(0, 10) || '不限' }}
        </span>
      </template>
      <template #banner="{ row }">
        <Tag v-if="row.bannerId" color="green">已配置</Tag>
        <Tag v-else color="orange">未配置</Tag>
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
