<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createRechargeTier,
  getRechargeTiers,
  updateRechargeTier,
  type RechargeTier,
} from '#/api/recharge';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);
const existingTiers = ref<RechargeTier[]>([]);

const title = computed(() =>
  mode.value === 'create' ? '新建充值档位' : '编辑充值档位',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'tierName',
      label: '档位名称',
      rules: 'required',
      componentProps: {
        allowClear: true,
        maxLength: 30,
        showCount: true,
        placeholder: '如：充1000送100',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'minAmount',
      label: '充值门槛(元)',
      rules: 'required',
      componentProps: {
        min: 0.01,
        precision: 2,
        style: 'width: 100%',
        placeholder: '达到该金额才适用此档位',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'bonusRate',
      label: '赠送比例',
      rules: 'required',
      defaultValue: 0,
      componentProps: {
        min: 0,
        max: 1,
        step: 0.01,
        precision: 4,
        style: 'width: 100%',
        placeholder: '0.10 表示赠送 10%',
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
      try {
        existingTiers.value = (await getRechargeTiers()) || [];
      } catch {
        existingTiers.value = [];
      }
      const data = modalApi.getData<{ mode: string; row?: any }>();
      mode.value = (data?.mode as any) || 'create';
      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        formApi.setValues({ ...data.row });
      } else {
        editingId.value = 0;
        formApi.resetForm();
      }
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    const tierName = (values.tierName ?? '').toString().trim();
    const minAmount = Number(values.minAmount);
    const bonusRate = Number(values.bonusRate);
    const sortOrder = Number(values.sortOrder ?? 0);
    if (tierName.length === 0 || tierName.length > 30) {
      message.error('档位名称长度需在 1-30 个字符之间');
      return;
    }
    if (!Number.isFinite(minAmount) || minAmount <= 0 || minAmount > 999999) {
      message.error('充值门槛需为大于 0 且不超过 999999 的数值');
      return;
    }
    if (!Number.isFinite(bonusRate) || bonusRate < 0 || bonusRate > 1) {
      message.error('赠送比例需在 0 ~ 1 之间');
      return;
    }
    if (!Number.isInteger(sortOrder) || sortOrder < 0 || sortOrder > 9999) {
      message.error('排序需为 0 ~ 9999 之间的整数');
      return;
    }
    // 以分为单位比较，规避浮点误差
    const minAmountCents = Math.round(minAmount * 100);
    const duplicated = existingTiers.value.some(
      (t) =>
        t.id !== editingId.value &&
        Math.round(Number(t.minAmount) * 100) === minAmountCents,
    );
    if (duplicated) {
      message.error(`已存在充值门槛为 ${minAmount} 元的档位，请调整门槛金额`);
      return;
    }
    const payload = {
      tierName,
      minAmount,
      bonusRate,
      sortOrder,
      status: Number(values.status ?? 1),
    };
    if (mode.value === 'create') {
      await createRechargeTier(payload as any);
      message.success('创建成功');
    } else {
      await updateRechargeTier(editingId.value, payload as any);
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
