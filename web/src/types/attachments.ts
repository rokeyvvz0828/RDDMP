export interface ProjectAttachment {
  id: number
  fileName: string
  contentType: string
  size: number
  uploaderId: number
  uploaderName?: string | null
  createdAt?: string | null
  categoryId?: number | null
  categoryName?: string | null
}

export interface AttachmentCategory {
  id: number
  name: string
  sortNo: number
}

export interface ProjectAttachmentPage {
  records: ProjectAttachment[]
  total: number
  page: number
  size: number
}

export interface AttachmentLink {
  attachmentId: number
  fileName: string
  url: string
}
