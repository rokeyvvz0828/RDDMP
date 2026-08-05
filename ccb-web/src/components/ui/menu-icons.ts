import {
  Avatar,
  Collection,
  Connection,
  DataBoard,
  Document,
  Folder,
  Grid,
  Menu,
  Monitor,
  OfficeBuilding,
  Operation,
  Setting,
  Tickets,
  Tools,
  User
} from '@element-plus/icons-vue'

const iconMap = {
  avatar: Avatar,
  collection: Collection,
  connection: Connection,
  dashboard: DataBoard,
  document: Document,
  folder: Folder,
  grid: Grid,
  menu: Menu,
  monitor: Monitor,
  org: OfficeBuilding,
  operation: Operation,
  setting: Setting,
  tickets: Tickets,
  tools: Tools,
  user: User
}

export const menuIconOptions = [
  { key: 'grid', label: '网格' },
  { key: 'dashboard', label: '工作台' },
  { key: 'folder', label: '目录' },
  { key: 'setting', label: '设置' },
  { key: 'user', label: '用户' },
  { key: 'org', label: '组织' },
  { key: 'menu', label: '菜单' },
  { key: 'collection', label: '集合' },
  { key: 'document', label: '文档' },
  { key: 'monitor', label: '监控' },
  { key: 'operation', label: '操作' },
  { key: 'tickets', label: '工单' },
  { key: 'tools', label: '工具' },
  { key: 'connection', label: '连接' },
  { key: 'avatar', label: '成员' }
] as const

export function resolveMenuIcon(name?: string | null) {
  return iconMap[String(name || '').toLowerCase() as keyof typeof iconMap] || Grid
}
