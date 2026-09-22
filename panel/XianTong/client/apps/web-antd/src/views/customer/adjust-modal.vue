<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message, Modal } from 'ant-design-vue';

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

    // 二次确认对话框（需求 P0-5 合规：关键操作需二次确认）
    let confirmTitle = '';
    let confirmContent = '';
    if (values.adjustType === 'wallet') {
      confirmTitle = '确认调整余额';
      confirmContent = `即将为客户调整余额 ${values.amount >= 0 ? '+' : ''}${values.amount} 元，原因：${values.reason}。此操作将记入审计日志，是否继续？`;
    } else if (values.adjustType === 'level') {
      const levelNames: Record<number, string> = { 1: '普通会员', 2: '银卡会员', 3: '金卡会员', 4: '钻石会员' };
      confirmTitle = '确认调整会员等级';
      confirmContent = `即将将客户等级调整为「${levelNames[values.levelCode] || values.levelCode}」，原因：${values.reason}。此操作将记入审计日志，是否继续？`;
    } else {
      confirmTitle = '确认调整积分';
      confirmContent = `即将为客户调整积分 ${values.pointsAmount >= 0 ? '+' : ''}${values.pointsAmount} 分，原因：${values.reason}。此操作将记入审计日志，是否继续？`;
    }

    Modal.confirm({
      title: confirmTitle,
      content: confirmContent,
      okText: '确认执行',
      cancelText: '取消',
      okType: 'danger',
      async onOk() {
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
  },
});
</script>

<template>
  <Modal title="客户调整">
    <div class="mb-4 text-sm opacity-70">{{ customerInfo }}</div>
    <Form />
  </Modal>
</template>
