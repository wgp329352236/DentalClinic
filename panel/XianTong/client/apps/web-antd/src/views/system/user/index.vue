<script lang="ts" setup>
import { onMounted, ref } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, message, Modal, Switch, Tag } from 'ant-design-vue';

import { useVbenVxeGrid } from '#/adapter/vxe-table';
import { getClinics } from '#/api/clinic';
import {
  deleteUser,
  getUserList,
  resetUserPassword,
  updateUserStatus,
} from '#/api/system';

import UserFormModal from './form-modal.vue';

// 门店ID→名称映射（授权门店列展示用）
const clinicMap = ref<Record<number, string>>({});
onMounted(async () => {
  const clinics = await getClinics();
  clinicMap.value = Object.fromEntries(
    clinics.map((c: any) => [c.id, c.name]),
  );
});

const [FormModal, formModalApi] = useVbenModal({
  connectedComponent: UserFormModal,
  destroyOnClose: true,
});

const [Grid, gridApi] = useVbenVxeGrid({
  formOptions: {
    schema: [
      {
        component: 'Input',
        componentProps: { placeholder: '搜索用户名或显示名' },
        fieldName: 'keyword',
        label: '关键词',
      },
    ],
    submitOnChange: true,
  },
  gridOptions: {
    columns: [
      { field: 'username', title: '用户名', minWidth: 120 },
      { field: 'displayName', title: '显示名', minWidth: 120 },
      {
        field: 'roles',
        title: '角色',
        minWidth: 150,
        slots: { default: 'roles' },
      },
      {
        field: 'clinicIds',
        title: '授权门店',
        minWidth: 180,
        slots: { default: 'clinics' },
      },
      {
        field: 'status',
        title: '状态',
        width: 80,
        slots: { default: 'status' },
      },
      { field: 'lastLoginAt', title: '最后登录', minWidth: 170 },
      { field: 'createdAt', title: '创建时间', minWidth: 170 },
      {
        field: 'action',
        title: '操作',
        width: 220,
        fixed: 'right',
        slots: { default: 'action' },
      },
    ],
    height: 'auto',
    proxyConfig: {
      ajax: {
        query: async ({ page }: any, formValues: any) => {
          const res = await getUserList({
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
  formModalApi.setData({ mode: 'create' }).open();
}

function onEdit(row: any) {
  formModalApi.setData({ mode: 'edit', row }).open();
}

async function onToggleStatus(row: any) {
  const newStatus = row.status === 1 ? 0 : 1;
  const action = newStatus === 0 ? '禁用' : '启用';
  Modal.confirm({
    title: '确认操作',
    content: `确定${action}用户 ${row.username}？`,
    async onOk() {
      await updateUserStatus(row.id, newStatus);
      message.success(`${action}成功`);
      gridApi.query();
    },
  });
}

async function onResetPassword(row: any) {
  Modal.confirm({
    title: '重置密码',
    content: `确定重置用户 ${row.username} 的密码为 123456？`,
    async onOk() {
      await resetUserPassword(row.id, '123456');
      message.success('密码已重置为 123456');
    },
  });
}

async function onDelete(row: any) {
  Modal.confirm({
    title: '删除用户',
    content: `确定删除用户 ${row.username}？此操作不可恢复！`,
    okType: 'danger',
    async onOk() {
      await deleteUser(row.id);
      message.success('删除成功');
      gridApi.query();
    },
  });
}
</script>

<template>
  <Page auto-content-height>
    <FormModal @success="gridApi.query()" />
    <Grid table-title="用户管理">
      <template #toolbar-tools>
        <Button type="primary" @click="onCreate">
          <Plus class="size-5" />
          新建用户
        </Button>
      </template>
      <template #roles="{ row }">
        <Tag v-for="r in row.roles" :key="r.roleCode" color="blue">
          {{ r.roleName }}
        </Tag>
      </template>
      <template #clinics="{ row }">
        <template v-if="row.clinicIds?.length">
          <Tag
            v-for="cid in row.clinicIds"
            :key="cid"
            class="mb-0.5"
            color="geekblue"
          >
            {{ clinicMap[cid] || `门店#${cid}` }}
          </Tag>
        </template>
        <span v-else class="opacity-50">全部门店 / 未配置</span>
      </template>
      <template #status="{ row }">
        <Switch
          :checked="row.status === 1"
          @change="() => onToggleStatus(row)"
        />
      </template>
      <template #action="{ row }">
        <Button type="link" size="small" @click="onEdit(row)">编辑</Button>
        <Button type="link" size="small" @click="onResetPassword(row)">
          重置密码
        </Button>
        <Button type="link" size="small" danger @click="onDelete(row)">
          删除
        </Button>
      </template>
    </Grid>
  </Page>
</template>
