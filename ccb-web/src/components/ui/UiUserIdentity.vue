<script setup lang="ts">
import { computed } from 'vue'
import { UserFilled } from '@element-plus/icons-vue'
import type { UserProfile } from '../../types/system'

const props = withDefaults(defineProps<{ user?: UserProfile | null; size?: number | string; showName?: boolean; showProfile?: boolean }>(), {
  user: null,
  size: 32,
  showName: true,
  showProfile: true
})
const initial = computed(() => (props.user?.displayName || props.user?.username || '用').slice(0, 1))
const roles = computed(() => props.user?.roles?.join('、') || '暂无角色')
</script>

<template>
  <el-popover v-if="showProfile" placement="top-start" trigger="hover" :show-after="120" width="250px">
    <template #reference>
      <span class="ui-user-identity">
        <el-avatar :size="size" :src="user?.avatarUrl || undefined"><span v-if="user">{{ initial }}</span><el-icon v-else><UserFilled /></el-icon></el-avatar>
        <strong v-if="showName">{{ user?.displayName || '未登录用户' }}</strong>
      </span>
    </template>
    <div class="ui-user-profile">
      <div class="ui-user-profile__head">
        <el-avatar :size="44" :src="user?.avatarUrl || undefined">{{ initial }}</el-avatar>
        <div><strong>{{ user?.displayName || '未登录用户' }}</strong><span>{{ user?.username || '暂无账号' }}</span></div>
      </div>
      <dl><dt>所属组织</dt><dd>{{ user?.orgName || '未分配组织' }}</dd><dt>角色</dt><dd>{{ roles }}</dd></dl>
    </div>
  </el-popover>
  <span v-else class="ui-user-identity">
    <el-avatar :size="size" :src="user?.avatarUrl || undefined"><span v-if="user">{{ initial }}</span><el-icon v-else><UserFilled /></el-icon></el-avatar>
    <strong v-if="showName">{{ user?.displayName || '未登录用户' }}</strong>
  </span>
</template>