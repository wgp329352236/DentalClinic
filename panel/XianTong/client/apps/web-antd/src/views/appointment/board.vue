<script lang="ts" setup>
import { computed, ref, watch } from 'vue';

import { Page, useVbenModal } from '@vben/common-ui';
import { Plus } from '@vben/icons';

import { Button, DatePicker, Select, Spin, Tag } from 'ant-design-vue';

import { getAppointments } from '#/api/appointment';
import { getClinics } from '#/api/clinic';
import { getDoctors } from '#/api/doctor';

import AppointmentCreateModal from './create-modal.vue';
import AppointmentDetailModal from './detail-modal.vue';

// ---------------- 状态色 ----------------
const statusStyleMap: Record<string, { bg: string; border: string; text: string }> = {
  pending: { bg: '#fff7e6', border: '#ffa940', text: '待确认' },
  confirmed: { bg: '#e6f4ff', border: '#69b1ff', text: '已确认' },
  arrived: { bg: '#e6fffb', border: '#36cfc9', text: '已到店' },
  completed: { bg: '#f6ffed', border: '#73d13d', text: '已完成' },
  cancelled: { bg: '#fafafa', border: '#d9d9d9', text: '已取消' },
  no_show: { bg: '#fff2f0', border: '#ff7875', text: '爽约' },
};

// ---------------- 弹窗 ----------------
const [CreateModal, createModalApi] = useVbenModal({
  connectedComponent: AppointmentCreateModal,
  destroyOnClose: true,
});
const [DetailModal, detailModalApi] = useVbenModal({
  connectedComponent: AppointmentDetailModal,
  destroyOnClose: true,
});

// ---------------- 基础数据 ----------------
const clinics = ref<any[]>([]);
const doctors = ref<any[]>([]);
const selectedClinicId = ref<number | undefined>();
const selectedDate = ref<string>(formatDate(new Date()));
const loading = ref(false);
const appointments = ref<any[]>([]);

const clinicOptions = computed(() =>
  clinics.value.map((c) => ({ label: c.name, value: c.id })),
);

const currentClinic = computed(
  () => clinics.value.find((c) => c.id === selectedClinicId.value) || null,
);

// ---------------- 时间轴 ----------------
/**
 * 时段轴：优先取门诊 openTime/closeTime（30 分钟一格），
 * 并并入当天预约中出现过的开始时间，避免营业时段外的历史预约无处安放。
 */
const timeSlots = computed<string[]>(() => {
  const axis = new Set<string>();
  const clinic = currentClinic.value;
  const open = clinic?.openTime;
  const close = clinic?.closeTime;
  if (open && close && toMinutes(close) > toMinutes(open)) {
    for (let t = toMinutes(open); t < toMinutes(close); t += 30) {
      axis.add(toHHmm(t));
    }
  } else {
    for (let t = 9 * 60; t < 21 * 60; t += 30) {
      axis.add(toHHmm(t));
    }
  }
  for (const appt of appointments.value) {
    if (appt.startTime) axis.add(appt.startTime);
  }
  return [...axis].sort();
});

// 看板列：与门诊关联的医生 ∪ 当天有预约的医生；无关联记录的医生按全部门诊处理
const boardDoctors = computed(() => {
  const clinicId = selectedClinicId.value;
  const related = doctors.value.filter((doctor) => {
    const ids = doctor.clinicIds;
    return !ids || ids.length === 0 || ids.includes(clinicId);
  });
  const bookedIds = new Set(appointments.value.map((a) => a.doctorId));
  const extra = doctors.value.filter(
    (doctor) => bookedIds.has(doctor.id) && !related.some((d) => d.id === doctor.id),
  );
  return [...related, ...extra];
});

// doctorId__HH:mm → 该格预约（同一时段可能多人，maxAppointments > 1）
const cellMap = computed(() => {
  const map = new Map<string, any[]>();
  for (const appt of appointments.value) {
    const key = `${appt.doctorId}__${appt.startTime}`;
    const list = map.get(key) || [];
    list.push(appt);
    map.set(key, list);
  }
  return map;
});

// ---------------- 数据加载（单日单店一次查询） ----------------
async function loadBoard() {
  if (!selectedClinicId.value) {
    appointments.value = [];
    return;
  }
  loading.value = true;
  try {
    // 不分页：不传 page/pageSize，后端返回该日该门店全量预约，前端自行分组
    const res = await getAppointments({
      clinicId: selectedClinicId.value,
      dateFrom: selectedDate.value,
      dateTo: selectedDate.value,
    });
    appointments.value = res.list || [];
  } finally {
    loading.value = false;
  }
}

watch([selectedClinicId, selectedDate], loadBoard);

// ---------------- 交互 ----------------
function shiftDate(delta: number) {
  selectedDate.value = formatDate(
    new Date(
      Number(selectedDate.value.slice(0, 4)),
      Number(selectedDate.value.slice(5, 7)) - 1,
      Number(selectedDate.value.slice(8, 10)) + delta,
    ),
  );
}

function goToday() {
  selectedDate.value = formatDate(new Date());
}

function onCreate() {
  if (!selectedClinicId.value) return;
  createModalApi
    .setData({ clinicId: selectedClinicId.value, date: selectedDate.value })
    .open();
}

function onCardClick(appt: any) {
  detailModalApi.setData({ id: appt.id }).open();
}

function cardStyle(status: string) {
  const style = statusStyleMap[status] || {
    bg: '#f5f5f5',
    border: '#d9d9d9',
    text: status,
  };
  return {
    backgroundColor: style.bg,
    borderLeft: `3px solid ${style.border}`,
  };
}

// ---------------- 工具 ----------------
function toMinutes(hhmm: string): number {
  const parts = hhmm.split(':');
  const hour = Number(parts[0] ?? 0);
  const minute = Number(parts[1] ?? 0);
  return hour * 60 + minute;
}

function toHHmm(total: number): string {
  const hour = String(Math.floor(total / 60)).padStart(2, '0');
  const minute = String(total % 60).padStart(2, '0');
  return `${hour}:${minute}`;
}

function formatDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

// ---------------- 初始化 ----------------
(async () => {
  const [clinicList, doctorList] = await Promise.all([
    getClinics(),
    getDoctors(),
  ]);
  clinics.value = clinicList || [];
  doctors.value = doctorList || [];
  if (clinics.value[0]) {
    selectedClinicId.value = clinics.value[0].id;
  }
})();
</script>

<template>
  <Page auto-content-height>
    <CreateModal @success="loadBoard" />
    <DetailModal @success="loadBoard" />

    <!-- 顶部筛选与操作 -->
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <Select
        v-model:value="selectedClinicId"
        show-search
        :options="clinicOptions"
        option-filter-prop="label"
        placeholder="选择门诊"
        style="width: 200px"
      />
      <Button @click="shiftDate(-1)">前一天</Button>
      <DatePicker
        v-model:value="selectedDate"
        :allow-clear="false"
        value-format="YYYY-MM-DD"
      />
      <Button @click="shiftDate(1)">后一天</Button>
      <Button @click="goToday">今天</Button>
      <Button type="primary" :disabled="!selectedClinicId" @click="onCreate">
        <Plus class="size-5" />
        代客预约
      </Button>
      <div class="ml-auto flex items-center gap-2">
        <Tag
          v-for="(meta, key) in statusStyleMap"
          :key="key"
          :color="meta.border"
        >
          {{ meta.text }}
        </Tag>
      </div>
    </div>

    <Spin :spinning="loading">
      <div
        v-if="!selectedClinicId"
        class="flex h-64 items-center justify-center text-sm opacity-60"
      >
        暂无可访问的门诊数据，请联系管理员授权门店
      </div>
      <div v-else class="board-scroll">
        <div
          class="board-grid"
          :style="{
            gridTemplateColumns: `68px repeat(${boardDoctors.length || 1}, minmax(150px, 1fr))`,
          }"
        >
          <!-- 表头 -->
          <div class="board-corner">时间</div>
          <div v-for="doctor in boardDoctors" :key="`head-${doctor.id}`" class="board-head">
            <div class="font-medium">{{ doctor.name }}</div>
            <div class="text-xs opacity-60">{{ doctor.title || '医生' }}</div>
          </div>

          <!-- 时间轴行 -->
          <template v-for="slot in timeSlots" :key="slot">
            <div class="board-time">{{ slot }}</div>
            <div
              v-for="doctor in boardDoctors"
              :key="`${slot}-${doctor.id}`"
              class="board-cell"
            >
              <div
                v-for="appt in cellMap.get(`${doctor.id}__${slot}`) || []"
                :key="appt.id"
                class="board-card"
                :style="cardStyle(appt.status)"
                @click="onCardClick(appt)"
              >
                <div class="truncate text-xs font-medium">
                  {{ appt.userName || `客户#${appt.userId}` }}
                </div>
                <div class="truncate text-[11px] opacity-70">
                  {{ statusStyleMap[appt.status]?.text || appt.status }}
                  {{ appt.endTime ? `· ${appt.endTime}` : '' }}
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
    </Spin>
  </Page>
</template>

<style scoped>
.board-scroll {
  max-height: calc(100vh - 220px);
  overflow: auto;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.board-grid {
  display: grid;
  grid-auto-rows: 46px;
  min-width: 100%;
}

.board-corner,
.board-head,
.board-time,
.board-cell {
  border-bottom: 1px solid #f5f5f5;
  border-right: 1px solid #f5f5f5;
}

.board-corner,
.board-head {
  position: sticky;
  top: 0;
  z-index: 4;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 4px 8px;
  background: #fafafa;
  font-size: 12px;
}

.board-corner {
  left: 0;
  z-index: 5;
  align-items: center;
}

.board-head {
  z-index: 3;
}

.board-time {
  position: sticky;
  left: 0;
  z-index: 2;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 2px;
  background: #fafafa;
  color: #8c8c8c;
  font-size: 11px;
}

.board-cell {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 2px;
}

.board-card {
  cursor: pointer;
  border-radius: 4px;
  padding: 2px 6px;
  line-height: 1.4;
  transition: box-shadow 0.15s;
}

.board-card:hover {
  box-shadow: 0 2px 6px rgb(0 0 0 / 15%);
}
</style>
