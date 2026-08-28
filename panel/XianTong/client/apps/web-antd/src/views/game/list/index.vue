<script lang="ts" setup>
import { useRouter } from 'vue-router';

import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { useAccess } from '@vben/access';
import { getGameList } from '#/api/game';

import CreateGameModal from './create-modal.vue';
import CloneGameModal from './clone-modal.vue';
import DeleteGameModal from './delete-modal.vue';

const router = useRouter();
const { hasAccessByCodes } = useAccess();

const [CreateModal, createModalApi] = useVbenModal({
  connectedComponent: CreateGameModal,
  destroyOnClose: true,
});

const [CloneModal, cloneModalApi] = useVbenModal({
  connectedComponent: CloneGameModal,
  destroyOnClose: true,
});

// BUG-15.1.C1 fix: 使用 useVbenModal 替代 Modal.confirm
const [DeleteModal, deleteModalApi] = useVbenModal({
  connectedComponent: DeleteGameModal,
  destroyOnClose: true,
});

// BUG-15.1.C5 fix: type bitmask 映射
const TYPE_MAP: Record<number, string> = { 1: '测试', 2: '正式', 4: '预约', 8: 'MINI' };
// BUG-15.1.C6 fix: payType bitmask 映射
const PAY_TYPE_MAP: Record<number, string> = { 1: 'SIMPLE', 2: 'IAP', 4: 'IAA', 8: 'MINI', 16: 'PRE' };

function decodeBitmask(value: number, map: Record<number, string>): string {
  const labels: string[] = [];
  for (const [bit, label] of Object.entries(map)) {
    if (value & Number(bit)) labels.push(label);
  }
  return labels.length > 0 ? labels.join('+') : String(value);
}

// BUG-15.1.U4 / BUG-15.1.U5 fix: 日期格式化
function formatDate(value: number): string {
  if (!value || value === 0) return '运营中';
  // BUG-15.1.U6 fix: 哨兵值 29991024 也视为"运营中"
  if (value >= 29990000) return '运营中';
  const str = String(value);
  if (str.length === 8) {
    return `${str.slice(0, 4)}-${str.slice(4, 6)}-${str.slice(6, 8)}`;
  }
  return str;
}

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { placeholder: '搜索游戏代码或名称' },
        fieldName: 'keyword',
        label: '关键词',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      // BUG-15.1.U8 fix: 添加行号列
      { type: 'seq', title: '#', width: 50 },
      { field: 'code', title: '游戏代码', minWidth: 120 },
      { field: 'name', title: '游戏名称', minWidth: 150 },
      { field: 'area', title: '地区', width: 80 },
      // BUG-15.1.U2 fix: type 列使用 slot 显示可读文本
      { field: 'type', title: '类型', width: 100, slots: { default: 'type' } },
      // BUG-15.1.U3 fix: payType 列使用 slot 显示可读文本
      { field: 'payType', title: '付费', width: 130, slots: { default: 'payType' } },
      // BUG-15.1.U4 fix: openTime 格式化
      { field: 'openTime', title: '开服时间', width: 110, slots: { default: 'openTime' } },
      // BUG-15.1.U5 fix: closeTime 格式化
      { field: 'closeTime', title: '关服时间', width: 110, slots: { default: 'closeTime' } },
      { field: 'channelsCount', title: '渠道数', width: 80 },
      { field: 'configStatus', title: '状态', width: 100, slots: { default: 'configStatus' } },
      { field: 'action', title: '操作', width: 200, fixed: 'right', slots: { default: 'action' } },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }, formValues) => {
          const res = await getGameList({
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

function onCreate() {
  createModalApi.setData({}).open();
}

function onEdit(row: any) {
  router.push(`/game/detail/${row.code}`);
}

function onClone(row: any) {
  cloneModalApi.setData({ sourceCode: row.code, sourceName: row.name }).open();
}

// BUG-15.1.C1 / BUG-15.1.U1 fix: 使用 useVbenModal 实现删除确认
function onDelete(row: any) {
  deleteModalApi.setData({ code: row.code, name: row.name }).open();
}
</script>

<template>
  <Page auto-content-height>
    <CreateModal @success="gridApi.query()" />
    <CloneModal @success="gridApi.query()" />
    <DeleteModal @success="gridApi.query()" />
    <Grid table-title="游戏管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建游戏
        </Button>
      </template>
      <!-- BUG-15.1.U2 fix: type 显示可读文本 -->
      <template #type="{ row }">
        <Tag>{{ decodeBitmask(row.type, TYPE_MAP) }}</Tag>
      </template>
      <!-- BUG-15.1.U3 fix: payType 显示可读文本 -->
      <template #payType="{ row }">
        <Tag>{{ decodeBitmask(row.payType, PAY_TYPE_MAP) }}</Tag>
      </template>
      <!-- BUG-15.1.U4 fix: openTime 格式化 -->
      <template #openTime="{ row }">
        {{ formatDate(row.openTime) }}
      </template>
      <!-- BUG-15.1.U5 / BUG-15.1.U6 fix: closeTime 格式化 + 哨兵值处理 -->
      <template #closeTime="{ row }">
        {{ formatDate(row.closeTime) }}
      </template>
      <template #configStatus="{ row }">
        <Tag :color="row.configStatus === 'complete' ? 'success' : 'warning'">
          {{ row.configStatus === 'complete' ? '✅ 完整' : '⚠️ 不完整' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <!-- BUG-15.1.U9 fix: 操作按钮权限控制 -->
        <Button v-if="hasAccessByCodes(['game:update'])" type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button v-if="hasAccessByCodes(['game:create'])" type="link" size="small" @click="onClone(row)">克隆</Button>
        <Button v-if="hasAccessByCodes(['game:delete'])" type="link" size="small" danger @click="onDelete(row)">删除</Button>
      </template>
    </Grid>
  </Page>
</template>
