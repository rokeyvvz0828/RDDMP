export function formatDateOnly(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  const text = String(value)
  const match = text.match(/^(\d{4})[-\/]?(\d{2})[-\/]?(\d{2})/)
  if (match) return match[1] + '-' + match[2] + '-' + match[3]
  const date = new Date(text)
  if (Number.isNaN(date.getTime())) return '-'
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return year + '-' + month + '-' + day
}