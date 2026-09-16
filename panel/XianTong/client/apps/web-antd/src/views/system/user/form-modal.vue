<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getClinics } from '#/api/clinic';
import { createUser, getRoleList, updateUser } from '#/api/system';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);
const roles = ref<any[]>([]);

const title = computed(() =>
  mode.value === 'create' ? '新建用户' : '编辑用户',
);

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
    {
      component: 'ApiSelect',
      fieldName: 'clinicIds',
      label: '授权门店',
      // 数据隔离：不选 = 看不到任何门店业务数据；
      // 角色含「全部门店数据」权限时该限制不生效
      componentProps: {
        mode: 'multiple',
        allowClear: true,
        api: getClinics,
        labelField: 'name',
        valueField: 'id',
        placeholder: '不选则无任何门店数据权限',
        style: 'width: 100%',
      },
      help: '控制该账号可见的门店业务数据；角色拥有「全部门店数据」权限时不生效',
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ mode: string; row?: any }>();
      mode.value = (data?.mode as any) || 'create';

      // 密码仅新建时可填：编辑模式隐藏该字段（hide 会移除 dom 并跳过 required 校验）
      formApi.updateSchema([
        { fieldName: 'password', hide: mode.value !== 'create' },
      ]);

      // 加载角色列表
      const roleData = await getRoleList();
      roles.value = roleData;
      formApi.updateSchema([
        {
          fieldName: 'roleIds',
          componentProps: {
            mode: 'multiple',
            placeholder: '请选择角色',
            options: roleData.map((r: any) => ({
              label: r.roleName,
              value: r.id,
            })),
          },
        },
      ]);

      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        formApi.setValues({
          username: data.row.username,
          displayName: data.row.displayName,
          email: data.row.email,
          roleIds: data.row.roles?.map((r: any) => r.id) || [],
          clinicIds: data.row.clinicIds || [],
        });
      } else {
        formApi.resetForm();
      }
    }
  },
  async onConfirm() {
    // 表单声明了 required 规则，提交前必须校验，否则空表单会直接打到后端
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    if (mode.value === 'create') {
      await createUser({
        ...values,
        // 未选择时 ApiSelect 的值可能为 undefined，后端要求 List<Long>
        clinicIds: values.clinicIds || [],
      });
      message.success('创建成功');
    } else {
      await updateUser(editingId.value, {
        displayName: values.displayName,
        email: values.email,
        roleIds: values.roleIds,
        // 编辑时全量覆盖门店授权（含清空为空数组）
        clinicIds: values.clinicIds || [],
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
