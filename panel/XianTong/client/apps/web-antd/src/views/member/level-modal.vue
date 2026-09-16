<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createMemberLevel,
  getMemberLevels,
  updateMemberLevel,
  type MemberLevel,
} from '#/api/member';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);
const existingLevels = ref<MemberLevel[]>([]);

const title = computed(() =>
  mode.value === 'create' ? '新建会员等级' : '编辑会员等级',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'InputNumber',
      fieldName: 'levelCode',
      label: '等级编码',
      rules: 'required',
      componentProps: {
        min: 1,
        max: 9999,
        precision: 0,
        style: 'width: 100%',
        placeholder: '1-9999 的整数',
      },
    },
    {
      component: 'Input',
      fieldName: 'levelName',
      label: '等级名称',
      rules: 'required',
      componentProps: {
        allowClear: true,
        maxLength: 20,
        showCount: true,
        placeholder: '如：普通会员 / 银卡 / 金卡',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'minConsumption',
      label: '升级门槛(元)',
      rules: 'required',
      componentProps: {
        min: 0,
        max: 9999999,
        precision: 2,
        style: 'width: 100%',
        placeholder: '0-9999999',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'discountRate',
      label: '折扣比例',
      rules: 'required',
      defaultValue: 1,
      componentProps: {
        min: 0.01,
        max: 1,
        step: 0.01,
        precision: 2,
        style: 'width: 100%',
        placeholder: '0.95 表示 95 折，1 表示不打折',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'benefits',
      label: '权益说明',
      componentProps: {
        rows: 3,
        maxLength: 500,
        showCount: true,
        placeholder: '如：每年免费口腔检查 1 次、洗牙 8 折',
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
        existingLevels.value = (await getMemberLevels()) || [];
      } catch {
        existingLevels.value = [];
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
    const levelCode = Number(values.levelCode);
    const levelName = (values.levelName ?? '').toString().trim();
    const minConsumption = Number(values.minConsumption);
    const discountRate = Number(values.discountRate);
    const benefits = (values.benefits ?? '').toString().trim();
    if (!Number.isInteger(levelCode) || levelCode < 1 || levelCode > 9999) {
      message.error('等级编码必须为 1-9999 的整数');
      return;
    }
    if (levelName.length === 0 || levelName.length > 20) {
      message.error('等级名称长度需在 1-20 个字符之间');
      return;
    }
    if (
      !Number.isFinite(minConsumption) ||
      minConsumption < 0 ||
      minConsumption > 9999999
    ) {
      message.error('升级门槛需在 0 ~ 9999999 之间');
      return;
    }
    if (
      !Number.isFinite(discountRate) ||
      discountRate < 0.01 ||
      discountRate > 1
    ) {
      message.error('折扣比例需在 0.01 ~ 1 之间');
      return;
    }
    if (benefits.length > 500) {
      message.error('权益说明不能超过 500 个字符');
      return;
    }
    const duplicated = existingLevels.value.some(
      (l) => l.id !== editingId.value && l.levelCode === levelCode,
    );
    if (duplicated) {
      message.error(`已存在等级编码 ${levelCode}，请调整编码`);
      return;
    }
    const payload = {
      levelCode,
      levelName,
      minConsumption,
      discountRate,
      benefits,
      status: Number(values.status ?? 1),
    };
    if (mode.value === 'create') {
      await createMemberLevel(payload as any);
      message.success('创建成功');
    } else {
      await updateMemberLevel(editingId.value, payload as any);
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
