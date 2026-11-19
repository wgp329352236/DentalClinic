<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createCouponTemplate, updateCouponTemplate } from '#/api/coupon';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

const title = computed(() =>
  mode.value === 'create' ? '新建优惠券' : '编辑优惠券',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'name',
      label: '券名称',
      rules: 'required',
      componentProps: { placeholder: '如：新人专享券' },
    },
    {
      component: 'Select',
      fieldName: 'couponType',
      label: '券类型',
      rules: 'selectRequired',
      defaultValue: 'cash',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '现金券', value: 'cash' },
          { label: '折扣券', value: 'discount' },
          { label: '服务券', value: 'service' },
        ],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'faceValue',
      label: '面额/折扣',
      rules: 'required',
      componentProps: {
        min: 0,
        precision: 2,
        style: 'width: 100%',
        placeholder: '现金券填金额，折扣券填如0.8',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'minConsumption',
      label: '使用门槛',
      defaultValue: 0,
      componentProps: {
        min: 0,
        precision: 2,
        style: 'width: 100%',
        placeholder: '满多少可用，0为无门槛',
      },
    },
    {
      component: 'Input',
      fieldName: 'applicableDepartments',
      label: '适用科室',
      componentProps: { placeholder: '科室ID的JSON数组，如 [1,2]，留空为通用' },
    },
    {
      component: 'DatePicker',
      fieldName: 'validFrom',
      label: '生效日期',
      rules: 'selectRequired',
      componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
    },
    {
      component: 'DatePicker',
      fieldName: 'validTo',
      label: '失效日期',
      rules: 'selectRequired',
      componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
    },
    {
      component: 'InputNumber',
      fieldName: 'totalQuantity',
      label: '发放总量',
      rules: 'required',
      componentProps: { min: 1, precision: 0, style: 'width: 100%' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      defaultValue: 1,
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '启用', value: 1 },
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
          // 后端返回 "YYYY-MM-DD HH:mm:ss"，DatePicker 仅需日期部分
          validFrom: data.row.validFrom?.slice(0, 10),
          validTo: data.row.validTo?.slice(0, 10),
        });
      } else {
        formApi.resetForm();
      }
    }
  },
  async onConfirm() {
    // 表单声明了 required / selectRequired 规则，提交前必须校验，
    // 否则空表单会直接打到后端
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    if (mode.value === 'create') {
      await createCouponTemplate(values);
      message.success('创建成功');
    } else {
      await updateCouponTemplate(editingId.value, values);
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
