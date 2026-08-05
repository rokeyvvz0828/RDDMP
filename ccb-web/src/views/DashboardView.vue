<script setup lang="ts">
import { computed } from 'vue'
import { useAuthStore } from '../stores/auth'
import { Calendar, Connection, Lock, UserFilled } from '@element-plus/icons-vue'

const auth = useAuthStore()
const currentHour = new Date().getHours()
const greeting = computed(() => currentHour < 12 ? '早上好' : currentHour < 18 ? '下午好' : '晚上好')
</script>

<template>
  <div class="dashboard-page">
    <section class="page-intro">
      <div>
        <span class="panel-kicker">工作台 / 01</span>
        <h1>{{ greeting }}，{{ auth.user?.displayName || '管理员' }}</h1>
        <p>这是你的平台工作台，当前系统运行在单租户基础模式。</p>
      </div>
      <div class="status-pill"><span class="status-dot"></span>系统运行正常</div>
    </section>

    <section class="metric-grid">
      <el-card shadow="never" class="metric-card accent-blue">
        <div class="metric-top"><span>组织节点</span><el-icon><Connection /></el-icon></div>
        <strong>01</strong><small>根组织已配置</small>
      </el-card>
      <el-card shadow="never" class="metric-card accent-green">
        <div class="metric-top"><span>可用菜单</span><el-icon><Calendar /></el-icon></div>
        <strong>{{ auth.routes[0]?.children?.length || 0 }}</strong><small>来自动态路由配置</small>
      </el-card>
      <el-card shadow="never" class="metric-card accent-orange">
        <div class="metric-top"><span>角色权限</span><el-icon><Lock /></el-icon></div>
        <strong>{{ auth.user?.permissions?.length || 0 }}</strong><small>后端授权生效</small>
      </el-card>
      <el-card shadow="never" class="metric-card accent-purple">
        <div class="metric-top"><span>当前账号</span><el-icon><UserFilled /></el-icon></div>
        <strong>管理员</strong><small>{{ auth.user?.username }}</small>
      </el-card>
    </section>

    <section class="dashboard-grid">
      <el-card shadow="never" class="surface-card activity-card">
        <template #header><div class="card-heading"><div><span class="panel-kicker">系统状态</span><h3>平台状态</h3></div><span class="muted">实时</span></div></template>
        <div class="signal-row"><span class="signal-icon blue"><Connection /></span><div><strong>认证服务</strong><p>JWT access / refresh 正常工作</p></div><span class="signal-ok">在线</span></div>
        <div class="signal-row"><span class="signal-icon green"><Calendar /></span><div><strong>动态菜单</strong><p>权限菜单已加载至当前工作台</p></div><span class="signal-ok">在线</span></div>
        <div class="signal-row"><span class="signal-icon orange"><Lock /></span><div><strong>权限边界</strong><p>后端接口授权已启用</p></div><span class="signal-ok">在线</span></div>
      </el-card>

      <el-card shadow="never" class="surface-card quick-card">
        <template #header><div class="card-heading"><div><span class="panel-kicker">快捷入口</span><h3>常用功能</h3></div></div></template>
        <div class="quick-list">
          <router-link to="/system/users"><span class="quick-index">01</span><span><strong>用户管理</strong><small>维护账号与状态</small></span><b>→</b></router-link>
          <router-link to="/system/roles"><span class="quick-index">02</span><span><strong>角色权限</strong><small>配置角色与菜单授权</small></span><b>→</b></router-link>
          <router-link to="/system/params"><span class="quick-index">03</span><span><strong>参数管理</strong><small>统一维护运行参数</small></span><b>→</b></router-link>
        </div>
      </el-card>
    </section>
  </div>
</template>