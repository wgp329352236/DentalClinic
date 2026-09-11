<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { adjustLevel, adjustPoints, adjustWallet } from '#/api/customer';

const emit = defineEmits<{ success: [] }>();

const customerId = ref<number>(0);
const customerInfo = ref('');

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Select',
      fieldName: 'adjustType',
      label: '调整类型',
      defaultValue: 'wallet',
      rules: 'selectRequired',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '余额', value: 'wallet' },
          { label: '会员等级', value: 'level' },
          { label: '积分', value: 'points' },
        ],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'amount',
      label: '调整金额',
      rules: 'required',
      componentProps: {
        precision: 2,
        style: 'width: 100%',
        placeholder: '正数增加，负数扣减',
      },
      dependencies: {
        if(values) {
          return values.adjustType === 'wallet';
        },
        triggerFields: ['adjustType'],
      },
    },
    {
      component: 'Select',
      fieldName: 'levelCode',
      label: '目标等级',
      rules: 'selectRequired',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '普通会员', value: 1 },
          { label: '银卡会员', value: 2 },
          { label: '金卡会员', value: 3 },
          { label: '钻石会员', value: 4 },
        ],
      },
      dependencies: {
        if(values) {
          return values.adjustType === 'level';
        },
        triggerFields: ['adjustType'],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'pointsAmount',
      label: '调整积分',
      rules: 'required',
      componentProps: {
        precision: 0,
        style: 'width: 100%',
        placeholder: '正数增加，负数扣减',
      },
      dependencies: {
        if(values) {
          return values.adjustType === 'points';
        },
        triggerFields: ['adjustType'],
      },
    },
    {
      component: 'Textarea',
      fieldName: 'reason',
      label: '调整原因',
      rules: 'required',
      componentProps: { rows: 2, placeholder: '必填，将记入流水' },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ row?: any }>();
      const row = data?.row;
      customerId.value = row?.id ?? 0;
      customerInfo.value = row
        ? `${row.realName || row.nickname || '客户'}（ID:${row.id}）`
        : '';
      formApi.resetForm();
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    if (values.adjustType === 'wallet') {
      await adjustWallet(customerId.value, {
        amount: values.amount,
        reason: values.reason,
      });
    } else if (values.adjustType === 'level') {
      await adjustLevel(customerId.value, {
        levelCode: values.levelCode,
        reason: values.reason,
      });
    } else {
      await adjustPoints(customerId.value, {
        amount: values.pointsAmount,
        reason: values.reason,
      });
    }
    message.success('调整成功');
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal title="客户调整">
    <div class="mb-4 text-sm opacity-70">{{ customerInfo }}</div>
    <Form />
  </Modal>
</template>
