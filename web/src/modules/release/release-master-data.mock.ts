import type { ArtifactTypeCode } from '../../api/release'

export interface ReleaseDeliveryUnitOption {
  id: string
  code: string
  name: string
  artifactType: ArtifactTypeCode
}

export interface ReleaseSubsystemOption {
  id: string
  code: string
  name: string
  units: ReleaseDeliveryUnitOption[]
}

// Temporary selector provider. Replace this export when the R&D master-data module is available.
export const releaseSubsystemOptions: ReleaseSubsystemOption[] = [
  { id: 'SUBSYS-AUTH', code: 'AUTH', name: '统一认证子系统', units: [
    { id: 'DU-AUTH-SVC', code: 'AUTH-SVC', name: '认证服务', artifactType: 'IMAGE' },
    { id: 'DU-AUTH-SDK', code: 'AUTH-SDK', name: '认证客户端组件', artifactType: 'BINARY' }
  ] },
  { id: 'SUBSYS-MSG', code: 'MSG', name: '消息中心子系统', units: [
    { id: 'DU-MSG-SVC', code: 'MSG-SVC', name: '消息中心服务', artifactType: 'IMAGE' },
    { id: 'DU-MSG-WEB', code: 'MSG-WEB', name: '消息中心前端', artifactType: 'BINARY' }
  ] },
  { id: 'SUBSYS-WORKFLOW', code: 'WORKFLOW', name: '流程平台子系统', units: [
    { id: 'DU-WF-SVC', code: 'WF-SVC', name: '流程服务', artifactType: 'IMAGE' },
    { id: 'DU-WF-WEB', code: 'WF-WEB', name: '流程管理前端', artifactType: 'BINARY' }
  ] },
  { id: 'SUBSYS-REPORT', code: 'REPORT', name: '数据分析子系统', units: [
    { id: 'DU-REPORT-SVC', code: 'REPORT-SVC', name: '报表服务', artifactType: 'IMAGE' }
  ] }
]
