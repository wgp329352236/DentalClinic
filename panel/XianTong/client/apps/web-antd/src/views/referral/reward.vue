<script lang="ts" setup>
import { Page } from '@vben/common-ui';

import { Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getReferralRewards } from '#/api/referral';

const rewardTypeMap: Record<string, string> = {
  balance: '余额',
  coupon: '优惠券',
  points: '积分',
};

const stageMap: Record<string, string> = {
  first_consumption: '首次消费',
  manual: '手动发放',
  registration: '注册',
  tier: '阶梯奖励',
};

const statusMap: Record<string, { color: string; text: string }> = {
  granted: { color: 'green', text: '已发放' },
  pending: { color: 'orange', text: '待发放' },
};

const [Grid] = useVbenVxeGrid({
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
            { label: '注册', value: 'registration' },
            { label: '首次消费', value: 'first_consumption' },
            { label: '阶梯奖励', value: 'tier' },
            { label: '手动发放', value: 'manual' },
          ],
        },
        fieldName: 'stage',
        label: '发放阶段',
      },
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          style: 'width: 100%',
          options: [
            { label: '待发放', value: 'pending' },
            { label: '已发放', value: 'granted' },
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
      { field: 'relationId', title: '关系ID', width: 90 },
      { field: 'referrerName', title: '推荐人', minWidth: 120 },
      { field: 'refereeName', title: '被推荐人', minWidth: 120 },
      {
        field: 'rewardType',
        title: '奖励类型',
        width: 100,
        slots: { default: 'rewardType' },
      },
      { field: 'rewardValue', title: '奖励值', width: 100 },
      {
        field: 'stage',
        title: '发放阶段',
        width: 110,
        slots: { default: 'stage' },
      },
      {
        field: 'status',
        title: '状态',
        width: 100,
        slots: { default: 'status' },
      },
      { field: 'grantedAt', title: '发放时间', minWidth: 170 },
      { field: 'createdAt', title: '创建时间', minWidth: 170 },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any, formValues: any) => {
          const res = await getReferralRewards({
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
    <Grid table-title="推荐奖励记录">
      <template #rewardType="{ row }">
        {{ rewardTypeMap[row.rewardType] || row.rewardType }}
      </template>
      <template #stage="{ row }">
        {{ stageMap[row.stage] || row.stage }}
      </template>
      <template #status="{ row }">
        <Tag :color="statusMap[row.status]?.color || 'default'">
          {{ statusMap[row.status]?.text || row.status }}
        </Tag>
      </template>
    </Grid>
  </Page>
</template>
