<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getClinics } from '#/api/clinic';
import { getRechargeRecords } from '#/api/recharge';

const statusMap: Record<string, { color: string; text: string }> = {
  pending: { color: 'orange', text: '待支付' },
  success: { color: 'green', text: '已支付' },
  failed: { color: 'red', text: '支付失败' },
  refunded: { color: 'default', text: '已退款' },
};

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '姓名/手机号' },
        fieldName: 'keyword',
        label: '关键词',
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
        component: 'Select',
        componentProps: {
          allowClear: true,
          style: 'width: 100%',
          options: [
            { label: '待支付', value: 'pending' },
            { label: '已支付', value: 'success' },
            { label: '支付失败', value: 'failed' },
            { label: '已退款', value: 'refunded' },
          ],
        },
        fieldName: 'status',
        label: '状态',
      },
      {
        component: 'RangePicker',
        componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
        fieldName: 'dateRange',
        label: '充值时间',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 80 },
      { field: 'userName', title: '客户', minWidth: 100 },
      { field: 'userPhone', title: '手机号', width: 130 },
      { field: 'clinicName', title: '门诊', minWidth: 120 },
      { field: 'amount', title: '充值金额', width: 100 },
      { field: 'bonusAmount', title: '赠送金额', width: 100 },
      { field: 'paymentMethod', title: '支付方式', width: 100 },
      {
        field: 'status',
        title: '状态',
        width: 90,
        slots: { default: 'status' },
      },
      { field: 'outTradeNo', title: '商户订单号', minWidth: 180 },
      { field: 'paidAt', title: '支付时间', minWidth: 170 },
      { field: 'createdAt', title: '创建时间', minWidth: 170 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any, formValues: any) => {
          const [dateFrom, dateTo] = formValues?.dateRange ?? [];
          const res = await getRechargeRecords({
            page: page.currentPage,
            pageSize: page.pageSize,
            keyword: formValues?.keyword || undefined,
            clinicId: formValues?.clinicId ?? undefined,
            status: formValues?.status || undefined,
            dateFrom,
            dateTo,
          });
          return { items: res.list, total: res.total };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { custom: true, refresh: true, search: true, zoom: true },
  },
});
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="充值流水">
      <template #status="{ row }">
        <Tag :color="statusMap[row.status]?.color || 'default'">
          {{ statusMap[row.status]?.text || row.status }}
        </Tag>
      </template>
    </Grid>
  </Page>
</template>
