<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Avatar, Button, message, Modal } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getClinics } from '#/api/clinic';
import { deleteDoctor, getDoctors } from '#/api/doctor';

import DoctorFormModal from './form-modal.vue';

const clinicMap = ref<Record<number, string>>({});

onMounted(async () => {
  const clinics = await getClinics();
  clinicMap.value = Object.fromEntries(
    (clinics as any[]).map((c) => [c.id, c.name]),
  );
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: DoctorFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      {
        field: 'avatar',
        title: '头像',
        width: 70,
        slots: { default: 'avatar' },
      },
      { field: 'name', title: '姓名', minWidth: 100 },
      { field: 'title', title: '职称', width: 120 },
      { field: 'specialties', title: '擅长', minWidth: 160 },
      {
        field: 'clinicIds',
        title: '所属门诊',
        minWidth: 160,
        slots: { default: 'clinics' },
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
        query: async () => {
          const data = await getDoctors();
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, zoom: true },
  },
});

function clinicNames(ids: null | number[] | undefined) {
  if (!ids || ids.length === 0) return '-';
  return ids.map((id) => clinicMap.value[id] || `#${id}`).join('、');
}

function onCreate() {
  formModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  formModalApi.setData({ mode: 'edit', row }).open();
}

async function onDelete(row: any) {
  Modal.confirm({
    title: '删除医生',
    content: `确定删除医生「${row.name}」？`,
    okType: 'danger',
    async onOk() {
      await deleteDoctor(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="医生管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建医生
        </Button>
      </template>
      <template #avatar="{ row }">
        <Avatar :src="row.avatar" size="small">
          {{ row.name?.charAt(0) }}
        </Avatar>
      </template>
      <template #clinics="{ row }">
        {{ clinicNames(row.clinicIds) }}
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" danger @click="onDelete(row)">
          删除
        </Button>
      </template>
    </Grid>
  </Page>
</template>
