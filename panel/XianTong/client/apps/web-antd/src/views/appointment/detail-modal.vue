<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import {
  getAppointmentDetail,
  updateAppointmentStatus,
  updateMedicalRecord,
} from '#/api/appointment';

const emit = defineEmits<{ success: [] }>();

const appointmentId = ref<number>(0);
const info = ref<any>({});
const consumption = ref<any>({});

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Select',
      fieldName: 'status',
      label: '预约状态',
      componentProps: {
        style: 'width: 100%',
        options: [
          { label: '待确认', value: 'pending' },
          { label: '已确认', value: 'confirmed' },
          { label: '已到店', value: 'arrived' },
          { label: '已完成', value: 'completed' },
          { label: '已取消', value: 'cancelled' },
          { label: '爽约', value: 'no_show' },
        ],
      },
    },
    {
      component: 'Input',
      fieldName: 'cancelReason',
      label: '取消/爽约原因',
      componentProps: { placeholder: '选填，取消或爽约时填写' },
      dependencies: {
        if(values) {
          return values.status === 'cancelled' || values.status === 'no_show';
        },
        triggerFields: ['status'],
      },
    },
    {
      component: 'Input',
      fieldName: 'remark',
      label: '备注',
      componentProps: { placeholder: '选填' },
    },
    {
      component: 'Textarea',
      fieldName: 'diagnosis',
      label: '诊断',
      componentProps: { rows: 2, placeholder: '病历诊断' },
    },
    {
      component: 'Textarea',
      fieldName: 'treatmentPlan',
      label: '治疗方案',
      componentProps: { rows: 2, placeholder: '治疗方案' },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (isOpen) {
      const data = modalApi.getData<{ id: number }>();
      appointmentId.value = data?.id ?? 0;
      const res = await getAppointmentDetail(appointmentId.value);
      info.value = res.appointment || {};
      consumption.value = res.consumption || {};
      formApi.setValues({
        status: info.value.status,
        cancelReason: info.value.cancelReason,
        remark: info.value.remark,
        diagnosis: info.value.diagnosis,
        treatmentPlan: info.value.treatmentPlan,
      });
    }
  },
  async onConfirm() {
    const values = await formApi.getValues();
    await updateAppointmentStatus(appointmentId.value, {
      status: values.status,
      remark: values.remark,
      cancelReason: values.cancelReason,
    });
    await updateMedicalRecord(appointmentId.value, {
      diagnosis: values.diagnosis,
      treatmentPlan: values.treatmentPlan,
    });
    message.success('保存成功');
    emit('success');
    modalApi.close();
  },
});
</script>

<template>
  <Modal title="预约详情">
    <div class="mb-4 grid grid-cols-2 gap-x-4 gap-y-2 text-sm">
      <div>
        <span class="opacity-60">客户：</span>
        {{ info.userName || '-' }}
      </div>
      <div>
        <span class="opacity-60">电话：</span>
        {{ info.userPhone || '-' }}
      </div>
      <div>
        <span class="opacity-60">门诊：</span>
        {{ info.clinicName || '-' }}
      </div>
      <div>
        <span class="opacity-60">科室：</span>
        {{ info.departmentName || '-' }}
      </div>
      <div>
        <span class="opacity-60">医生：</span>
        {{ info.doctorName || '-' }}
      </div>
      <div>
        <span class="opacity-60">日期：</span>
        {{ info.appointmentDate || '-' }}
      </div>
      <div>
        <span class="opacity-60">时间：</span>
        {{ info.startTime }}{{ info.endTime ? ` - ${info.endTime}` : '' }}
      </div>
      <div>
        <span class="opacity-60">到店：</span>
        {{ info.arrivedAt || '-' }}
      </div>
      <div>
        <span class="opacity-60">完成：</span>
        {{ info.completedAt || '-' }}
      </div>
      <div>
        <span class="opacity-60">消费：</span>
        ￥{{ consumption.totalAmount ?? 0 }}（{{ consumption.recordCount ?? 0 }}
        笔）
      </div>
    </div>
    <Form />
  </Modal>
</template>
