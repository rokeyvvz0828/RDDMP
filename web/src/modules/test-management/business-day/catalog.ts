/*
文件：web/src/modules/test-management/business-day/catalog.ts
说明：营业日管理 V51 历史三级路径的兼容目录常量。
用途：保留旧 slug 语义；正式页面由单一入口和顶部视图导航承载。
作者：hengguan
*/
export const businessDay = {
  key: 'business-day',
  label: '营业日管理',
  pages: [
    ['calendar-overview', '日历概览'],
    ['calendar-schedule', '日历安排'],
    ['batch-requirements', '跑批需求'],
    ['test-environments', '测试环境管理']
  ]
} as const
