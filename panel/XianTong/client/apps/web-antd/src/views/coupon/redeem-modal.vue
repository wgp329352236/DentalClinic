<script lang="ts" setup>
import { useVbenModal } from '@vben/common-ui';

import { Alert, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { scanRedeemCoupon } from '#/api/coupon';

const emit = defineEmits<{ success: [] }>();

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Textarea',
      fieldName: 'qrData',
      label: '优惠券二维码',
      rules: 'required',
      componentProps: {
        autofocus: true,
        placeholder: '请扫码，或粘贴券码数据：couponCode|userId|couponId',
        rows: 3,
        style: 'width: 100%',
      },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) formApi.resetForm();
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;

    const { qrData } = await formApi.getValues();
    const result = await scanRedeemCoupon({ qrData: String(qrData).trim() });
    message.success(
      `核销成功：${result.couponName}，优惠金额 ¥${result.faceValue}`,
    );
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal title="扫码核销优惠券" class="w-[520px]">
    <Alert
      class="mb-4"
      message="使用扫码枪扫描客户小程序券码，或将扫码结果粘贴到下方输入框。"
      show-icon
      type="info"
    />
    <Form />
  </Modal>
</template>
