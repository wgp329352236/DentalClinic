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
let initialValues: Record<string, any> = {};

const cancelStatusText: Record<string, string> = {
  approved: '已同意',
  pending: '待审批',
  rejected: '已驳回',
};

function sameValue(left: any, right: any) {
  return (left ?? '') === (right ?? '');
}

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
      initialValues = {
        status: info.value.status,
        cancelReason: info.value.cancelReason,
        remark: info.value.remark,
        diagnosis: info.value.diagnosis,
        treatmentPlan: info.value.treatmentPlan,
      };
      formApi.setValues(initialValues);
    }
  },
  async onConfirm() {
    const values = await formApi.getValues();
    const statusChanged =
      !sameValue(values.status, initialValues.status) ||
      !sameValue(values.remark, initialValues.remark) ||
      !sameValue(values.cancelReason, initialValues.cancelReason);
    const medicalRecordChanged =
      !sameValue(values.diagnosis, initialValues.diagnosis) ||
      !sameValue(values.treatmentPlan, initialValues.treatmentPlan);

    if (!statusChanged && !medicalRecordChanged) {
      message.info('未检测到修改');
      return;
    }
    if (statusChanged) {
      await updateAppointmentStatus(appointmentId.value, {
        status: values.status,
        remark: values.remark,
        cancelReason: values.cancelReason,
      });
    }
    if (medicalRecordChanged) {
      await updateMedicalRecord(appointmentId.value, {
        diagnosis: values.diagnosis,
        treatmentPlan: values.treatmentPlan,
      });
    }
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
    <div
      v-if="info.cancelRequestStatus"
      class="mb-4 rounded border border-orange-200 bg-orange-50 p-3 text-sm"
    >
      <div class="mb-1 font-medium">
        取消申请：{{ cancelStatusText[info.cancelRequestStatus] || info.cancelRequestStatus }}
      </div>
      <div>
        <span class="opacity-60">申请原因：</span>
        {{ info.cancelRequestReason || '-' }}
      </div>
      <div>
        <span class="opacity-60">申请时间：</span>
        {{ info.cancelRequestedAt || '-' }}
      </div>
      <div v-if="info.cancelReviewedAt">
        <span class="opacity-60">审核人：</span>
        {{ info.cancelReviewedByName || `管理员#${info.cancelReviewedBy || '-'}` }}
        <span class="ml-3 opacity-60">审核时间：</span>
        {{ info.cancelReviewedAt }}
      </div>
    </div>
    <Form />
  </Modal>
</template>
