<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  createMessageTemplate,
  updateMessageTemplate,
} from '#/api/messageTemplate';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);
// 内置模板：templateKey 锁定不可改，只可编辑标题/内容/微信模板ID/状态
const builtIn = ref(false);

const title = computed(() =>
  mode.value === 'create' ? '新建消息模板' : '编辑消息模板',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'templateKey',
      label: '业务标识',
      rules: 'required',
      componentProps: {
        placeholder: '如：appointment_remind_evening',
      },
    },
    {
      component: 'Input',
      fieldName: 'templateId',
      label: '微信模板ID',
      componentProps: {
        placeholder: '小程序后台申请的订阅消息模板ID，可后补',
      },
    },
    {
      component: 'Input',
      fieldName: 'title',
      label: '模板标题',
      rules: 'required',
      componentProps: { placeholder: '如：预约就诊提醒' },
    },
    {
      component: 'Input',
      fieldName: 'triggerEvent',
      label: '触发事件',
      componentProps: {
        placeholder: '如：就诊前一天 18:00 / 当天 09:00',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'content',
      label: '模板内容',
      rules: 'required',
      componentProps: {
        rows: 4,
        placeholder: '支持变量占位，如：{name}，您预约的{date} {time}就诊请按时到店',
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
      const data = modalApi.getData<{ mode: string; row?: any }>();
      mode.value = (data?.mode as any) || 'create';
      builtIn.value = false;
      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        builtIn.value = !!data.row.builtIn;
        formApi.setValues({
          templateKey: data.row.templateKey,
          templateId: data.row.templateId,
          title: data.row.title,
          triggerEvent: data.row.triggerEvent,
          content: data.row.content,
          status: data.row.status,
        });
        // 内置模板的业务标识不允许修改
        formApi.updateSchema([
          {
            fieldName: 'templateKey',
            componentProps: { disabled: builtIn.value },
          },
        ]);
      } else {
        editingId.value = 0;
        formApi.resetForm();
        formApi.updateSchema([
          { fieldName: 'templateKey', componentProps: { disabled: false } },
        ]);
      }
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    if (mode.value === 'create') {
      await createMessageTemplate(values as any);
      message.success('创建成功');
    } else {
      await updateMessageTemplate(editingId.value, values as any);
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
