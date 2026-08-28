<script lang="ts" setup>
import { ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { deleteMenu, getMenuTree } from '#/api/system';

import MenuFormModal from './form-modal.vue';

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: MenuFormModal,
  destroyOnClose: true,
});

// 将树形数据扁平化用于 VxeTable
function flattenTree(nodes: any[], parentId: any = null): any[] {
  const result: any[] = [];
  for (const node of nodes) {
    result.push({ ...node, _parentId: parentId });
    if (node.children?.length) {
      result.push(...flattenTree(node.children, node.id));
    }
  }
  return result;
}

const [Grid, gridApi] = useVbenVxeGrid({
  gridOptions: {
    columns: [
      { field: 'menuName', title: '菜单名称', treeNode: true, minWidth: 200 },
      {
        field: 'menuType',
        title: '类型',
        width: 90,
        slots: { default: 'menuType' },
      },
      { field: 'routePath', title: '路由', minWidth: 180 },
      { field: 'permissionCode', title: '权限', minWidth: 150 },
      { field: 'icon', title: '图标', minWidth: 180 },
      { field: 'sortOrder', title: '排序', width: 70 },
      {
        field: 'action',
        title: '操作',
        width: 150,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    treeConfig: {
      parentField: '_parentId',
      rowField: 'id',
      transform: true,
    },
    proxyConfig: {
      ajax: {
        query: async () => {
          const tree = await getMenuTree();
          const flat = flattenTree(tree);
          return { items: flat, total: flat.length };
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
  Modal.confirm({
    title: '删除菜单',
    content: `确定删除菜单"${row.menuName}"及其所有子菜单？`,
    okType: 'danger',
    async onOk() {
      await deleteMenu(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="菜单管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建菜单
        </Button>
      </template>
      <template #menuType="{ row }">
        <Tag :color="row.menuType === 'directory' ? 'blue' : row.menuType === 'menu' ? 'green' : 'orange'">
          {{ row.menuType === 'directory' ? '目录' : row.menuType === 'menu' ? '菜单' : '按钮' }}
        </Tag>
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" danger @click="onDelete(row)">删除</Button>
      </template>
    </Grid>
  </Page>
</template>
