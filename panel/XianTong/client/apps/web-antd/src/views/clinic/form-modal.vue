<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createClinic, updateClinic } from '#/api/clinic';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

const businessDayOptions = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 7 },
];

function toBusinessDayValues(raw: unknown): number[] {
  const values = Array.isArray(raw)
    ? raw
    : String(raw || '')
        .split(',')
        .map((item) => Number(item.trim()));
  return values
    .map(Number)
    .filter((day) => Number.isInteger(day) && day >= 1 && day <= 7);
}

function serializeBusinessDays(raw: unknown): string {
  return [...new Set(toBusinessDayValues(raw))]
    .sort((a, b) => a - b)
    .join(',');
}

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
      component: 'Select',
      fieldName: 'businessDays',
      label: '营业日',
      rules: 'selectRequired',
      defaultValue: [1, 2, 3, 4, 5, 6, 7],
      componentProps: {
        mode: 'multiple',
        options: businessDayOptions,
        placeholder: '请选择营业日',
        style: 'width: 100%',
      },
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
      fieldName: 'latitude',
      label: '纬度',
      componentProps: {
        min: -90,
        max: 90,
        precision: 6,
        step: 0.000001,
        placeholder: '选填，如 31.230416',
        style: 'width: 100%',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'longitude',
      label: '经度',
      componentProps: {
        min: -180,
        max: 180,
        precision: 6,
        step: 0.000001,
        placeholder: '选填，如 121.473701',
        style: 'width: 100%',
      },
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
        formApi.setValues({
          ...data.row,
          businessDays: toBusinessDayValues(data.row.businessDays),
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
    const latitude = values.latitude == null || values.latitude === ''
      ? null
      : Number(values.latitude);
    const longitude = values.longitude == null || values.longitude === ''
      ? null
      : Number(values.longitude);
    if ((latitude === null) !== (longitude === null)) {
      message.warning('纬度和经度需同时填写');
      return;
    }
    const payload = {
      ...values,
      businessDays: serializeBusinessDays(values.businessDays),
      latitude,
      longitude,
    };
    if (mode.value === 'create') {
      await createClinic(payload);
      message.success('创建成功');
    } else {
      await updateClinic(editingId.value, payload);
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
