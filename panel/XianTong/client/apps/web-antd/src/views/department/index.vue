<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getClinics } from '#/api/clinic';
import { deleteDepartment, getDepartments } from '#/api/department';

import DepartmentFormModal from './form-modal.vue';

const clinicMap = ref<Record<number, string>>({});

onMounted(async () => {
  const clinics = await getClinics();
  clinicMap.value = Object.fromEntries(
    (clinics as any[]).map((c) => [c.id, c.name]),
  );
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: DepartmentFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
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
        label: '所属门诊',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'id', title: 'ID', width: 70 },
      { field: 'deptName', title: '科室名称', minWidth: 140 },
      {
        field: 'clinicId',
        title: '所属门诊',
        minWidth: 140,
        slots: { default: 'clinic' },
      },
      { field: 'description', title: '描述', minWidth: 200 },
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
          const data = await getDepartments(formValues);
          return { items: data, total: data.length };
        },
      },
    },
    toolbarConfig: { custom: true, refresh: true, search: true, zoom: true },
  },
});

function onCreate() {
  formModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  formModalApi.setData({ mode: 'edit', row }).open();
}

async function onDelete(row: any) {
  Modal.confirm({
    title: '删除科室',
    content: `确定删除科室「${row.deptName}」？`,
    okType: 'danger',
    async onOk() {
      await deleteDepartment(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="科室管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建科室
        </Button>
      </template>
      <template #clinic="{ row }">
        {{ clinicMap[row.clinicId] || `门诊#${row.clinicId}` }}
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
