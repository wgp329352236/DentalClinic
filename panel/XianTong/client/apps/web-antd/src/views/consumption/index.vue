<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getClinics } from '#/api/clinic';
import { getConsumptions } from '#/api/consumption';

import ConsumptionCreateModal from './create-modal.vue';
import ConsumptionDetailModal from './detail-modal.vue';
import ConsumptionResultModal from './result-modal.vue';

const statusMap: Record<string, { color: string; text: string }> = {
  failed: { color: 'red', text: '失败' },
  refunded: { color: 'orange', text: '已退款' },
  success: { color: 'green', text: '已完成' },
};

const [CreateModal, createModalApi] = useVbenModal({
  connectedComponent: ConsumptionCreateModal,
  destroyOnClose: true,
});

const [ResultModal, resultModalApi] = useVbenModal({
  connectedComponent: ConsumptionResultModal,
  destroyOnClose: true,
});

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: ConsumptionDetailModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '客户ID' },
        fieldName: 'userId',
        label: '客户ID',
      },
      {
        component: 'ApiSelect',
        componentProps: {
          allowClear: true,
          api: getClinics,
          labelField: 'name',
          placeholder: '全部门诊',
          style: 'width: 100%',
          valueField: 'id',
        },
        fieldName: 'clinicId',
        label: '门诊',
      },
      {
        component: 'RangePicker',
        componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
        fieldName: 'dateRange',
        label: '消费日期',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      {
        field: 'customerName',
        title: '客户',
        minWidth: 130,
        slots: { default: 'customer' },
      },
      { field: 'clinicName', title: '门诊', minWidth: 110 },
      { field: 'originalAmount', title: '原价', width: 90 },
      {
        field: 'discountAmount',
        title: '等级折扣',
        width: 95,
        slots: { default: 'deduction' },
      },
      { field: 'amount', title: '折后金额', width: 100 },
      {
        field: 'couponDeduct',
        title: '券抵扣',
        width: 90,
        slots: { default: 'deduction' },
      },
      {
        field: 'pointsDeduct',
        title: '积分抵扣',
        width: 95,
        slots: { default: 'deduction' },
      },
      { field: 'payAmount', title: '实付', width: 90 },
      { field: 'balanceUsed', title: '余额支付', width: 100 },
      { field: 'wechatPaid', title: '微信支付', width: 100 },
      {
        field: 'pointsEarned',
        title: '获得积分',
        width: 95,
        slots: { default: 'points' },
      },
      {
        field: 'status',
        title: '状态',
        width: 90,
        slots: { default: 'status' },
      },
      { field: 'operatorName', title: '录入人', width: 100 },
      { field: 'createdAt', title: '消费时间', minWidth: 170 },
      {
        field: 'action',
        title: '操作',
        width: 70,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any, formValues: any) => {
          const [startDate, endDate] = formValues?.dateRange ?? [];
          const res = await getConsumptions({
            page: page.currentPage,
            pageSize: page.pageSize,
            userId: formValues?.userId || undefined,
            clinicId: formValues?.clinicId || undefined,
            startDate,
            endDate,
          });
          return { items: res.list, total: res.total };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { custom: true, refresh: true, search: true, zoom: true },
  },
});

function onCreate() {
  createModalApi.open();
}

// 录入成功后先刷新流水，再展示本次结算明细（折扣/券/支付拆分/积分/升级/推荐奖励）
function onCreateSuccess(settlement: any) {
  gridApi.query();
  resultModalApi.setData({ settlement }).open();
}

function onDetail(row: any) {
  detailModalApi.setData({ row }).open();
}
</script>

<template>
  <Page auto-content-height>
    <CreateModal @success="onCreateSuccess" />
    <ResultModal />
    <DetailModal />
    <Grid table-title="消费流水">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          录入消费
        </Button>
      </template>
      <template #customer="{ row }">
        <div>{{ row.customerName || '-' }}</div>
        <div class="text-xs opacity-60">
          {{ row.phone || '无手机号' }} · ID:{{ row.userId }}
        </div>
      </template>
      <template #deduction="{ row, column }">
        <span v-if="Number(row[column.field]) > 0" class="text-red-500">
          -{{ Number(row[column.field]).toFixed(2) }}
        </span>
        <span v-else class="opacity-40">0.00</span>
      </template>
      <template #points="{ row }">
        <span v-if="row.pointsEarned > 0" class="text-green-600">
          +{{ row.pointsEarned }}
        </span>
        <span v-else class="opacity-40">0</span>
      </template>
      <template #status="{ row }">
        <Tag :color="statusMap[row.status]?.color || 'default'">
          {{ statusMap[row.status]?.text || row.status }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onDetail(row)">详情</Button>
      </template>
    </Grid>
  </Page>
</template>
