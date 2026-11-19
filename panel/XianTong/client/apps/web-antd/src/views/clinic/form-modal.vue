<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createClinic, updateClinic } from '#/api/clinic';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

const title = computed(() =>
  mode.value === 'create' ? '新建门诊' : '编辑门诊',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'name',
      label: '门诊名称',
      rules: 'required',
      componentProps: { placeholder: '如：总院门诊' },
    },
    {
      component: 'Input',
      fieldName: 'address',
      label: '地址',
      rules: 'required',
      componentProps: { placeholder: '详细地址' },
    },
    {
      component: 'Input',
      fieldName: 'phone',
      label: '联系电话',
      rules: 'required',
      componentProps: { placeholder: '如：010-12345678' },
    },
    {
      component: 'Input',
      fieldName: 'openTime',
      label: '开门时间',
      rules: 'required',
      componentProps: { placeholder: 'HH:mm，如 09:00' },
    },
    {
      component: 'Input',
      fieldName: 'closeTime',
      label: '关门时间',
      rules: 'required',
      componentProps: { placeholder: 'HH:mm，如 18:00' },
    },
    {
      component: 'Textarea',
      fieldName: 'description',
      label: '描述',
      componentProps: { rows: 2, placeholder: '门诊简介（选填）' },
    },
    {
      component: 'Input',
      fieldName: 'coverImage',
      label: '封面图',
      componentProps: { placeholder: '图片 URL（选填）' },
    },
    {
      component: 'InputNumber',
      fieldName: 'sortOrder',
      label: '排序',
      defaultValue: 0,
      componentProps: { min: 0, precision: 0, style: 'width: 100%' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      defaultValue: 1,
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '营业中', value: 1 },
          { label: '停用', value: 0 },
        ],
      },
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
        formApi.setValues({ ...data.row });
      } else {
        formApi.resetForm();
      }
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    if (mode.value === 'create') {
      await createClinic(values);
      message.success('创建成功');
    } else {
      await updateClinic(editingId.value, values);
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
