<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getPointsRecords } from '#/api/points';

const [Grid] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { allowClear: true, placeholder: '用户ID' },
        fieldName: 'userId',
        label: '用户ID',
      },
      {
        component: 'Input',
        componentProps: {
          allowClear: true,
          placeholder: '如 consumption / checkin',
        },
        fieldName: 'sourceType',
        label: '来源类型',
      },
      {
        component: 'RangePicker',
        componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
        fieldName: 'dateRange',
        label: '时间范围',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 80 },
      { field: 'userId', title: '用户ID', width: 90 },
      {
        field: 'changeAmount',
        title: '变动积分',
        width: 100,
        slots: { default: 'change' },
      },
      { field: 'balanceAfter', title: '变动后余额', width: 110 },
      { field: 'sourceType', title: '来源', width: 150 },
      { field: 'description', title: '描述', minWidth: 160 },
      { field: 'expiresAt', title: '过期时间', minWidth: 170 },
      { field: 'createdAt', title: '创建时间', minWidth: 170 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any, formValues: any) => {
          const [startDate, endDate] = formValues?.dateRange ?? [];
          const res = await getPointsRecords({
            page: page.currentPage,
            pageSize: page.pageSize,
            userId: formValues?.userId || undefined,
            sourceType: formValues?.sourceType || undefined,
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
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="积分记录">
      <template #change="{ row }">
        <span
          :class="row.changeAmount >= 0 ? 'text-green-600' : 'text-red-500'"
        >
          {{ row.changeAmount >= 0 ? '+' : '' }}{{ row.changeAmount }}
        </span>
      </template>
    </Grid>
  </Page>
</template>
