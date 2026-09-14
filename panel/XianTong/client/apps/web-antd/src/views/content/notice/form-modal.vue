<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createNotice, updateNotice } from '#/api/content';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

const title = computed(() =>
  mode.value === 'create' ? '新建公告' : '编辑公告',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Textarea',
      fieldName: 'content',
      label: '公告内容',
      rules: 'required',
      // 后端列宽 varchar(500)，前端先卡一道长度，避免超长文本被静默截断
      componentProps: {
        rows: 3,
        maxlength: 500,
        showCount: true,
        placeholder: '如：本周末口腔义诊活动，欢迎预约参加',
      },
    },
    {
      component: 'Select',
      fieldName: 'linkType',
      label: '跳转类型',
      defaultValue: 'none',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '不跳转', value: 'none' },
          { label: '小程序页面', value: 'page' },
          { label: '外部网页', value: 'webview' },
        ],
      },
    },
    {
      component: 'Input',
      fieldName: 'linkTarget',
      label: '跳转目标',
      help: '小程序页面填 pages 路径；外部网页必须是 https 地址，且域名需在公众平台配置',
      componentProps: { placeholder: '跳转目标' },
      dependencies: {
        if: (values) => values.linkType !== 'none',
        rules: (values) => (values.linkType === 'none' ? null : 'required'),
        triggerFields: ['linkType'],
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'sortOrder',
      label: '排序',
      defaultValue: 0,
      help: '数值越小越靠前',
      componentProps: { min: 0, precision: 0, style: 'width: 100%' },
    },
    {
      component: 'DatePicker',
      fieldName: 'startAt',
      label: '生效日期',
      help: '留空表示不限',
      componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
    },
    {
      component: 'DatePicker',
      fieldName: 'endAt',
      label: '失效日期',
      help: '留空表示不限，填写后当天 23:59:59 前仍生效',
      componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
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

      if (mode.value === 'edit' && data?.row) {
        editingId.value = data.row.id;
        formApi.setValues({
          ...data.row,
          // 后端返回 "YYYY-MM-DD HH:mm:ss"，DatePicker 仅需日期部分
          startAt: data.row.startAt?.slice(0, 10),
          endAt: data.row.endAt?.slice(0, 10),
        });
      } else {
        formApi.resetForm();
      }
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    modalApi.setState({ confirmLoading: true });
    try {
      if (mode.value === 'create') {
        await createNotice(values);
        message.success('创建成功');
      } else {
        await updateNotice(editingId.value, values);
        message.success('更新成功');
      }
      emit('success');
      modalApi.close();
    } finally {
      modalApi.setState({ confirmLoading: false });
    }
  },
});
</script>

<template>
  <Modal :title="title">
    <Form />
  </Modal>
</template>
