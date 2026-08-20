import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(fileURLToPath(import.meta.url))
const html = await readFile(resolve(root, 'rddmp-promo.html'))
const output = resolve(root, 'rddmp-tech-promo.webm')
const server = createServer((request, response) => {
  if (request.method === 'POST' && request.url === '/upload') {
    const chunks = []
    request.on('data', chunk => chunks.push(chunk))
    request.on('end', async () => {
      try {
        await import('node:fs/promises').then(fs => fs.writeFile(output, Buffer.concat(chunks)))
        response.writeHead(201, { 'Content-Type': 'text/plain; charset=utf-8' })
        response.end('saved')
      } catch (error) {
        response.writeHead(500)
        response.end(String(error))
      }
    })
    return
  }
  if (request.url !== '/' && request.url !== '/rddmp-promo.html') {
    response.writeHead(404)
    response.end('Not found')
    return
  }
  response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' })
  response.end(html)
})
server.listen(4180, '127.0.0.1', () => console.log('RDDMP promo server: http://127.0.0.1:4180/'))
