<script lang="ts" setup>
import { ref, computed } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Tree } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createRole, getAllPermissions, updateRole } from '#/api/system';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);
const permTree = ref<any[]>([]);
const checkedKeys = ref<number[]>([]);

const title = computed(() => mode.value === 'create' ? '新建角色' : '编辑角色');

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'roleCode',
      label: '角色编码',
      rules: 'required',
      componentProps: { placeholder: '如 editor' },
    },
    {
      component: 'Input',
      fieldName: 'roleName',
      label: '角色名称',
      rules: 'required',
      componentProps: { placeholder: '请输入角色名称' },
    },
    {
      component: 'Input',
      fieldName: 'description',
      label: '描述',
      componentProps: { placeholder: '角色描述' },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ mode: string; row?: any }>();
      mode.value = data?.mode as any || 'create';

      // 加载权限树
      const groups = await getAllPermissions();
      permTree.value = groups.map((g: any) => ({
        title: g.module,
        key: `module_${g.module}`,
        children: g.permissions.map((p: any) => ({
          title: p.permissionName,
          key: p.id,
        })),
      }));

      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        formApi.setValues({
          roleCode: data.row.roleCode,
          roleName: data.row.roleName,
          description: data.row.description,
        });
        checkedKeys.value = data.row.permissions?.map((p: any) => p.id) || [];
      } else {
        formApi.resetForm();
        checkedKeys.value = [];
      }
    }
  },
  async onConfirm() {
    const values = await formApi.getValues();
    const permissionIds = checkedKeys.value.filter(k => typeof k === 'number');

    if (mode.value === 'create') {
      await createRole({ ...values, permissionIds });
      message.success('创建成功');
    } else {
      await updateRole(editingId.value, { ...values, permissionIds });
      message.success('更新成功');
    }
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal :title="title">
    <Form />
    <div class="mt-4">
      <div class="mb-2 font-medium">权限分配</div>
      <Tree
        v-model:checkedKeys="checkedKeys"
        :tree-data="permTree"
        checkable
        default-expand-all
      />
    </div>
  </Modal>
</template>
