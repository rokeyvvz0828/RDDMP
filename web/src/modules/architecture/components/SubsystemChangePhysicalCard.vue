<script setup lang="ts">
import { Delete } from '@element-plus/icons-vue'
import type {
  LogicalSubsystemOption,
  OrganizationOption,
  ParameterOption,
  PhysicalDraftInput,
  UserOption
} from '../types'

const props = withDefaults(defineProps<{
  organizations: OrganizationOption[]
  users: UserOption[]
  logicalSubsystems: LogicalSubsystemOption[]
  runtimes: ParameterOption[]
  levels: ParameterOption[]
  frameworks: ParameterOption[]
  title?: string
  numberLabel?: string
  showLogicalTarget?: boolean
  removable?: boolean
  readonly?: boolean
}>(), {
  title: '物理子系统',
  numberLabel: '待生成',
  showLogicalTarget: false,
  removable: false,
  readonly: false
})

const model = defineModel<PhysicalDraftInput>({ required: true })
const emit = defineEmits<{ remove: [] }>()

function teamChanged(value: number | null) {
  const team = props.organizations.find(item => item.id === value)
  model.value.responsibleTeamNameSnapshot = team?.pathLabel || team?.name || ''
}
</script>

<template>
  <article class="architecture-change-physical-card">
    <header class="architecture-change-card__header">
      <div>
        <span class="architecture-change-card__eyebrow">PHYSICAL SUBSYSTEM · {{ model.lineNo }}</span>
        <h3>{{ title }}</h3>
        <small>系统编号：{{ numberLabel }}</small>
      </div>
      <el-button v-if="removable && !readonly" type="danger" text @click="emit('remove')">
        <el-icon><Delete /></el-icon>移除
      </el-button>
    </header>

    <div class="architecture-form-grid">
      <el-form-item v-if="showLogicalTarget" label="所属逻辑子系统" required>
        <el-select v-model="model.targetLogicalSubsystemId" :disabled="readonly" filterable placeholder="选择已启用逻辑子系统">
          <el-option
            v-for="item in logicalSubsystems"
            :key="item.id"
            :label="`${item.name}（${item.code}）`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="系统简称" required>
        <el-input v-model="model.shortName" :disabled="readonly" maxlength="100" />
      </el-form-item>
      <el-form-item label="系统名称" required :class="{ 'is-wide': !showLogicalTarget }">
        <el-input v-model="model.name" :disabled="readonly" maxlength="200" />
      </el-form-item>
      <el-form-item label="英文名称">
        <el-input v-model="model.englishName" :disabled="readonly" maxlength="200" />
      </el-form-item>
      <el-form-item label="所属事业群">
        <el-input v-model="model.businessGroupName" :disabled="readonly" maxlength="100" />
      </el-form-item>
      <el-form-item label="负责团队" required>
        <el-select
          v-model="model.responsibleTeamOrgId"
          :disabled="readonly"
          filterable
          placeholder="选择负责团队"
          @change="teamChanged"
        >
          <el-option v-for="item in organizations" :key="item.id" :label="item.pathLabel" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="负责人">
        <el-select v-model="model.ownerUserId" :disabled="readonly" filterable clearable placeholder="可选">
          <el-option v-for="item in users" :key="item.id" :label="`${item.displayName}（${item.username}）`" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="系统运行时间">
        <el-select v-model="model.runtimeCode" :disabled="readonly" clearable>
          <el-option v-for="item in runtimes" :key="item.code" :label="item.label" :value="item.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="系统级别">
        <el-select v-model="model.systemLevelCode" :disabled="readonly" clearable>
          <el-option v-for="item in levels" :key="item.code" :label="item.label" :value="item.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="开发平台框架">
        <el-select v-model="model.developmentFrameworkCode" :disabled="readonly" clearable>
          <el-option v-for="item in frameworks" :key="item.code" :label="item.label" :value="item.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="系统描述" class="is-wide">
        <el-input v-model="model.description" :disabled="readonly" type="textarea" :rows="3" maxlength="2000" show-word-limit />
      </el-form-item>
      <el-form-item label="备注" class="is-wide">
        <el-input v-model="model.remark" :disabled="readonly" type="textarea" :rows="2" maxlength="1000" show-word-limit />
      </el-form-item>
    </div>
  </article>
</template>
