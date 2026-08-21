/*
文件：web/src/modules/test-management/catalog.ts
说明：测试管理非营业日占位页面的领域目录聚合器。
用途：按动态路由 domain/section 解析页面所属领域和中文标题。
作者：hengguan
*/
import { applicationAssembly } from './application-assembly/catalog'
import { businessDay } from './business-day/catalog'
import { nonFunctional } from './non-functional/catalog'
import { securityTesting } from './security/catalog'
import { userTesting } from './user-testing/catalog'

const domains = [applicationAssembly, userTesting, nonFunctional, securityTesting, businessDay]

// 关键逻辑：只有目录中显式登记的领域与页面组合才能渲染，未知路径返回 null。
export function resolveTestManagementPage(domainKey: string, pageKey: string) {
  const domain = domains.find(item => item.key === domainKey)
  const page = domain?.pages.find(item => item[0] === pageKey)
  return domain && page ? { domain: domain.label, title: page[1] } : null
}
