<script lang="ts" setup>
import { useVbenModal } from '@vben/common-ui';

import { Alert, message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getCouponTemplates, grantCoupons } from '#/api/coupon';
import { getCustomers } from '#/api/customer';

const emit = defineEmits<{ success: [] }>();

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'ApiSelect',
      fieldName: 'templateId',
      label: '优惠券模板',
      rules: 'selectRequired',
      componentProps: {
        afterFetch: (res: any) =>
          (res ?? [])
            .filter((item: any) => item.status === 1)
            .map((item: any) => {
              const remaining = Math.max(
                0,
                Number(item.totalQuantity) - Number(item.issuedQuantity),
              );
              return {
                ...item,
                disabled: remaining <= 0,
                remaining,
              };
            }),
        api: getCouponTemplates,
        labelFn: (item: any) =>
          `${item.name}（剩余 ${item.remaining ?? '?'} 张）`,
        placeholder: '请选择要发放的优惠券',
        style: 'width: 100%',
        valueField: 'id',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'userIds',
      label: '目标客户',
      rules: 'selectRequired',
      help: '支持多选；同一客户重复选择只会发放一张。',
      componentProps: {
        afterFetch: (res: any) => res?.list ?? [],
        api: getCustomers,
        allowClear: true,
        labelFn: (item: any) =>
          [item.realName || item.nickname || `客户 ${item.id}`, item.phone]
            .filter(Boolean)
            .join(' · '),
        mode: 'multiple',
        placeholder: '搜索并选择客户',
        showSearch: true,
        style: 'width: 100%',
        valueField: 'id',
      },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (!isOpen) return;

    const data = modalApi.getData<{ row?: { id?: number } }>();
    formApi.resetForm();
    if (data?.row?.id) {
      formApi.setValues({ templateId: data.row.id });
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;

    const values = await formApi.getValues();
    const userIds = (values.userIds ?? []).map((id: number | string) =>
      Number(id),
    );
    if (userIds.length === 0) {
      message.warning('请至少选择一个客户');
      return;
    }

    const result = await grantCoupons({
      templateId: Number(values.templateId),
      userIds,
    });

    if (result.failedUsers?.length) {
      message.warning(
        `成功发放 ${result.successCount} 张，未发放客户：${result.failedUsers.join('、')}`,
      );
    } else {
      message.success(`成功发放 ${result.successCount} 张优惠券`);
    }
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal title="定向发放优惠券" class="w-[560px]">
    <Alert
      class="mb-4"
      message="发放后客户可在小程序优惠券列表中查看，并由前台扫码核销。"
      show-icon
      type="info"
    />
    <Form />
  </Modal>
</template>
