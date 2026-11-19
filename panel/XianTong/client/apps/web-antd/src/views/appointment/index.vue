<script lang="ts" setup>
import { h } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { cancelAppointment, confirmAppointment, getAppointments, rejectAppointment } from '#/api/appointment';
import { getClinics } from '#/api/clinic';
import { getDoctors } from '#/api/doctor';

import AppointmentDetailModal from './detail-modal.vue';

const statusMap: Record<string, { color: string; text: string }> = {
  pending: { color: 'orange', text: '待确认' },
  confirmed: { color: 'blue', text: '已确认' },
  arrived: { color: 'cyan', text: '已到店' },
  completed: { color: 'green', text: '已完成' },
  cancelled: { color: 'default', text: '已取消' },
  no_show: { color: 'red', text: '爽约' },
};

const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: AppointmentDetailModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Select',
        componentProps: {
          allowClear: true,
          style: 'width: 100%',
          options: [
            { label: '待确认', value: 'pending' },
            { label: '已确认', value: 'confirmed' },
            { label: '已到店', value: 'arrived' },
            { label: '已完成', value: 'completed' },
            { label: '已取消', value: 'cancelled' },
            { label: '爽约', value: 'no_show' },
          ],
        },
        fieldName: 'status',
        label: '状态',
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
        component: 'ApiSelect',
        componentProps: {
          allowClear: true,
          api: getDoctors,
          labelField: 'name',
          placeholder: '全部医生',
          style: 'width: 100%',
          valueField: 'id',
        },
        fieldName: 'doctorId',
        label: '医生',
      },
      {
        component: 'DatePicker',
        componentProps: {
          style: 'width: 100%',
          type: 'date',
        },
        fieldName: 'startDate',
        label: '开始日期',
      },
      {
        component: 'DatePicker',
        componentProps: {
          style: 'width: 100%',
          type: 'date',
        },
        fieldName: 'endDate',
        label: '结束日期',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      { field: 'userName', title: '客户', minWidth: 100 },
      { field: 'userPhone', title: '电话', width: 130 },
      { field: 'clinicName', title: '门诊', minWidth: 120 },
      { field: 'doctorName', title: '医生', width: 100 },
      { field: 'appointmentDate', title: '预约日期', width: 120 },
      { field: 'startTime', title: '时间', width: 100 },
      {
        field: 'status',
        title: '状态',
        width: 100,
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
        query: async ({ page }: any, formValues: any) => {
          const res = await getAppointments({
            ...formValues,
            page: page.currentPage,
            pageSize: page.pageSize,
          });
          return { items: res.list, total: res.total };
        },
      },
    },
    pagerConfig: { enabled: true },
    toolbarConfig: { custom: true, refresh: true, search: true, zoom: true },
  },
});

function onDetail(row: any) {
  detailModalApi.setData({ id: row.id }).open();
}

async function onCancel(row: any) {
  Modal.confirm({
    title: '取消预约',
    content: `确定取消该预约（客户：${row.userName || row.userId}）？`,
    okType: 'danger',
    async onOk() {
      await cancelAppointment(row.id);
      message.success('预约已取消');
      gridApi.query();
    },
  });
}

// 确认预约：仅待确认（pending）状态可操作
async function onConfirm(row: any) {
  Modal.confirm({
    title: '确认预约',
    content: `确认该预约（客户：${row.userName || row.userId}）？确认后状态将变为「已确认」。`,
    async onOk() {
      await confirmAppointment(row.id);
      message.success('预约已确认');
      gridApi.query();
    },
  });
}

// 拒绝预约：需填写拒绝原因，流转为已取消
function onReject(row: any) {
  let rejectReason = '';
  Modal.confirm({
    title: '拒绝预约',
    content: h('div', { style: 'margin-top: 12px' }, [
      h('textarea', {
        rows: 3,
        placeholder: '请输入拒绝原因',
        style: 'width: 100%; padding: 8px; border: 1px solid #d9d9d9; border-radius: 4px; resize: vertical; box-sizing: border-box;',
        onInput: (e: Event) => { rejectReason = (e.target as HTMLTextAreaElement).value; },
      }),
    ]),
    okText: '确认拒绝',
    okType: 'danger',
    async onOk() {
      const reason = rejectReason.trim();
      if (!reason) {
        message.warning('请填写拒绝原因');
        return Promise.reject();
      }
      await rejectAppointment(row.id, reason);
      message.success('预约已拒绝');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <DetailModal @success="gridApi.query()" />
    <Grid table-title="预约管理">
      <template #status="{ row }">
        <Tag :color="statusMap[row.status]?.color || 'default'">
          {{ statusMap[row.status]?.text || row.status }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onDetail(row)">详情</Button>
        <template v-if="row.status === 'pending'">
          <Button type="link" size="small" @click="onConfirm(row)">确认</Button>
          <Button type="link" size="small" danger @click="onReject(row)">拒绝</Button>
        </template>
        <Button
          v-if="row.status !== 'cancelled' && row.status !== 'completed'"
          type="link"
          size="small"
          danger
          @click="onCancel(row)"
        >
          取消
        </Button>
      </template>
    </Grid>
  </Page>
</template>
