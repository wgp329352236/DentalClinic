<script lang="ts" setup>
/**
 * BUG-15.1.C2 fix: 添加 supportAreas 字段 (Tag Input)
 * BUG-15.1.C3 fix: 添加 pullChannels 字段 (Multi-Select)
 * BUG-15.1.C4 fix: 添加 pushChannels 字段 (Multi-Select)
 * BUG-15.1.C5 fix: type 使用 Bitmask Select 替代 InputNumber
 * BUG-15.1.C6 fix: payType 使用 Bitmask Select 替代 InputNumber
 * BUG-15.1.C7 fix: openTime 使用 DatePicker 替代 InputNumber
 * BUG-15.1.C8 fix: closeTime 使用 DatePicker 替代 InputNumber
 */
import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createGame } from '#/api/game';

const emit = defineEmits<{ success: [] }>();

// 渠道选项
const CHANNEL_OPTIONS = [
  { label: 'Google', value: 'google' },
  { label: 'Meta', value: 'meta' },
  { label: 'TikTok', value: 'tiktok' },
  { label: 'Apple', value: 'apple' },
  { label: 'Adjust', value: 'adjust' },
  { label: 'Applovin', value: 'applovin' },
  { label: 'Moloco', value: 'moloco' },
  { label: 'Reddit', value: 'reddit' },
  { label: 'VK', value: 'vk' },
  { label: 'Yandex', value: 'yandex' },
  { label: 'Unity', value: 'unity' },
  { label: 'Mintegral', value: 'mintegral' },
  { label: 'Remerge', value: 'remerge' },
  { label: 'IronSource', value: 'ironsource' },
  { label: 'Mistplay', value: 'mistplay' },
];

// BUG-15.1.C5 fix: type bitmask 选项
const TYPE_OPTIONS = [
  { label: '测试', value: 1 },
  { label: '正式', value: 2 },
  { label: '预约', value: 4 },
  { label: 'MINI', value: 8 },
];

// BUG-15.1.C6 fix: payType bitmask 选项
const PAY_TYPE_OPTIONS = [
  { label: 'SIMPLE', value: 1 },
  { label: 'IAP', value: 2 },
  { label: 'IAA', value: 4 },
  { label: 'MINI', value: 8 },
  { label: 'PRE', value: 16 },
];

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'code',
      label: '游戏代码',
      rules: 'required',
      componentProps: { placeholder: '如 Nova（只允许字母数字下划线）' },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '游戏名称',
      rules: 'required',
    },
    {
      component: 'Input',
      fieldName: 'area',
      label: '地区',
      rules: 'required',
      componentProps: { placeholder: '如 Global, JP, SEA' },
    },
    // BUG-15.1.C5 fix: type 使用 CheckboxGroup (bitmask 多选)
    {
      component: 'CheckboxGroup',
      fieldName: 'typeFlags',
      label: '类型',
      rules: 'required',
      componentProps: { options: TYPE_OPTIONS },
    },
    // BUG-15.1.C6 fix: payType 使用 CheckboxGroup (bitmask 多选)
    {
      component: 'CheckboxGroup',
      fieldName: 'payTypeFlags',
      label: '付费类型',
      rules: 'required',
      componentProps: { options: PAY_TYPE_OPTIONS },
    },
    // BUG-15.1.C7 fix: openTime 使用 DatePicker
    {
      component: 'DatePicker',
      fieldName: 'openTimeDate',
      label: '开服时间',
      rules: 'required',
      componentProps: { format: 'YYYY-MM-DD', valueFormat: 'YYYYMMDD' },
    },
    // BUG-15.1.C8 fix: closeTime 使用 DatePicker
    {
      component: 'DatePicker',
      fieldName: 'closeTimeDate',
      label: '关服时间',
      componentProps: { format: 'YYYY-MM-DD', valueFormat: 'YYYYMMDD', placeholder: '留空表示运营中' },
    },
    // BUG-15.1.C2 fix: supportAreas 字段
    {
      component: 'Select',
      fieldName: 'supportAreas',
      label: '支持地区',
      componentProps: { mode: 'tags', placeholder: '输入地区后回车添加，如 US, JP, KR', style: 'width: 100%' },
    },
    // BUG-15.1.C3 fix: pullChannels 字段
    {
      component: 'Select',
      fieldName: 'pullChannels',
      label: '拉取渠道',
      componentProps: { mode: 'multiple', options: CHANNEL_OPTIONS, placeholder: '选择拉取渠道', style: 'width: 100%' },
    },
    // BUG-15.1.C4 fix: pushChannels 字段
    {
      component: 'Select',
      fieldName: 'pushChannels',
      label: '推送渠道',
      componentProps: { mode: 'multiple', options: CHANNEL_OPTIONS, placeholder: '选择推送渠道', style: 'width: 100%' },
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
    const values = await formApi.getValues();

    // 将 bitmask 多选转为整数
    const type = (values.typeFlags || []).reduce((acc: number, v: number) => acc | v, 0);
    const payType = (values.payTypeFlags || []).reduce((acc: number, v: number) => acc | v, 0);
    // 将日期字符串转为整数
    const openTime = values.openTimeDate ? Number(values.openTimeDate) : 0;
    const closeTime = values.closeTimeDate ? Number(values.closeTimeDate) : 0;

    await createGame({
      code: values.code,
      name: values.name,
      area: values.area,
      type,
      payType,
      openTime,
      closeTime,
      supportAreas: values.supportAreas || [],
      pullChannels: values.pullChannels || [],
      pushChannels: values.pushChannels || [],
    });
    message.success('创建成功');
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal title="新建游戏">
    <Form />
  </Modal>
</template>
