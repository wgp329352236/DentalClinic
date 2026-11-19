<script lang="ts" setup>
import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';

import { message } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { getClinics } from '#/api/clinic';
import { createDepartment, updateDepartment } from '#/api/department';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

const title = computed(() =>
  mode.value === 'create' ? '新建科室' : '编辑科室',
);

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'ApiSelect',
      fieldName: 'clinicId',
      label: '所属门诊',
      rules: 'selectRequired',
      componentProps: {
        api: getClinics,
        labelField: 'name',
        valueField: 'id',
        style: 'width: 100%',
        placeholder: '请选择门诊',
      },
    },
    {
      component: 'Input',
      fieldName: 'name',
      label: '科室名称',
      rules: 'required',
      componentProps: { placeholder: '如：正畸科' },
    },
    {
      component: 'Textarea',
      fieldName: 'description',
      label: '描述',
      componentProps: { rows: 2, placeholder: '科室简介（选填）' },
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
        // 列表返回字段为 deptName，而创建/更新请求字段为 name，需映射
        formApi.setValues({
          clinicId: data.row.clinicId,
          name: data.row.deptName,
          description: data.row.description,
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
    if (mode.value === 'create') {
      await createDepartment(values);
      message.success('创建成功');
    } else {
      await updateDepartment(editingId.value, values);
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
