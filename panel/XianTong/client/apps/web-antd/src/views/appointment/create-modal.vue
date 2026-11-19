<script lang="ts" setup>
import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createAppointmentByAdmin } from '#/api/appointment';
import { getClinics } from '#/api/clinic';
import { getCustomers } from '#/api/customer';
import { getDepartments } from '#/api/department';
import { getDoctors } from '#/api/doctor';

const emit = defineEmits<{ success: [] }>();

// 09:00-20:30 每 30 分钟一个候选项；是否可约以后端排班/容量校验为准
const timeOptions = Array.from({ length: 24 }, (_, index) => {
  const totalMinutes = 9 * 60 + index * 30;
  const hour = String(Math.floor(totalMinutes / 60)).padStart(2, '0');
  const minute = String(totalMinutes % 60).padStart(2, '0');
  return { label: `${hour}:${minute}`, value: `${hour}:${minute}` };
});

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'ApiSelect',
      fieldName: 'clinicId',
      label: '就诊门诊',
      rules: 'selectRequired',
      componentProps: {
        allowClear: true,
        api: getClinics,
        labelField: 'name',
        placeholder: '请选择门诊',
        style: 'width: 100%',
        valueField: 'id',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'userId',
      label: '客户',
      rules: 'selectRequired',
      componentProps: {
        allowClear: true,
        api: getCustomers,
        labelFn: (item: any) =>
          `${item.realName || item.nickname || '客户'}（${item.phone || '无手机号'} · ID:${item.id}）`,
        optionFilterProp: 'label',
        placeholder: '搜索姓名或手机号选择客户',
        resultField: 'list',
        showSearch: true,
        style: 'width: 100%',
        valueField: 'id',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'doctorId',
      label: '医生',
      rules: 'selectRequired',
      componentProps: {
        allowClear: true,
        api: getDoctors,
        labelField: 'name',
        optionFilterProp: 'label',
        placeholder: '请选择医生',
        showSearch: true,
        style: 'width: 100%',
        valueField: 'id',
      },
    },
    {
      component: 'ApiSelect',
      fieldName: 'departmentId',
      label: '科室/项目',
      componentProps: {
        allowClear: true,
        api: getDepartments,
        immediate: false,
        labelField: 'name',
        placeholder: '选填，先选门诊后加载',
        style: 'width: 100%',
        valueField: 'id',
      },
      dependencies: {
        componentProps: (values: any) => ({
          params: { clinicId: values.clinicId },
        }),
        triggerFields: ['clinicId'],
      },
    },
    {
      component: 'DatePicker',
      fieldName: 'appointmentDate',
      label: '就诊日期',
      rules: 'required',
      componentProps: {
        style: 'width: 100%',
        valueFormat: 'YYYY-MM-DD',
        placeholder: '选择就诊日期',
      },
    },
    {
      component: 'Select',
      fieldName: 'startTime',
      label: '开始时间',
      rules: 'selectRequired',
      componentProps: {
        showSearch: true,
        optionFilterProp: 'label',
        options: timeOptions,
        placeholder: '具体时段以医生排班为准',
        style: 'width: 100%',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'remark',
      label: '备注',
      componentProps: { rows: 2, placeholder: '选填，如客户特殊需求' },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      formApi.resetForm();
      // 从看板带入门诊与日期，减少重复选择
      const preset = modalApi.getData<{
        clinicId?: number;
        date?: string;
      }>();
      await formApi.setValues({
        clinicId: preset?.clinicId,
        appointmentDate: preset?.date,
      });
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    modalApi.setState({ confirmLoading: true });
    try {
      await createAppointmentByAdmin({
        userId: values.userId,
        clinicId: values.clinicId,
        doctorId: values.doctorId,
        departmentId: values.departmentId ?? undefined,
        appointmentDate: values.appointmentDate,
        startTime: values.startTime,
        remark: values.remark || undefined,
      });
      message.success('代客预约成功');
      emit('success');
      modalApi.close();
    } finally {
      modalApi.setState({ confirmLoading: false });
    }
  },
});
</script>

<template>
  <Modal title="代客预约">
    <Form />
  </Modal>
</template>
