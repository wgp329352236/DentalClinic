<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { cancelAppointment, getAppointments } from '#/api/appointment';
import { getClinics } from '#/api/clinic';
import { getDoctors } from '#/api/doctor';

import AppointmentDetailModal from './detail-modal.vue';

const statusMap: Record<string, { color: string; text: string }> = {
  pending: { color: 'orange', text: '待确认' },
  confirmed: { color: 'blue', text: '已确认' },
  arrived: { color: 'cyan', text: '已到店' },
  completed: { color: 'green', text: '已完成' },
  cancelled: { color: 'default', text: '已取消' },
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
        width: 140,
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
