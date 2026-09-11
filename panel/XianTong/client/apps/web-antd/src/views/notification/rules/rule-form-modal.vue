<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createNotificationRule, updateNotificationRule } from '#/api/config';

const emit = defineEmits<{ success: [] }>();
const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);
const title = computed(() =>
  mode.value === 'create' ? '新建通知规则' : '编辑通知规则',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'ruleName',
      label: '规则名称',
      rules: 'required',
    },
    {
      component: 'Select',
      fieldName: 'conditionType',
      label: '条件类型',
      rules: 'required',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '任务失败次数', value: 'task_failure' },
          { label: 'DLQ 溢出', value: 'dlq_overflow' },
          { label: '错过调度', value: 'missed_schedule' },
        ],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'threshold',
      label: '阈值',
      rules: 'required',
      componentProps: { min: 1 },
    },
    {
      component: 'Input',
      fieldName: 'webhookUrl',
      label: 'Webhook URL',
      rules: 'required',
      componentProps: { placeholder: 'https://open.feishu.cn/...' },
    },
    {
      component: 'Switch',
      fieldName: 'isEnabled',
      label: '启用',
      defaultValue: true,
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ mode: string; row?: any }>();
      mode.value = (data?.mode as any) || 'create';
      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        formApi.setValues(data.row);
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
      await createNotificationRule(values);
      message.success('创建成功');
    } else {
      await updateNotificationRule(editingId.value, values);
      message.success('更新成功');
    }
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal :title="title"><Form /></Modal>
</template>
