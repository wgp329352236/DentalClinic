<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getCouponRecords } from '#/api/coupon';

const statusMap: Record<string, { color: string; text: string }> = {
  expired: { color: 'default', text: '已过期' },
  unused: { color: 'green', text: '未使用' },
  used: { color: 'blue', text: '已使用' },
};

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { placeholder: '用户ID' },
        fieldName: 'userId',
        label: '用户ID',
      },
      {
        component: 'Input',
        componentProps: { placeholder: '模板ID' },
        fieldName: 'templateId',
        label: '模板ID',
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          style: 'width: 100%',
          options: [
            { label: '未使用', value: 'unused' },
            { label: '已使用', value: 'used' },
            { label: '已过期', value: 'expired' },
          ],
        },
        fieldName: 'status',
        label: '状态',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 80 },
      { field: 'userId', title: '用户ID', width: 100 },
      { field: 'couponName', title: '券名称', minWidth: 150 },
      { field: 'couponCode', title: '券码', minWidth: 180 },
      {
        field: 'status',
        title: '状态',
        width: 100,
        slots: { default: 'status' },
      },
      { field: 'usedAt', title: '使用时间', minWidth: 170 },
      { field: 'createdAt', title: '发放时间', minWidth: 170 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any, formValues: any) => {
          const res = await getCouponRecords({
            page: page.currentPage,
            pageSize: page.pageSize,
            ...formValues,
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
    <Grid table-title="优惠券发放记录">
      <template #status="{ row }">
        <Tag :color="statusMap[row.status]?.color || 'default'">
          {{ statusMap[row.status]?.text || row.status }}
        </Tag>
      </template>
    </Grid>
  </Page>
</template>
