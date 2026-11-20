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

type ViewMode = 'day' | 'week';

// ---------------- 状态色 ----------------
const statusStyleMap: Record<string, { bg: string; border: string; text: string }> = {
  pending: { bg: '#fff7e6', border: '#ffa940', text: '待确认' },
  confirmed: { bg: '#e6f4ff', border: '#69b1ff', text: '已确认' },
  arrived: { bg: '#e6fffb', border: '#36cfc9', text: '已到店' },
  completed: { bg: '#f6ffed', border: '#73d13d', text: '已完成' },
  cancelled: { bg: '#fafafa', border: '#d9d9d9', text: '已取消' },
  no_show: { bg: '#fff2f0', border: '#ff7875', text: '爽约' },
};

const weekdayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

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
const viewMode = ref<ViewMode>('day');
const loading = ref(false);
const appointments = ref<any[]>([]);

const clinicOptions = computed(() =>
  clinics.value.map((c) => ({ label: c.name, value: c.id })),
);

const currentClinic = computed(
  () => clinics.value.find((c) => c.id === selectedClinicId.value) || null,
);

const weekStart = computed(() => startOfWeek(parseLocalDate(selectedDate.value)));
const weekEnd = computed(() => addDays(weekStart.value, 6));
const weekRangeText = computed(
  () => `${formatMonthDay(formatDate(weekStart.value))} - ${formatMonthDay(formatDate(weekEnd.value))}`,
);

const weekGroups = computed(() =>
  Array.from({ length: 7 }, (_, index) => {
    const date = formatDate(addDays(weekStart.value, index));
    const dayAppointments = appointments.value
      .filter((appointment) => appointment.appointmentDate === date)
      .sort(compareAppointments);
    return {
      date,
      weekday: weekdayNames[parseLocalDate(date).getDay()],
      dayLabel: formatMonthDay(date),
      isToday: date === formatDate(new Date()),
      appointments: dayAppointments,
    };
  }),
);

const statusDistribution = computed(() =>
  Object.entries(statusStyleMap).map(([status, meta]) => ({
    status,
    ...meta,
    count: appointments.value.filter((appointment) => appointment.status === status).length,
  })),
);

const periodSummary = computed(() => ({
  total: appointments.value.length,
  pending: appointments.value.filter((appointment) => appointment.status === 'pending').length,
  pendingCancel: appointments.value.filter(
    (appointment) => appointment.cancelRequestStatus === 'pending',
  ).length,
  completed: appointments.value.filter((appointment) => appointment.status === 'completed').length,
}));

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

// ---------------- 数据加载（单店、单日或整周一次查询） ----------------
async function loadBoard() {
  if (!selectedClinicId.value) {
    appointments.value = [];
    return;
  }
  loading.value = true;
  try {
    // 不分页：后端返回该门店指定日期范围的全量预约，前端自行分组。
    const dateFrom = viewMode.value === 'week'
      ? formatDate(weekStart.value)
      : selectedDate.value;
    const dateTo = viewMode.value === 'week'
      ? formatDate(weekEnd.value)
      : selectedDate.value;
    const res = await getAppointments({
      clinicId: selectedClinicId.value,
      dateFrom,
      dateTo,
    });
    appointments.value = res.list || [];
  } finally {
    loading.value = false;
  }
}

watch([selectedClinicId, selectedDate, viewMode], loadBoard);

// ---------------- 交互 ----------------
function shiftDate(delta: number) {
  const step = viewMode.value === 'week' ? delta * 7 : delta;
  selectedDate.value = formatDate(addDays(parseLocalDate(selectedDate.value), step));
}

function goToday() {
  selectedDate.value = formatDate(new Date());
}

function setViewMode(mode: ViewMode) {
  viewMode.value = mode;
}

function onCreate(date = selectedDate.value) {
  if (!selectedClinicId.value) return;
  createModalApi
    .setData({ clinicId: selectedClinicId.value, date })
    .open();
}

function onCardClick(appt: any) {
  detailModalApi.setData({ id: appt.id }).open();
}

function cardStyle(appt: any) {
  const style = statusStyleMap[appt.status] || {
    bg: '#f5f5f5',
    border: '#d9d9d9',
    text: appt.status,
  };
  const hasPendingCancel = appt.cancelRequestStatus === 'pending';
  return {
    backgroundColor: style.bg,
    borderLeft: `3px solid ${style.border}`,
    outline: hasPendingCancel ? '2px solid #fa8c16' : undefined,
    outlineOffset: '-2px',
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

function compareAppointments(left: any, right: any): number {
  return `${left.appointmentDate} ${left.startTime}`.localeCompare(
    `${right.appointmentDate} ${right.startTime}`,
  );
}

function parseLocalDate(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year || 1970, (month || 1) - 1, day || 1);
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  next.setDate(next.getDate() + days);
  return next;
}

function startOfWeek(date: Date): Date {
  const mondayOffset = (date.getDay() + 6) % 7;
  return addDays(date, -mondayOffset);
}

function formatMonthDay(date: string): string {
  const parsed = parseLocalDate(date);
  return `${parsed.getMonth() + 1}月${parsed.getDate()}日`;
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
    <div class="mb-3">
      <div class="flex flex-wrap items-center gap-2">
        <Select
          v-model:value="selectedClinicId"
          show-search
          :options="clinicOptions"
          option-filter-prop="label"
          placeholder="选择门诊"
          style="width: 200px"
        />
        <div class="view-mode-switch">
          <Button
            :type="viewMode === 'day' ? 'primary' : 'default'"
            @click="setViewMode('day')"
          >单日</Button>
          <Button
            :type="viewMode === 'week' ? 'primary' : 'default'"
            @click="setViewMode('week')"
          >本周</Button>
        </div>
        <Button @click="shiftDate(-1)">
          {{ viewMode === 'day' ? '前一天' : '上一周' }}
        </Button>
        <DatePicker
          v-model:value="selectedDate"
          :allow-clear="false"
          value-format="YYYY-MM-DD"
        />
        <Button @click="shiftDate(1)">
          {{ viewMode === 'day' ? '后一天' : '下一周' }}
        </Button>
        <Button @click="goToday">回到今天</Button>
        <Button type="primary" :disabled="!selectedClinicId" @click="onCreate()">
          <Plus class="size-5" />
          代客预约
        </Button>
        <div v-if="viewMode === 'day'" class="ml-auto flex items-center gap-2">
          <Tag
            v-for="(meta, key) in statusStyleMap"
            :key="key"
            :color="meta.border"
          >
            {{ meta.text }}
          </Tag>
        </div>
      </div>
      <div class="board-context">
        <span class="font-medium">{{ currentClinic?.name || '请选择门诊' }}</span>
        <span v-if="viewMode === 'day'"> · {{ selectedDate }}</span>
        <span v-else> · 本周 {{ weekRangeText }}（周一至周日）</span>
      </div>
    </div>

    <Spin :spinning="loading">
      <div
        v-if="!selectedClinicId"
        class="flex h-64 items-center justify-center text-sm opacity-60"
      >
        暂无可访问的门诊数据，请联系管理员授权门店
      </div>

      <template v-else>
        <!-- 本周汇总：预约量、待确认、待审取消申请与状态分布 -->
        <div v-if="viewMode === 'week'" class="week-summary">
          <div class="summary-stat">
            <div class="summary-value">{{ periodSummary.total }}</div>
            <div class="summary-label">本周预约量</div>
          </div>
          <div class="summary-stat warning">
            <div class="summary-value">{{ periodSummary.pending }}</div>
            <div class="summary-label">待确认</div>
          </div>
          <div class="summary-stat danger">
            <div class="summary-value">{{ periodSummary.pendingCancel }}</div>
            <div class="summary-label">待审取消申请</div>
          </div>
          <div class="summary-stat success">
            <div class="summary-value">{{ periodSummary.completed }}</div>
            <div class="summary-label">已完成</div>
          </div>
          <div class="status-summary">
            <div class="mb-2 text-xs opacity-60">状态分布</div>
            <div class="flex flex-wrap gap-2">
              <Tag
                v-for="item in statusDistribution"
                :key="item.status"
                :color="item.count > 0 ? item.border : undefined"
              >
                {{ item.text }} {{ item.count }}
              </Tag>
            </div>
          </div>
        </div>

        <!-- 本周按日期分组，不与单日医生时段矩阵混用 -->
        <div v-if="viewMode === 'week'" class="week-board">
          <section
            v-for="day in weekGroups"
            :key="day.date"
            class="week-day"
            :class="{ 'is-today': day.isToday, 'is-empty': day.appointments.length === 0 }"
          >
            <div class="week-day-head">
              <div class="flex items-center gap-2">
                <span class="week-day-name">{{ day.weekday }}</span>
                <span class="week-day-date">{{ day.dayLabel }}</span>
                <Tag v-if="day.isToday" color="blue">今天</Tag>
              </div>
              <div class="flex items-center gap-2">
                <span class="text-xs opacity-60">{{ day.appointments.length }} 个预约</span>
                <Button type="link" size="small" @click.stop="onCreate(day.date)">
                  代客预约
                </Button>
              </div>
            </div>

            <div v-if="day.appointments.length === 0" class="week-empty">暂无预约</div>
            <div
              v-for="appt in day.appointments"
              :key="appt.id"
              class="week-appointment"
              :style="cardStyle(appt)"
              @click="onCardClick(appt)"
            >
              <div class="week-time">{{ appt.startTime }}-{{ appt.endTime }}</div>
              <div class="week-info">
                <div class="week-customer">
                  {{ appt.userName || `客户#${appt.userId}` }}
                  <span v-if="appt.userPhone" class="week-phone">{{ appt.userPhone }}</span>
                </div>
                <div class="week-meta">
                  {{ appt.doctorName || '未分配医生' }}
                  <span v-if="appt.departmentName"> · {{ appt.departmentName }}</span>
                  <span v-if="appt.remark"> · {{ appt.remark }}</span>
                </div>
              </div>
              <div class="week-tags">
                <Tag :color="statusStyleMap[appt.status]?.border">
                  {{ statusStyleMap[appt.status]?.text || appt.status }}
                </Tag>
                <Tag v-if="appt.cancelRequestStatus === 'pending'" color="orange">
                  待审取消申请
                </Tag>
              </div>
            </div>
          </section>
        </div>

        <!-- 今日沿用医生 × 时段矩阵 -->
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
                :style="cardStyle(appt)"
                @click="onCardClick(appt)"
              >
                <div class="truncate text-xs font-medium">
                  {{ appt.userName || `客户#${appt.userId}` }}
                </div>
                <div class="truncate text-[11px] opacity-70">
                  {{ statusStyleMap[appt.status]?.text || appt.status }}
                  {{ appt.endTime ? `· ${appt.endTime}` : '' }}
                </div>
                <div
                  v-if="appt.cancelRequestStatus === 'pending'"
                  class="mt-0.5 inline-flex w-fit rounded bg-orange-100 px-1 text-[10px] text-orange-700"
                >
                  待审取消申请
                </div>
              </div>
            </div>
          </template>
        </div>
      </div>
      </template>
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

.view-mode-switch {
  display: inline-flex;
}

.view-mode-switch :deep(.ant-btn:first-child) {
  border-radius: 6px 0 0 6px;
}

.view-mode-switch :deep(.ant-btn:last-child) {
  margin-left: -1px;
  border-radius: 0 6px 6px 0;
}

.board-context {
  margin-top: 8px;
  color: #8c8c8c;
  font-size: 12px;
}

.week-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(110px, 1fr)) minmax(260px, 1.7fr);
  gap: 12px;
  margin-bottom: 12px;
}

.summary-stat,
.status-summary {
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
  padding: 12px 14px;
}

.summary-stat {
  border-top: 3px solid #69b1ff;
}

.summary-stat.warning {
  border-top-color: #ffa940;
}

.summary-stat.danger {
  border-top-color: #ff7875;
}

.summary-stat.success {
  border-top-color: #73d13d;
}

.summary-value {
  font-size: 24px;
  font-weight: 600;
  line-height: 1.25;
}

.summary-label {
  margin-top: 2px;
  color: #8c8c8c;
  font-size: 12px;
}

.week-board {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.week-day {
  overflow: hidden;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
  background: #fff;
}

.week-day.is-today {
  border-color: #91caff;
  box-shadow: 0 0 0 2px rgb(24 144 255 / 8%);
}

.week-day-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 14px;
  background: #fafafa;
}

.week-day.is-today .week-day-head {
  background: #e6f4ff;
}

.week-day-name {
  font-weight: 600;
}

.week-day-date {
  color: #595959;
}

.week-empty {
  padding: 14px;
  color: #bfbfbf;
  font-size: 12px;
}

.week-appointment {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin: 8px;
  padding: 9px 12px;
  cursor: pointer;
  border-radius: 6px;
  transition: box-shadow 0.15s;
}

.week-appointment:hover {
  box-shadow: 0 2px 8px rgb(0 0 0 / 12%);
}

.week-time {
  font-size: 13px;
  font-weight: 600;
}

.week-customer {
  font-size: 13px;
  font-weight: 500;
}

.week-phone {
  margin-left: 8px;
  color: #8c8c8c;
  font-size: 12px;
  font-weight: 400;
}

.week-meta {
  overflow: hidden;
  margin-top: 2px;
  color: #8c8c8c;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.week-tags {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}

@media (max-width: 1200px) {
  .week-summary {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .status-summary {
    grid-column: 1 / -1;
  }
}
</style>
