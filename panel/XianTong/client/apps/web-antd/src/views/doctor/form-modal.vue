<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getClinics } from '#/api/clinic';
import { createDoctor, updateDoctor } from '#/api/doctor';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

const title = computed(() =>
  mode.value === 'create' ? '新建医生' : '编辑医生',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'name',
      label: '姓名',
      rules: 'required',
      componentProps: { placeholder: '医生姓名' },
    },
    {
      component: 'Input',
      fieldName: 'title',
      label: '职称',
      rules: 'required',
      componentProps: { placeholder: '如：主任医师' },
    },
    {
      component: 'Input',
      fieldName: 'specialties',
      label: '擅长',
      rules: 'required',
      componentProps: { placeholder: '如：牙齿矫正、种植牙' },
    },
    {
      component: 'Textarea',
      fieldName: 'introduction',
      label: '简介',
      componentProps: { rows: 3, placeholder: '医生简介（选填）' },
    },
    {
      component: 'Input',
      fieldName: 'avatar',
      label: '头像',
      componentProps: { placeholder: '头像 URL（选填）' },
    },
    {
      component: 'ApiSelect',
      fieldName: 'clinicIds',
      label: '所属门诊',
      componentProps: {
        allowClear: true,
        api: getClinics,
        labelField: 'name',
        mode: 'multiple',
        placeholder: '可多选',
        style: 'width: 100%',
        valueField: 'id',
      },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      defaultValue: 1,
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '在职', value: 1 },
          { label: '离职', value: 0 },
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
        formApi.setValues({
          name: data.row.name,
          title: data.row.title,
          specialties: data.row.specialties,
          introduction: data.row.introduction,
          avatar: data.row.avatar,
          clinicIds: data.row.clinicIds ?? [],
          status: data.row.status ?? 1,
        });
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
      await createDoctor(values);
      message.success('创建成功');
    } else {
      await updateDoctor(editingId.value, values);
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
