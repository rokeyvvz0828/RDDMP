<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowRight, Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const form = reactive({ username: 'admin', password: 'admin123' })
const error = ref('')

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
      <p class="eyebrow">统一平台</p>
      <h1>统一管理平台</h1>
      <p class="brand-copy">组织、权限、配置和业务能力，在一个清晰的工作台里协同运行。</p>
      <div class="brand-line"></div>
      <span>单租户基础版</span>
    </section>

    <section class="login-panel">
      <div class="login-heading">
        <span class="panel-kicker">欢迎回来</span>
        <h2>登录系统</h2>
        <p>使用平台账号进入工作台</p>
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