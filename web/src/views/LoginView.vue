<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Box, CircleCheck, Document, Lock, Promotion, SetUp, Upload, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: 'admin123' })
const error = ref('')
const deliveryStages = [
  { label: '需求', caption: '范围与目标', icon: Document },
  { label: '研发', caption: '方案与实现', icon: SetUp },
  { label: '版本交付', caption: '构建与发布', icon: Box },
  { label: '测试', caption: '质量与验收', icon: CircleCheck },
  { label: '数据迁移', caption: '校验与切换', icon: Upload },
  { label: '投产', caption: '上线与追踪', icon: Promotion }
]

async function submit() {
  error.value = ''
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = String(route.query.redirect || '/dashboard')
    router.replace(redirect.startsWith('/') ? redirect : '/dashboard')
  } catch {
    error.value = '账号或密码错误，请重试'
  }
}
</script>

<template>
  <main class="login-shell">
    <section class="login-brand">
      <div class="login-brand__top">
        <div class="brand-mark">EP</div>
        <span class="login-brand__product">ENGINEERING DELIVERY</span>
      </div>
      <div class="login-brand__intro">
        <p class="eyebrow">工程交付平台</p>
        <h1>让每一次交付，<br /><em>都有迹可循</em></h1>
        <p class="brand-copy">围绕工程交付全流程，统一沉淀工作上下文，让需求、实现、质量与上线保持同一条线。</p>
      </div>
      <ol class="delivery-flow" aria-label="工程交付流程">
        <li v-for="(stage, index) in deliveryStages" :key="stage.label" :class="{ 'is-final': index === deliveryStages.length - 1 }">
          <div class="delivery-flow__icon"><el-icon><component :is="stage.icon" /></el-icon></div>
          <strong>{{ stage.label }}</strong>
          <span>{{ stage.caption }}</span>
        </li>
      </ol>
      <div class="login-brand__footer"><span>DELIVERY CONTROL CENTER</span></div>
    </section>

    <section class="login-panel">
      <div class="login-heading">
        <h2>工程交付平台</h2>
      </div>
      <el-form @submit.prevent="submit">
        <el-form-item>
          <el-input v-model="form.username" size="large" placeholder="用户名">
            <template #prefix><el-icon><User /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" size="large" type="password" show-password placeholder="密码">
            <template #prefix><el-icon><Lock /></el-icon></template>
          </el-input>
        </el-form-item>
        <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
        <el-button class="login-button" type="primary" size="large" native-type="submit" :loading="auth.loading">
          进入工作台 <el-icon><ArrowRight /></el-icon>
        </el-button>
      </el-form>
    </section>
  </main>
</template>
