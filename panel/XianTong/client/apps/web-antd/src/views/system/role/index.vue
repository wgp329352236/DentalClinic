<script lang="ts" setup>
import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteRole, getRoleList } from '#/api/system';

import RoleFormModal from './form-modal.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: RoleFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'roleCode', title: '角色编码', minWidth: 150 },
      { field: 'roleName', title: '角色名称', minWidth: 150 },
      { field: 'description', title: '描述', minWidth: 200 },
      {
        field: 'permissions',
        title: '权限数',
        width: 90,
        slots: { default: 'permCount' },
      },
      {
        field: 'isSystem',
        title: '系统角色',
        width: 90,
        slots: { default: 'isSystem' },
      },
      { field: 'createdAt', title: '创建时间', minWidth: 170 },
      {
        field: 'action',
        title: '操作',
        width: 150,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async () => {
          const data = await getRoleList();
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

async function onDelete(row: any) {
  if (row.isSystem) {
    message.warning('系统内置角色不可删除');
    return;
  }
  Modal.confirm({
    title: '删除角色',
    content: `确定删除角色 ${row.roleName}？`,
    okType: 'danger',
    async onOk() {
      await deleteRole(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="角色管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建角色
        </Button>
      </template>
      <template #permCount="{ row }">
        <Tag color="blue">{{ row.permissions?.length || 0 }}</Tag>
      </template>
      <template #isSystem="{ row }">
        <Tag :color="row.isSystem ? 'red' : 'default'">{{ row.isSystem ? '是' : '否' }}</Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" danger :disabled="!!row.isSystem" @click="onDelete(row)">删除</Button>
      </template>
    </Grid>
  </Page>
</template>
