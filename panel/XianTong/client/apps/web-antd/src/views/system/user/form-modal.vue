<script lang="ts" setup>
import { ref, computed } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createUser, getRoleList, updateUser } from '#/api/system';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);
const roles = ref<any[]>([]);

const title = computed(() => mode.value === 'create' ? '新建用户' : '编辑用户');

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'username',
      label: '用户名',
      rules: 'required',
      componentProps: { placeholder: '请输入用户名', disabled: false },
    },
    {
      component: 'InputPassword',
      fieldName: 'password',
      label: '密码',
      rules: 'required',
      componentProps: { placeholder: '请输入密码' },
      dependencies: {
        if(values) { return mode.value === 'create'; },
        triggerFields: ['_mode'],
      },
    },
    {
      component: 'Input',
      fieldName: 'displayName',
      label: '显示名称',
      rules: 'required',
      componentProps: { placeholder: '请输入显示名称' },
    },
    {
      component: 'Input',
      fieldName: 'email',
      label: '邮箱',
      componentProps: { placeholder: '请输入邮箱' },
    },
    {
      component: 'Select',
      fieldName: 'roleIds',
      label: '角色',
      rules: 'selectRequired',
      componentProps: {
        mode: 'multiple',
        placeholder: '请选择角色',
        options: [],
        style: 'width: 100%',
      },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ mode: string; row?: any }>();
      mode.value = data?.mode as any || 'create';

      // 加载角色列表
      const roleData = await getRoleList();
      roles.value = roleData;
      formApi.updateSchema([{
        fieldName: 'roleIds',
        componentProps: {
          mode: 'multiple',
          placeholder: '请选择角色',
          options: roleData.map((r: any) => ({ label: r.roleName, value: r.id })),
        },
      }]);

      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        formApi.setValues({
          username: data.row.username,
          displayName: data.row.displayName,
          email: data.row.email,
          roleIds: data.row.roles?.map((r: any) => r.id) || [],
        });
      } else {
        formApi.resetForm();
      }
    }
  },
  async onConfirm() {
    const values = await formApi.getValues();
    if (mode.value === 'create') {
      await createUser(values);
      message.success('创建成功');
    } else {
      await updateUser(editingId.value, {
        displayName: values.displayName,
        email: values.email,
        roleIds: values.roleIds,
      });
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
  </Modal>
</template>
