<script setup lang="ts">
import { computed } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import type { PersonProfile } from '../../types/system'

const props = withDefaults(defineProps<{
  profile?: PersonProfile | null
  state?: 'idle' | 'loading' | 'ready' | 'missing' | 'error'
  fallbackName?: string | null
  fallbackAccount?: string | null
  fallbackAvatarUrl?: string | null
}>(), {
  profile: null,
  state: 'idle',
  fallbackName: null,
  fallbackAccount: null,
  fallbackAvatarUrl: null
})
const emit = defineEmits<{ retry: [] }>()

const displayName = computed(() => props.profile?.displayName || props.fallbackName || '未知人员')
const account = computed(() => props.profile?.username || props.fallbackAccount || '暂无账号')
const avatarUrl = computed(() => props.profile?.avatarUrl || props.fallbackAvatarUrl || undefined)
const initial = computed(() => displayName.value.slice(0, 1))
const roles = computed(() => props.profile?.roles ?? [])
// 有档案就渲染档案，避免加载中或重试时清空已经可见的内容。
const loaded = computed(() => !!props.profile)
const stopped = computed(() => !!props.profile && props.profile.status !== 1)
</script>

<template>
  <div class="ui-person-card">
    <div class="ui-person-card__head">
      <el-avatar :size="44" :src="avatarUrl" aria-hidden="true">
        <span>{{ initial }}</span>
      </el-avatar>
      <div class="ui-person-card__who">
        <strong>{{ displayName }}</strong>
        <span>{{ account }}</span>
      </div>
      <span v-if="loaded" class="ui-person-card__badge" :class="{ 'is-off': stopped }">
        {{ stopped ? '已停用' : '启用' }}
      </span>
    </div>

    <p v-if="!loaded && state === 'loading'" class="ui-person-card__status">
      <el-skeleton :rows="2" animated />
    </p>
    <p v-else-if="!loaded && state === 'missing'" class="ui-person-card__status">未找到该人员信息</p>
    <div v-else-if="!loaded && state === 'error'" class="ui-person-card__status">
      <span>人员信息加载失败</span>
      <el-button link type="primary" size="small" @click="emit('retry')">重试</el-button>
    </div>

    <template v-if="loaded">
      <section class="ui-person-card__section">
        <h4>联系方式</h4>
        <dl><dt>电话</dt><dd class="ui-person-card__value">{{ profile?.mobilePhone || '—' }}</dd></dl>
      </section>
      <section class="ui-person-card__section">
        <h4>组织归属</h4>
        <dl><dt>团队</dt><dd class="ui-person-card__value">{{ profile?.orgName || '—' }}</dd></dl>
      </section>
      <section class="ui-person-card__section">
        <h4>角色</h4>
        <div v-if="roles.length" class="ui-person-card__chips">
          <span v-for="role in roles" :key="role.id" class="ui-person-card__chip">{{ role.name }}</span>
        </div>
        <div v-else class="ui-person-card__chips">
          <span class="ui-person-card__chip ui-person-card__chip--plain">暂无角色</span>
        </div>
      </section>
    </template>

    <p v-if="!loaded && !state" class="ui-person-card__status">
      <el-icon><UserFilled /></el-icon>
      <span>暂无更多人员信息</span>
    </p>
  </div>
</template>

<style scoped>
.ui-person-card { display: grid; gap: 0; min-width: 0; color: var(--text); }
.ui-person-card__head { display: flex; align-items: center; gap: 11px; min-width: 0; padding-bottom: 11px; border-bottom: 1px solid var(--line); }
.ui-person-card__head .el-avatar { flex: 0 0 auto; color: var(--brand-strong); background: color-mix(in srgb, var(--brand) 18%, var(--panel-bg)); }
.ui-person-card__who { min-width: 0; display: grid; gap: 3px; }
.ui-person-card__who strong { font-size: 14px; overflow-wrap: anywhere; }
.ui-person-card__who span { color: var(--muted); font-size: 12px; overflow-wrap: anywhere; }
.ui-person-card__badge { flex: 0 0 auto; margin-left: auto; font-size: 11px; padding: 2px 8px; border-radius: 999px; color: var(--success); border: 1px solid color-mix(in srgb, var(--success) 40%, transparent); background: color-mix(in srgb, var(--success) 10%, transparent); }
.ui-person-card__badge.is-off { color: var(--muted); border-color: var(--line); background: transparent; }
.ui-person-card__section { padding-top: 10px; }
.ui-person-card__section + .ui-person-card__section { border-top: 1px dashed var(--line); margin-top: 10px; }
.ui-person-card__section h4 { margin: 0 0 7px; font-size: 11px; font-weight: 650; letter-spacing: .6px; color: var(--muted); }
.ui-person-card__section dl { display: grid; grid-template-columns: 46px minmax(0, 1fr); gap: 7px 10px; margin: 0; font-size: 12px; }
.ui-person-card__section dt { color: var(--muted); }
.ui-person-card__section dd { margin: 0; min-width: 0; }
/* 浮层的目的是看全信息，超长团队名与角色名换行而不省略。 */
.ui-person-card__value { overflow-wrap: anywhere; }
.ui-person-card__chips { display: flex; flex-wrap: wrap; gap: 6px; }
.ui-person-card__chip { max-width: 100%; font-size: 11.5px; line-height: 1.7; padding: 3px 9px; border-radius: 999px; overflow-wrap: anywhere; color: var(--brand-strong); border: 1px solid color-mix(in srgb, var(--brand) 30%, var(--line)); background: color-mix(in srgb, var(--brand) 9%, var(--panel-bg)); }
.ui-person-card__chip--plain { color: var(--muted); border-style: dashed; background: transparent; }
.ui-person-card__status { display: flex; align-items: center; gap: 8px; margin: 11px 0 0; color: var(--muted); font-size: 12px; }
.ui-person-card__status :deep(.el-skeleton) { width: 100%; }
</style>
