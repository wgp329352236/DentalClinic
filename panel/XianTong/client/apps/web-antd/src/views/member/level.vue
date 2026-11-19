<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteMemberLevel, getMemberLevels } from '#/api/member';

import LevelModal from './level-modal.vue';

const [LevelModalRef, levelModalApi] = useVbenModal({
  connectedComponent: LevelModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      { field: 'levelCode', title: '等级编码', width: 90 },
      { field: 'levelName', title: '等级名称', minWidth: 120 },
      { field: 'minConsumption', title: '升级门槛(元)', width: 120 },
      {
        field: 'discountRate',
        title: '折扣',
        width: 90,
        slots: { default: 'discount' },
      },
      { field: 'benefits', title: '权益说明', minWidth: 180 },
      { field: 'userCount', title: '关联客户', width: 90 },
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
          const data = await getMemberLevels();
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

function onCreate() {
  levelModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  levelModalApi.setData({ mode: 'edit', row }).open();
}

function onDelete(row: any) {
  Modal.confirm({
    title: '删除会员等级',
    content:
      row.userCount > 0
        ? `等级「${row.levelName}」下仍有 ${row.userCount} 位客户，无法删除。请先调整这些客户的等级。`
        : `确定删除等级「${row.levelName}」？`,
    okType: 'danger',
    async onOk() {
      // 有关联客户时后端返回 409，由请求拦截器统一提示
      await deleteMemberLevel(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <LevelModalRef @success="gridApi.query()" />
    <Grid table-title="会员等级">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建等级
        </Button>
      </template>
      <template #discount="{ row }">
        {{ row.discountRate >= 1 ? '不打折' : `${(row.discountRate * 100).toFixed(1)}折` }}
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
