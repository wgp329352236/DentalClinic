<script lang="ts" setup>
import type { UploadProps } from 'ant-design-vue';

import { computed, ref } from 'vue';

import { useVbenModal } from '@vben/common-ui';
import { IconifyIcon } from '@vben/icons';

import { Image, Input, message, Upload } from 'ant-design-vue';

import { useVbenForm } from '#/adapter/form';
import { createActivity, updateActivity } from '#/api/content';
import { uploadImage } from '#/api/upload';

const emit = defineEmits<{ success: [] }>();

const mode = ref<'create' | 'edit'>('create');
const editingId = ref<number>(0);

// 封面与轮播图同理：上传流程产出单个 URL，放进 schema 反而要在提交前拆包
const coverImage = ref<string>('');
const uploading = ref<boolean>(false);

const title = computed(() =>
  mode.value === 'create' ? '新建活动' : '编辑活动',
);

const customRequest: UploadProps['customRequest'] = async (options) => {
  const file = options.file as File;
  if (file.size / 1024 / 1024 > 5) {
    message.error('图片大小不能超过 5MB');
    options.onError?.(new Error('file too large'));
    return;
  }
  uploading.value = true;
  try {
    const result = await uploadImage(file);
    coverImage.value = result.url;
    message.success('上传成功');
    options.onSuccess?.(result);
  } catch (error: any) {
    if (!error?.response) message.error(error?.message || '上传失败');
    options.onError?.(error);
  } finally {
    uploading.value = false;
  }
};

const [Form, formApi] = useVbenForm({
  showDefaultActions: false,
  schema: [
    {
      component: 'Input',
      fieldName: 'title',
      label: '活动名称',
      rules: 'required',
      componentProps: { placeholder: '如：新客首诊免费洁牙' },
    },
    {
      component: 'Textarea',
      fieldName: 'summary',
      label: '活动简介',
      help: '一句话概括，用于详情页标题下方与分享文案，最多 500 字',
      componentProps: {
        rows: 2,
        maxlength: 500,
        showCount: true,
        placeholder: '如：新客到院即享免费洁牙一次，含口腔检查与洁治',
      },
    },
    {
      component: 'Textarea',
      fieldName: 'content',
      label: '活动详情',
      help: '纯文本，支持换行分段；小程序详情页按段落渲染，不支持富文本',
      componentProps: {
        rows: 8,
        maxlength: 5000,
        showCount: true,
        placeholder: '活动规则、适用门店、预约说明等',
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
      label: '开始日期',
      help: '留空表示不限',
      componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
    },
    {
      component: 'DatePicker',
      fieldName: 'endAt',
      label: '结束日期',
      help: '留空表示不限，填写后当天 23:59:59 前仍有效',
      componentProps: { style: 'width: 100%', valueFormat: 'YYYY-MM-DD' },
    },
    {
      component: 'Select',
      fieldName: 'status',
      label: '状态',
      defaultValue: 1,
      help: '停用后小程序首页对应轮播图同步隐藏',
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
    if (!isOpen) return;
    const data = modalApi.getData<{ mode: string; row?: any }>();
    mode.value = (data?.mode as any) || 'create';

    if (mode.value === 'edit' && data?.row) {
      editingId.value = data.row.id;
      coverImage.value = data.row.coverImage || '';
      formApi.setValues({
        ...data.row,
        // 后端返回 "YYYY-MM-DD HH:mm:ss"，DatePicker 仅需日期部分
        startAt: data.row.startAt?.slice(0, 10),
        endAt: data.row.endAt?.slice(0, 10),
      });
    } else {
      editingId.value = 0;
      coverImage.value = '';
      formApi.resetForm();
    }
  },
  async onConfirm() {
    const { valid } = await formApi.validate();
    if (!valid) return;
    const values = await formApi.getValues();
    const payload = { ...values, coverImage: coverImage.value || null };
    modalApi.setState({ confirmLoading: true });
    try {
      if (mode.value === 'create') {
        await createActivity(payload);
        message.success('创建成功');
      } else {
        await updateActivity(editingId.value, payload);
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
    <div class="mb-4">
      <div class="mb-2">活动封面</div>
      <div class="flex items-center gap-4">
        <Upload
          :custom-request="customRequest"
          :show-upload-list="false"
          accept="image/jpeg,image/png,image/gif,image/webp"
          list-type="picture-card"
        >
          <div v-if="uploading">
            <IconifyIcon
              class="animate-spin"
              icon="ant-design:loading-outlined"
            />
            <div class="mt-1 text-xs">上传中</div>
          </div>
          <div v-else>
            <IconifyIcon icon="ant-design:plus-outlined" />
            <div class="mt-1 text-xs">上传封面</div>
          </div>
        </Upload>
        <Image
          v-if="coverImage"
          :src="coverImage"
          :height="72"
          :width="180"
          class="rounded object-cover"
        />
      </div>
      <!-- 选填：留空时小程序详情页用主题色渐变兜底，不阻塞活动创建 -->
      <div class="mt-2 flex items-center gap-2">
        <Input
          v-model:value="coverImage"
          allow-clear
          placeholder="或直接填写封面地址（选填），如 /api/v1/files/xxx.png"
        />
        <span class="text-muted-foreground shrink-0 text-xs">建议 750×420</span>
      </div>
    </div>
    <Form />
  </Modal>
</template>
