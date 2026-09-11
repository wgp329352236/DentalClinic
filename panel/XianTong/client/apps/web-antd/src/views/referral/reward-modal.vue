<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { grantReferralReward } from '#/api/referral';

const emit = defineEmits<{ success: [] }>();

const relationId = ref<number>(0);
const relationInfo = ref('');

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Select',
      fieldName: 'rewardType',
      label: '奖励类型',
      rules: 'selectRequired',
      defaultValue: 'points',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '积分', value: 'points' },
          { label: '余额', value: 'balance' },
        ],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'rewardValue',
      label: '奖励值',
      rules: 'required',
      componentProps: {
        min: 0.01,
        precision: 2,
        style: 'width: 100%',
        placeholder: '积分填整数，余额填金额',
      },
    },
    {
      component: 'Select',
      fieldName: 'stage',
      label: '发放阶段',
      defaultValue: 'manual',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '手动发放', value: 'manual' },
          { label: '注册', value: 'registration' },
          { label: '首次消费', value: 'first_consumption' },
          { label: '阶梯奖励', value: 'tier' },
        ],
      },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ row?: any }>();
      const row = data?.row;
      relationId.value = row?.id ?? 0;
      relationInfo.value = row
        ? `推荐人：${row.referrerName}（ID:${row.referrerId}） → 被推荐人：${row.refereeName}（ID:${row.refereeId}）`
        : '';
      formApi.resetForm();
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    await grantReferralReward({ ...values, relationId: relationId.value });
    message.success('发放成功');
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal title="发放推荐奖励">
    <div class="mb-4 text-sm opacity-70">{{ relationInfo }}</div>
    <Form />
  </Modal>
</template>
