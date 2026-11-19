<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Image, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteBanner, getBanners } from '#/api/content';

import BannerFormModal from './form-modal.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: BannerFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      {
        field: 'imageUrl',
        title: '图片',
        width: 130,
        slots: { default: 'image' },
      },
      { field: 'title', title: '标题', minWidth: 150 },
      {
        field: 'activityTitle',
        title: '关联活动',
        minWidth: 160,
        slots: { default: 'activity' },
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
          const data = await getBanners();
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
    title: '删除轮播图',
    content: `确定删除轮播图「${row.title}」？删除后小程序首页将不再展示该项。`,
    okType: 'danger',
    async onOk() {
      await deleteBanner(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="轮播图管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建轮播图
        </Button>
      </template>
      <template #image="{ row }">
        <Image :src="row.imageUrl" :height="40" :width="100" />
      </template>
      <template #activity="{ row }">
        <!-- 历史轮播图可能还没绑定活动，旧数据在 C 端已不展示，这里明确提示需要重新绑定 -->
        <span v-if="row.activityTitle">{{ row.activityTitle }}</span>
        <Tag v-else color="orange">未绑定活动</Tag>
      </template>
      <template #schedule="{ row }">
        <!-- 起止都为空即长期有效，显示占位符比留白更明确 -->
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
