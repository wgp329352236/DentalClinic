<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getPointsRules } from '#/api/points';

import PointsRuleModal from './rule-modal.vue';

const ruleTypeMap: Record<string, string> = {
  earn: '获取',
  consume: '消耗',
  deduct: '扣减',
};

const [RuleModal, ruleModalApi] = useVbenModal({
  connectedComponent: PointsRuleModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      { field: 'ruleName', title: '规则名称', minWidth: 120 },
      {
        field: 'ruleType',
        title: '类型',
        width: 90,
        slots: { default: 'ruleType' },
      },
      { field: 'actionType', title: '动作', width: 140 },
      { field: 'pointsPerUnit', title: '单位积分', width: 100 },
      { field: 'unitDescription', title: '说明', minWidth: 140 },
      { field: 'dailyLimit', title: '每日上限', width: 90 },
      { field: 'monthlyLimit', title: '每月上限', width: 90 },
      { field: 'validityMonths', title: '有效期(月)', width: 100 },
      {
        field: 'status',
        title: '状态',
        width: 90,
        slots: { default: 'status' },
      },
      {
        field: 'action',
        title: '操作',
        width: 90,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getPointsRules();
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

function onCreate() {
  ruleModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  ruleModalApi.setData({ mode: 'edit', row }).open();
}
</script>

<template>
  <Page auto-content-height>
    <RuleModal @success="gridApi.query()" />
    <Grid table-title="积分规则">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建规则
        </Button>
      </template>
      <template #ruleType="{ row }">
        <Tag color="blue">{{ ruleTypeMap[row.ruleType] || row.ruleType }}</Tag>
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 1 ? 'green' : 'default'">
          {{ row.status === 1 ? '启用' : '停用' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
      </template>
    </Grid>
  </Page>
</template>
