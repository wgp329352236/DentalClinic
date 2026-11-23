<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getDoctors } from '#/api/doctor';
import { createSchedule, updateSchedule } from '#/api/schedule';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref(0);
const title = computed(() =>
  mode.value === 'create' ? '新建排班' : '编辑排班',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'ApiSelect',
      fieldName: 'doctorId',
      label: '医生',
      rules: 'selectRequired',
      componentProps: {
        api: getDoctors,
        labelField: 'name',
        valueField: 'id',
        style: 'width: 100%',
        placeholder: '请选择医生',
      },
    },
    {
      component: 'Select',
      fieldName: 'dayOfWeek',
      label: '星期',
      rules: 'selectRequired',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '周一', value: 1 },
          { label: '周二', value: 2 },
          { label: '周三', value: 3 },
          { label: '周四', value: 4 },
          { label: '周五', value: 5 },
          { label: '周六', value: 6 },
          { label: '周日', value: 7 },
        ],
      },
    },
    {
      component: 'TimePicker',
      fieldName: 'startTime',
      label: '开始时间',
      rules: 'required',
      componentProps: {
        format: 'HH:mm',
        placeholder: '请选择开始时间',
        style: 'width: 100%',
        valueFormat: 'HH:mm',
      },
    },
    {
      component: 'TimePicker',
      fieldName: 'endTime',
      label: '结束时间',
      rules: 'required',
      componentProps: {
        format: 'HH:mm',
        placeholder: '请选择结束时间',
        style: 'width: 100%',
        valueFormat: 'HH:mm',
      },
    },
    {
      component: 'InputNumber',
      fieldName: 'maxAppointments',
      label: '最大预约数',
      defaultValue: 1,
      rules: 'required',
      componentProps: { min: 1, precision: 0, style: 'width: 100%' },
    },
    {
      component: 'InputNumber',
      fieldName: 'slotDuration',
      label: '时段时长(分钟)',
      defaultValue: 30,
      rules: 'required',
      componentProps: { min: 15, max: 120, precision: 0, style: 'width: 100%' },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ doctorId?: number; row?: any }>();
      const row = data?.row;
      mode.value = row ? 'edit' : 'create';
      editingId.value = row?.id ?? 0;
      formApi.resetForm();
      if (row) {
        formApi.setValues({
          dayOfWeek: row.dayOfWeek,
          doctorId: row.doctorId,
          endTime: row.endTime,
          maxAppointments: row.maxAppointments,
          slotDuration: row.slotDuration,
          startTime: row.startTime,
        });
      } else if (data?.doctorId) {
        formApi.setValues({ doctorId: data.doctorId });
      }
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    if (mode.value === 'create') {
      await createSchedule(values);
      message.success('创建成功');
    } else {
      await updateSchedule(editingId.value, values);
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
