<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteCouponTemplate, getCouponTemplates } from '#/api/coupon';

import CouponFormModal from './form-modal.vue';
import PromoQrModalComponent from './promo-qr-modal.vue';

const couponTypeMap: Record<string, string> = {
  cash: '现金券',
  discount: '折扣券',
  service: '服务券',
};

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: CouponFormModal,
  destroyOnClose: true,
});

const [PromoQrModal, promoQrModalApi] = useVbenModal({
  connectedComponent: PromoQrModalComponent,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'name', title: '券名称', minWidth: 150 },
      {
        field: 'couponType',
        title: '类型',
        width: 100,
        slots: { default: 'couponType' },
      },
      { field: 'faceValue', title: '面额/折扣', width: 100 },
      { field: 'minConsumption', title: '使用门槛', width: 100 },
      { field: 'validFrom', title: '生效时间', minWidth: 170 },
      { field: 'validTo', title: '失效时间', minWidth: 170 },
      {
        field: 'issuedQuantity',
        title: '发放/总量',
        width: 110,
        slots: { default: 'stock' },
      },
      { field: 'usedQuantity', title: '已使用', width: 90 },
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
          const data = await getCouponTemplates();
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

function onPromoQr(row: any) {
  promoQrModalApi.setData({ row }).open();
}

async function onDelete(row: any) {
  Modal.confirm({
    title: '删除优惠券模板',
    content: `确定删除优惠券「${row.name}」？已发放的模板无法删除。`,
    okType: 'danger',
    async onOk() {
      await deleteCouponTemplate(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <PromoQrModal />
    <Grid table-title="优惠券模板">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建优惠券
        </Button>
      </template>
      <template #couponType="{ row }">
        <Tag color="blue">
          {{ couponTypeMap[row.couponType] || row.couponType }}
        </Tag>
      </template>
      <template #stock="{ row }">
        {{ row.issuedQuantity }} / {{ row.totalQuantity }}
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 1 ? 'green' : 'default'">
          {{ row.status === 1 ? '启用' : '停用' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" @click="onPromoQr(row)">推广码</Button>
        <Button type="link" size="small" danger @click="onDelete(row)">
          删除
        </Button>
      </template>
    </Grid>
  </Page>
</template>
