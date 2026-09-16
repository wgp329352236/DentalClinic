<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteRechargeTier, getRechargeTiers } from '#/api/recharge';

import TierModal from './tier-modal.vue';

const [TierModalRef, tierModalApi] = useVbenModal({
  connectedComponent: TierModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      { field: 'tierName', title: '档位名称', minWidth: 140 },
      { field: 'minAmount', title: '充值门槛(元)', width: 120 },
      {
        field: 'bonusRate',
        title: '赠送比例',
        width: 100,
        slots: { default: 'bonusRate' },
      },
      { field: 'bonusAmount', title: '预计赠送(元)', width: 120 },
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
        width: 130,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getRechargeTiers();
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

function onCreate() {
  tierModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  tierModalApi.setData({ mode: 'edit', row }).open();
}

function onDelete(row: any) {
  Modal.confirm({
    title: '删除充值档位',
    content: `确定删除档位「${row.tierName}」？删除后小程序端不再展示。`,
    okType: 'danger',
    async onOk() {
      await deleteRechargeTier(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <TierModalRef @success="gridApi.query()" />
    <Grid table-title="充值档位">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建档位
        </Button>
      </template>
      <template #bonusRate="{ row }">
        {{ (row.bonusRate * 100).toFixed(1) }}%
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
