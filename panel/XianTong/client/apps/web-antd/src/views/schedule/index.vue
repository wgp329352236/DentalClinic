<script lang="ts" setup>
import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { EmptyIcon, Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getDoctors } from '#/api/doctor';
import { deleteSchedule, getSchedules } from '#/api/schedule';

import ScheduleFormModal from './form-modal.vue';

const dayOfWeekMap: Record<number, string> = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
};

const selectedDoctorId = ref<number | undefined>();

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: ScheduleFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'ApiSelect',
        componentProps: {
          api: getDoctors,
          labelField: 'name',
          placeholder: '请选择医生',
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
      {
        field: 'dayOfWeek',
        title: '星期',
        width: 90,
        slots: { default: 'dayOfWeek' },
      },
      {
        field: 'startTime',
        title: '时间段',
        minWidth: 150,
        slots: { default: 'timeRange' },
      },
      { field: 'maxAppointments', title: '最大预约数', width: 110 },
      { field: 'slotDuration', title: '时段时长', width: 100 },
      {
        field: 'isEnabled',
        title: '状态',
        width: 90,
        slots: { default: 'enabled' },
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
        query: async (_params: any, formValues: any) => {
          selectedDoctorId.value = formValues?.doctorId;
          // 后端要求必须带 doctorId，未选择医生时不发请求，避免报错
          if (!formValues?.doctorId) {
            return { items: [], total: 0 };
          }
          const data = await getSchedules({ doctorId: formValues.doctorId });
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, search: true, zoom: true },
  },
});

function onCreate() {
  formModalApi.setData({ doctorId: selectedDoctorId.value }).open();
}

function onEdit(row: any) {
  formModalApi.setData({ row }).open();
}

async function onDelete(row: any) {
  Modal.confirm({
    title: '删除排班',
    content: `确定删除该排班（${dayOfWeekMap[row.dayOfWeek]} ${row.startTime}-${row.endTime}）？`,
    okType: 'danger',
    async onOk() {
      await deleteSchedule(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="医生排班">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建排班
        </Button>
      </template>
      <template #dayOfWeek="{ row }">
        {{ dayOfWeekMap[row.dayOfWeek] || row.dayOfWeek }}
      </template>
      <template #timeRange="{ row }">
        {{ row.startTime }} - {{ row.endTime }}
      </template>
      <template #enabled="{ row }">
        <Tag :color="row.isEnabled === 1 ? 'green' : 'default'">
          {{ row.isEnabled === 1 ? '启用' : '停用' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" danger @click="onDelete(row)">
          删除
        </Button>
      </template>
      <!-- 后端按医生查询排班，未选医生时不发请求，需明确提示而非只显示「暂无数据」 -->
      <template #empty>
        <EmptyIcon class="mx-auto" />
        <div class="mt-2">
          {{
            selectedDoctorId
              ? '该医生暂无排班，可点击右上角「新建排班」添加'
              : '请先在上方选择医生，再查看或新建排班'
          }}
        </div>
      </template>
    </Grid>
  </Page>
</template>
