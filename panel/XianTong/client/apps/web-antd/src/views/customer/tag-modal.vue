<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createCustomerTag,
  updateCustomerTag,
} from '#/api/customerTag';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

// 与 antd Tag 预设色保持一致
const colorOptions = [
  'blue',
  'green',
  'orange',
  'red',
  'purple',
  'cyan',
  'gold',
  'magenta',
].map((value) => ({ label: value, value }));

const title = computed(() =>
  mode.value === 'create' ? '新建客户标签' : '编辑客户标签',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'tagName',
      label: '标签名',
      rules: 'required',
      componentProps: { placeholder: '如：高价值客户 / 种植意向' },
    },
    {
      component: 'Select',
      fieldName: 'color',
      label: '颜色',
      defaultValue: 'blue',
      componentProps: { style: 'width: 100%', options: colorOptions },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: { rows: 2, placeholder: '标签用途说明（可选）' },
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
        formApi.setValues({
          tagName: data.row.tagName,
          color: data.row.color,
          remark: data.row.remark,
        });
      } else {
        editingId.value = 0;
        formApi.resetForm();
      }
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    if (mode.value === 'create') {
      await createCustomerTag(values as any);
      message.success('创建成功');
    } else {
      await updateCustomerTag(editingId.value, values as any);
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
