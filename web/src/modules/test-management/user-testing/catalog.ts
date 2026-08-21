/*
文件：web/src/modules/test-management/user-testing/catalog.ts
说明：用户测试的页面目录常量。
用途：为动态路由提供稳定 slug 与中文页面名称映射。
作者：hengguan
*/
export const userTesting = {
  key: 'user-testing',
  label: '用户测试',
  pages: [
    ['dashboard', '测试公告板'],
    ['plans', '测试方案'],
    ['scope', '测试范围'],
    ['cases', '测试案例'],
    ['defects', '测试缺陷'],
    ['reports', '测试报告'],
    ['analytics', '分析统计']
  ]
} as const
