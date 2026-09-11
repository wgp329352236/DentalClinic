<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createPointsRule, updatePointsRule } from '#/api/points';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

const title = computed(() =>
  mode.value === 'create' ? '新建积分规则' : '编辑积分规则',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'ruleName',
      label: '规则名称',
      rules: 'required',
      componentProps: { placeholder: '如：消费积分' },
    },
    {
      component: 'Select',
      fieldName: 'ruleType',
      label: '规则类型',
      rules: 'selectRequired',
      defaultValue: 'earn',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '获取', value: 'earn' },
          { label: '消耗', value: 'consume' },
          { label: '扣减', value: 'deduct' },
        ],
      },
    },
    {
      component: 'Input',
      fieldName: 'actionType',
      label: '动作类型',
      rules: 'required',
      componentProps: { placeholder: '如：consumption / recharge / signup' },
    },
    {
      component: 'InputNumber',
      fieldName: 'pointsPerUnit',
      label: '单位积分',
      rules: 'required',
      componentProps: {
        min: 1,
        precision: 0,
        style: 'width: 100%',
        placeholder: '每单位对应积分（须大于0）',
      },
    },
    {
      component: 'Input',
      fieldName: 'unitDescription',
      label: '单位说明',
      componentProps: { placeholder: '如：每消费1元' },
    },
    {
      component: 'InputNumber',
      fieldName: 'dailyLimit',
      label: '每日上限',
      componentProps: {
        min: 0,
        precision: 0,
        style: 'width: 100%',
        placeholder: '留空为不限',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'monthlyLimit',
      label: '每月上限',
      componentProps: {
        min: 0,
        precision: 0,
        style: 'width: 100%',
        placeholder: '留空为不限',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'validityMonths',
      label: '有效期(月)',
      defaultValue: 12,
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
      await createPointsRule(values);
      message.success('创建成功');
    } else {
      await updatePointsRule(editingId.value, values);
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
