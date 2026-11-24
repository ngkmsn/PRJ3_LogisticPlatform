import { app, BrowserWindow, net, protocol, shell } from 'electron'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { isInAppNavigation } from './navigation.js'

/**
 * The built SPA is served from app://dispatcher/ instead of file:// so requests carry
 * `Origin: app://dispatcher`, which the API gateway allows via CORS (file:// sends Origin: null).
 */
const SCHEME = 'app'
const HOST = 'dispatcher'
const devUrl = process.env.ELECTRON_DEV_URL

const here = path.dirname(fileURLToPath(import.meta.url))
const distDir = path.join(here, '..', 'dist')

protocol.registerSchemesAsPrivileged([
  { scheme: SCHEME, privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true } },
])

function serveDist() {
  protocol.handle(SCHEME, (request) => {
    const { pathname } = new URL(request.url)
    const relative = decodeURIComponent(pathname).replace(/^\/+/, '') || 'index.html'
    const filePath = path.normalize(path.join(distDir, relative))
    // Never serve anything outside the bundled dist directory.
    if (!filePath.startsWith(distDir)) {
      return new Response('Not found', { status: 404 })
    }
    return net.fetch(pathToFileURL(filePath).toString())
  })
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1440,
    height: 900,
    minWidth: 1200,
    minHeight: 760,
    title: 'Logistics Dispatcher',
    backgroundColor: '#111d2c',
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  })

  // Links to other sites (e.g. OpenStreetMap attribution) open in the system browser,
  // whether they target a new window or try to navigate the app window itself.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })
  win.webContents.on('will-navigate', (event, url) => {
    if (isInAppNavigation(url, devUrl)) return
    event.preventDefault()
    if (/^https?:\/\//.test(url)) shell.openExternal(url)
  })

  if (devUrl) {
    win.loadURL(devUrl)
  } else {
    win.loadURL(`${SCHEME}://${HOST}/index.html`)
  }
}

app.whenReady().then(() => {
  if (!devUrl) serveDist()
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit()
})
