/*
 * 文件：web/src/modules/test-management/announcement/wang-editor.d.ts
 * 说明：测试公告板的前端类型、接口或菜单配置。
 * 用途：集中维护模块可复用的前端契约或导航定义。
 * 作者：hengguan
 */
declare module '@wangeditor/editor-for-vue' {
  import type { Component } from 'vue'
  export const Editor: Component
  export const Toolbar: Component
}
