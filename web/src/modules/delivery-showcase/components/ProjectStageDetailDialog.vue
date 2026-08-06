<script setup lang="ts">
import { ref, watch } from 'vue'
import type { DeliveryProject } from '../types'

const props = defineProps<{ modelValue: boolean; project: DeliveryProject | null }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const active = ref(0)
watch(() => props.modelValue, open => { if (open && props.project) active.value = Math.max(0, ['需求', '研发', '测试', '迁移', '投产'].indexOf(props.project.stage)) })
</script>

<template>
  <el-dialog :model-value="modelValue" :title="project ? `${project.code} · 分步交付详情` : '分步交付详情'" width="min(980px, 96vw)" top="5vh" class="delivery-step-detail-dialog" @update:model-value="emit('update:modelValue', $event)">
    <template v-if="project"><el-steps :active="active" finish-status="success" align-center><el-step v-for="item in project.milestones" :key="item.id" :title="item.stage" :description="item.status" /></el-steps><div class="delivery-step-detail-body"><aside><button v-for="(item, index) in project.milestones" :key="item.id" type="button" :class="{ active: active === index }" @click="active = index"><span>{{ index + 1 }}</span><div><strong>{{ item.name }}</strong><small>{{ item.start }} 至 {{ item.end }}</small></div></button></aside><section><span class="panel-kicker">{{ project.milestones[active].stage }}阶段</span><h3>{{ project.milestones[active].name }}</h3><el-descriptions :column="2" border><el-descriptions-item label="阶段负责人">{{ project.milestones[active].owner }}</el-descriptions-item><el-descriptions-item label="阶段状态">{{ project.milestones[active].status }}</el-descriptions-item><el-descriptions-item label="计划开始">{{ project.milestones[active].start }}</el-descriptions-item><el-descriptions-item label="计划完成">{{ project.milestones[active].end }}</el-descriptions-item><el-descriptions-item label="阶段进度" :span="2"><el-progress :percentage="project.milestones[active].progress" /></el-descriptions-item></el-descriptions><div class="delivery-checklist"><h4>阶段检查项</h4><el-checkbox :model-value="project.milestones[active].progress > 20" disabled>交付物已上传并完成版本标记</el-checkbox><el-checkbox :model-value="project.milestones[active].progress > 55" disabled>质量门禁检查已通过</el-checkbox><el-checkbox :model-value="project.milestones[active].progress === 100" disabled>阶段负责人已确认完成</el-checkbox></div></section></div></template>
    <template #footer><el-button @click="emit('update:modelValue', false)">关闭</el-button><el-button :disabled="active === 0" @click="active--">上一步</el-button><el-button type="primary" :disabled="!project || active === project.milestones.length - 1" @click="active++">下一步</el-button></template>
  </el-dialog>
</template>
