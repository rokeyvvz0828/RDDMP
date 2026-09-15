<script setup lang="ts">
import { ref, watch } from 'vue'
import type { DetailSection, PhysicalSubsystem } from '../types'
import UiUserIdentity from '../../../components/ui/UiUserIdentity.vue'
import UiStatusTag from '../../../components/ui/UiStatusTag.vue'
import SubsystemParticipants from './SubsystemParticipants.vue'
import SubsystemDeploymentUnitsPanel from './SubsystemDeploymentUnitsPanel.vue'
import SubsystemDeliveryUnitsPanel from './SubsystemDeliveryUnitsPanel.vue'

type DetailTab = 'information' | 'participants' | 'deployment-units' | 'delivery-units'

const props = withDefaults(defineProps<{
  modelValue: boolean
  loading?: boolean
  title: string
  code?: string
  sections: DetailSection[]
  subsystem?: PhysicalSubsystem | null
}>(), { loading: false, code: '', subsystem: null })

const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()

const activeTab = ref<DetailTab>('information')
const participants = ref<InstanceType<typeof SubsystemParticipants> | null>(null)

// 每次打开都回到信息页签；销毁重建保证跨系统不残留上一个系统的页签状态。
watch(() => props.modelValue, value => { if (value) activeTab.value = 'information' })

/** 遮罩、右上角和 Esc 关闭前，先让参与人员页签完成未保存确认；保存进行中不允许关闭。 */
async function beforeClose(done: () => void) {
  const canLeave = participants.value ? await participants.value.confirmLeave() : true
  if (canLeave) done()
}
</script>

<template>
  <el-drawer
    :model-value="modelValue"
    :title="title"
    size="min(760px, calc(100vw - 24px))"
    destroy-on-close
    :before-close="beforeClose"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-loading="loading" class="architecture-detail-body">
      <div class="architecture-detail-heading">
        <strong>{{ title }}</strong>
        <span v-if="code">系统编号：{{ code }}</span>
      </div>
      <el-tabs v-model="activeTab" class="architecture-detail-tabs">
        <el-tab-pane label="信息" name="information">
          <section v-for="section in sections" :key="section.title" class="architecture-detail-section">
            <h4>{{ section.title }}</h4>
            <el-descriptions class="architecture-detail-descriptions" :column="2" border>
              <el-descriptions-item
                v-for="item in section.items"
                :key="item.label"
                :label="item.label"
                :span="item.wide ? 2 : 1"
              >
                <UiStatusTag v-if="item.tag" :value="item.tag.value" :labels="item.tag.labels" :tone="item.tag.tone" indicator />
                <UiUserIdentity v-else-if="item.userId" :user-id="item.userId" :fallback-name="item.value" variant="standard" />
                <span v-else :class="item.tone ? `is-${item.tone}` : ''">{{ item.value || '—' }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </section>
        </el-tab-pane>
        <el-tab-pane label="参与人员" name="participants" lazy>
          <SubsystemParticipants ref="participants" :subsystem="subsystem" @saved="emit('saved')" />
        </el-tab-pane>
        <el-tab-pane label="部署单元" name="deployment-units" lazy>
          <SubsystemDeploymentUnitsPanel :subsystem="subsystem" />
        </el-tab-pane>
        <el-tab-pane label="交付单元" name="delivery-units" lazy>
          <SubsystemDeliveryUnitsPanel :subsystem="subsystem" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>
