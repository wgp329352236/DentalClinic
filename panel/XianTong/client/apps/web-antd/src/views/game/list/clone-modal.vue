<script lang="ts" setup>
/**
 * BUG-15.1.C9 fix: onConfirm 调用 formApi.validate() 校验
 */
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { cloneGame } from '#/api/game';

const emit = defineEmits<{ success: [] }>();
const sourceCode = ref('');

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'newCode',
      label: '新游戏代码',
      rules: 'required',
      componentProps: { placeholder: '只允许字母、数字和下划线' },
    },
    { component: 'Input', fieldName: 'newName', label: '新游戏名称', rules: 'required' },
  ],
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ sourceCode: string; sourceName: string }>();
      sourceCode.value = data?.sourceCode || '';
      formApi.resetForm();
      formApi.setValues({ newName: `${data?.sourceName || ''} (副本)` });
    }
  },
  async onConfirm() {
    // BUG-15.1.C9 fix: 先校验表单
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    await cloneGame(sourceCode.value, values);
    message.success('克隆成功');
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal :title="`克隆游戏 - ${sourceCode}`">
    <Form />
  </Modal>
</template>
