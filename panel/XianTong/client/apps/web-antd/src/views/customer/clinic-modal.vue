<script lang="ts" setup>
import { ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { Alert, message, Modal as AntModal } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getClinics } from '#/api/clinic';
import { changeCustomerClinic } from '#/api/customer';

const emit = defineEmits<{ success: [] }>();

const customerId = ref(0);
const customerInfo = ref('');
const currentClinicId = ref<number>();
const currentClinicName = ref('未设置');
const clinics = ref<Array<{ id: number; name: string; status?: number }>>([]);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'ApiSelect',
      fieldName: 'clinicId',
      label: '目标归属门店',
      rules: 'selectRequired',
      help: '调整后该客户的新预约、消费及客户数据将按新门店权限展示。',
      componentProps: {
        afterFetch: (data: any[]) =>
          (data ?? [])
            .filter((item) => item.status === undefined || item.status === 1)
            .map((item) => ({
              ...item,
              disabled: item.id === currentClinicId.value,
            })),
        api: getClinics,
        labelField: 'name',
        placeholder: '请选择新的归属门店',
        style: 'width: 100%',
        valueField: 'id',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'reason',
      label: '调整原因',
      rules: 'required',
      componentProps: {
        maxlength: 500,
        placeholder: '必填，将记入管理端审计日志',
        rows: 3,
        showCount: true,
      },
    },
  ],
});

const [Modal, modalApi] = useVbenModal({
  async onOpenChange(isOpen) {
    if (!isOpen) return;
    const data = modalApi.getData<{ row?: any }>();
    const row = data?.row;
    customerId.value = row?.id ?? 0;
    customerInfo.value = row
      ? `${row.realName || row.nickname || '客户'}（ID:${row.id}）`
      : '';
    currentClinicId.value = row?.primaryClinicId;
    formApi.resetForm();

    clinics.value = await getClinics();
    currentClinicName.value =
      clinics.value.find((item) => item.id === currentClinicId.value)?.name ??
      '未设置';
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;

    const values = await formApi.getValues();
    const targetClinic = clinics.value.find(
      (item) => item.id === Number(values.clinicId),
    );
    AntModal.confirm({
      cancelText: '取消',
      content: `即将把客户归属门店由「${currentClinicName.value}」调整为「${
        targetClinic?.name || values.clinicId
      }」。原因：${values.reason}。此操作将写入审计日志，是否继续？`,
      okText: '确认调整',
      okType: 'danger',
      title: '确认调整客户归属门店',
      async onOk() {
        await changeCustomerClinic(customerId.value, {
          clinicId: Number(values.clinicId),
          reason: String(values.reason).trim(),
        });
        message.success('客户归属门店已调整');
        emit('success');
        modalApi.close();
      },
    });
  },
});
</script>

<template>
  <Modal title="调整归属门店" class="w-[560px]">
    <div class="mb-3 text-sm opacity-70">{{ customerInfo }}</div>
    <Alert
      class="mb-4"
      :message="`当前归属门店：${currentClinicName}`"
      show-icon
      type="info"
    />
    <Form />
  </Modal>
</template>
