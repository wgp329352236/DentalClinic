<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';

import { Button, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getReferralRelations } from '#/api/referral';

import RewardModal from './reward-modal.vue';

const rewardStatusMap: Record<string, { color: string; text: string }> = {
  full: { color: 'green', text: '已发放' },
  partial: { color: 'orange', text: '部分发放' },
  pending: { color: 'default', text: '待发放' },
};

const [RewardModalComp, rewardModalApi] = useVbenModal({
  connectedComponent: RewardModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { placeholder: '推荐人ID' },
        fieldName: 'referrerId',
        label: '推荐人ID',
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          style: 'width: 100%',
          options: [
            { label: '待发放', value: 'pending' },
            { label: '部分发放', value: 'partial' },
            { label: '已发放', value: 'full' },
          ],
        },
        fieldName: 'rewardStatus',
        label: '奖励状态',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 80 },
      { field: 'referrerName', title: '推荐人', minWidth: 130 },
      { field: 'refereeName', title: '被推荐人', minWidth: 130 },
      { field: 'referralCode', title: '推荐码', minWidth: 140 },
      { field: 'registeredAt', title: '注册时间', minWidth: 170 },
      { field: 'firstConsumedAt', title: '首消时间', minWidth: 170 },
      {
        field: 'rewardStatus',
        title: '奖励状态',
        width: 110,
        slots: { default: 'rewardStatus' },
      },
      {
        field: 'action',
        title: '操作',
        width: 120,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any, formValues: any) => {
          const res = await getReferralRelations({
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

function onGrant(row: any) {
  rewardModalApi.setData({ row }).open();
}
</script>

<template>
  <Page auto-content-height>
    <RewardModalComp @success="gridApi.query()" />
    <Grid table-title="推荐关系">
      <template #rewardStatus="{ row }">
        <Tag :color="rewardStatusMap[row.rewardStatus]?.color || 'default'">
          {{ rewardStatusMap[row.rewardStatus]?.text || row.rewardStatus }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onGrant(row)">发放奖励</Button>
      </template>
    </Grid>
  </Page>
</template>
