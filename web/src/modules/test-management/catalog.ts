import { applicationAssembly } from './application-assembly/catalog'
import { businessDay } from './business-day/catalog'
import { nonFunctional } from './non-functional/catalog'
import { securityTesting } from './security/catalog'
import { userTesting } from './user-testing/catalog'

const domains = [applicationAssembly, userTesting, nonFunctional, securityTesting, businessDay]

export function resolveTestManagementPage(domainKey: string, pageKey: string) {
  const domain = domains.find(item => item.key === domainKey)
  const page = domain?.pages.find(item => item[0] === pageKey)
  return domain && page ? { domain: domain.label, title: page[1] } : null
}
