<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createMenu, updateMenu } from '#/api/system';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

const title = computed(() =>
  mode.value === 'create' ? '新建菜单' : '编辑菜单',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'menuName',
      label: '菜单名称',
      rules: 'required',
    },
    {
      component: 'Select',
      fieldName: 'menuType',
      label: '类型',
      rules: 'required',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '目录', value: 'directory' },
          { label: '菜单', value: 'menu' },
          { label: '按钮', value: 'button' },
        ],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'parentId',
      label: '父菜单 ID',
      componentProps: { placeholder: '空=顶级菜单' },
    },
    {
      component: 'Input',
      fieldName: 'routePath',
      label: '路由路径',
      componentProps: { placeholder: '/path' },
    },
    {
      component: 'Input',
      fieldName: 'component',
      label: '组件路径',
      componentProps: { placeholder: '/views/xxx/index' },
    },
    {
      component: 'Input',
      fieldName: 'permissionCode',
      label: '权限编码',
      componentProps: { placeholder: 'system:user:list' },
    },
    {
      component: 'Input',
      fieldName: 'icon',
      label: '图标',
      componentProps: { placeholder: 'lucide:xxx' },
    },
    {
      component: 'InputNumber',
      fieldName: 'sortOrder',
      label: '排序',
      componentProps: { min: 0 },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ mode: string; row?: any }>();
      mode.value = (data?.mode as any) || 'create';

      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        formApi.setValues(data.row);
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
      await createMenu(values);
      message.success('创建成功');
    } else {
      await updateMenu(editingId.value, values);
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
