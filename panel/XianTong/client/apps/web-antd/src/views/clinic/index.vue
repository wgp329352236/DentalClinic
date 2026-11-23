<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteClinic, getClinics } from '#/api/clinic';

import ClinicFormModal from './form-modal.vue';
import ClinicQrModal from './qrcode-modal.vue';

const weekdayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

function formatBusinessDays(raw: unknown): string {
  const days = String(raw || '')
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((day) => Number.isInteger(day) && day >= 1 && day <= 7)
    .toSorted((a, b) => a - b);
  if (days.length === 7) return '每天';
  if (days.length === 0) return '未设置';
  // 连续星期合并为区间，与 C 端文案保持一致
  const parts: string[] = [];
  const firstDay = days[0];
  if (firstDay === undefined) return '未设置';
  let rangeStart = firstDay;
  let rangeEnd = firstDay;
  const label = (day: number) => weekdayLabels[day - 1] ?? `星期${day}`;
  const flush = () => {
    parts.push(
      rangeStart === rangeEnd
        ? label(rangeStart)
        : `${label(rangeStart)}至${label(rangeEnd)}`,
    );
  };
  for (const day of days.slice(1)) {
    if (day === rangeEnd + 1) {
      rangeEnd = day;
      continue;
    }
    flush();
    rangeStart = day;
    rangeEnd = day;
  }
  flush();
  return parts.join('、');
}

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: ClinicFormModal,
  destroyOnClose: true,
});

const [QrModal, qrModalApi] = useVbenModal({
  connectedComponent: ClinicQrModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      { field: 'name', title: '门诊名称', minWidth: 140 },
      { field: 'address', title: '地址', minWidth: 200 },
      { field: 'phone', title: '联系电话', width: 140 },
      {
        field: 'openTime',
        title: '营业时间',
        width: 150,
        slots: { default: 'businessHours' },
      },
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
        width: 200,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getClinics();
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

function onShowQr(row: any) {
  qrModalApi.setData({ row }).open();
}

async function onDelete(row: any) {
  Modal.confirm({
    title: '删除门诊',
    content: `确定删除门诊「${row.name}」？删除前请确保其下无科室/医生关联。`,
    okType: 'danger',
    async onOk() {
      await deleteClinic(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <QrModal />
    <Grid table-title="门诊管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建门诊
        </Button>
      </template>
      <template #businessHours="{ row }">
        <div>{{ formatBusinessDays(row.businessDays) }}</div>
        <div class="text-xs opacity-60">
          {{ row.openTime }} - {{ row.closeTime }}
        </div>
      </template>
      <template #status="{ row }">
        <Tag :color="row.status === 1 ? 'green' : 'default'">
          {{ row.status === 1 ? '营业中' : '停用' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" @click="onShowQr(row)">签到码</Button>
        <Button type="link" size="small" danger @click="onDelete(row)">
          删除
        </Button>
      </template>
    </Grid>
  </Page>
</template>
