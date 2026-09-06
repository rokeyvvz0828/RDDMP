import { spawn, spawnSync } from 'node:child_process';
import { existsSync, watch } from 'node:fs';
import net from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scriptDirectory, '..');
const isWindows = process.platform === 'win32';
const commands = {
  docker: 'docker',
  java: 'java',
  maven: isWindows ? 'mvn.cmd' : 'mvn',
  node: process.execPath,
  npm: isWindows ? 'npm.cmd' : 'npm',
};

const ADMIN_USERNAME = 'admin';
const ADMIN_PASSWORD = 'admin123';
const ADMIN_PASSWORD_HASH = '$2a$10$GVYj0Q6sf1RFLC.RU/LB..bUhLyHRvFQ5cr/y1J4F4G./LbYRkRMa';
const MYSQL_CONTAINER = 'rddmp-dev-mysql';
const MINIO_CONTAINER = 'rddmp-dev-minio';
const KKFILEVIEW_CONTAINER = 'rddmp-dev-kkfileview';
const MYSQL_PASSWORD = 'rddmp-dev-mysql-123456';
const MYSQL_ROOT_PASSWORD = 'rddmp-dev-root-123456';
const MINIO_ACCESS_KEY = 'rddmpdev';
const MINIO_SECRET_KEY = 'rddmp-dev-minio-123456';
const JWT_SECRET = 'rddmp-local-dev-only-jwt-secret-20260904-never-use-outside-local';
const KKFILEVIEW_IMAGE = 'keking/kkfileview@sha256:88bdbfd7a8e3b76b784c4c40dd824afbd5f8f63b7e58f7a84c596089a6b48180';
const BACKEND_URL = 'http://127.0.0.1:8080';
const FRONTEND_URL = 'http://127.0.0.1:5173';
const MYSQL_PORT = 13_306;
const MINIO_API_PORT = 19_000;
const MINIO_CONSOLE_PORT = 19_001;
const KKFILEVIEW_PORT = 18_012;

let devEnvironment;
let shuttingDown = false;
let backendProcess;
let frontendProcess;
let compileProcess;
let serverWatcher;
let rootPomWatcher;
let compileTimer;
let compilePending = false;
let completionResolve;
const expectedProcessExits = new WeakSet();

function info(message) {
  process.stdout.write(`[dev] ${message}\n`);
}

function error(message) {
  process.stderr.write(`[dev] ${message}\n`);
}

function usage() {
  process.stdout.write('用法：node scripts/dev.mjs [--down]\n');
}

function executable(command, args) {
  if (isWindows && command.toLowerCase().endsWith('.cmd')) {
    return {
      command: process.env.ComSpec || 'cmd.exe',
      args: ['/d', '/s', '/c', command, ...args],
    };
  }
  return { command, args };
}

function commandResult(command, args, options = {}) {
  const invocation = executable(command, args);
  return spawnSync(invocation.command, invocation.args, {
    cwd: repositoryRoot,
    env: options.env ?? process.env,
    encoding: 'utf8',
    shell: false,
    windowsHide: true,
    stdio: options.inherit ? 'inherit' : 'pipe',
  });
}

function combinedOutput(result) {
  return `${result.stdout ?? ''}${result.stderr ?? ''}`.trim();
}

function requireSuccess(command, args, description, options = {}) {
  const result = commandResult(command, args, options);
  if (result.error || result.status !== 0) {
    const detail = combinedOutput(result) || result.error?.message || `退出码 ${result.status}`;
    throw new Error(`${description}失败：${detail}`);
  }
  return result;
}

function parseMajor(text, pattern, description) {
  const match = text.match(pattern);
  if (!match) {
    throw new Error(`无法识别${description}版本：${text.trim()}`);
  }
  return Number(match[1]);
}

function checkToolchain() {
  const nodeMajor = Number(process.versions.node.split('.')[0]);
  if (nodeMajor < 20) {
    throw new Error(`Node.js 版本必须不低于 20，当前为 ${process.version}`);
  }

  const java = requireSuccess(commands.java, ['-version'], 'Java 检查');
  const javaOutput = combinedOutput(java);
  if (parseMajor(javaOutput, /version\s+"(\d+)/i, 'Java') < 17) {
    throw new Error(`Java 版本必须不低于 17：${javaOutput.split(/\r?\n/)[0]}`);
  }

  const maven = requireSuccess(commands.maven, ['-version'], 'Maven 检查');
  const mavenOutput = combinedOutput(maven);
  if (parseMajor(mavenOutput, /Apache Maven\s+(\d+)/i, 'Maven') < 3) {
    throw new Error(`Maven 版本必须不低于 3.9：${mavenOutput.split(/\r?\n/)[0]}`);
  }
  const mavenVersion = mavenOutput.match(/Apache Maven\s+(\d+)\.(\d+)/i);
  if (!mavenVersion
      || Number(mavenVersion[1]) < 3
      || (Number(mavenVersion[1]) === 3 && Number(mavenVersion[2]) < 9)) {
    throw new Error(`Maven 版本必须不低于 3.9：${mavenOutput.split(/\r?\n/)[0]}`);
  }

  requireSuccess(commands.npm, ['--version'], 'npm 检查');
  requireSuccess(commands.docker, ['version', '--format', '{{.Server.Version}}'], 'Docker Engine 检查');
  requireSuccess(commands.docker, ['compose', 'version'], 'Docker Compose 检查');
}

function resolveMinioHost() {
  if (process.platform !== 'linux') {
    return 'host.docker.internal';
  }
  const result = requireSuccess(
    commands.docker,
    ['network', 'inspect', 'bridge', '--format', '{{(index .IPAM.Config 0).Gateway}}'],
    'Docker 网关检查',
  );
  const gateway = result.stdout.trim();
  if (!/^\d{1,3}(?:\.\d{1,3}){3}$/.test(gateway)) {
    throw new Error(`无法识别 Docker bridge 网关：${gateway}`);
  }
  return gateway;
}

function buildDevEnvironment() {
  const minioHost = resolveMinioHost();
  return {
    ...process.env,
    COMPOSE_DISABLE_ENV_FILE: '1',
    COMPOSE_PROJECT_NAME: 'rddmp-dev',
    MYSQL_CONTAINER_NAME: MYSQL_CONTAINER,
    MINIO_CONTAINER_NAME: MINIO_CONTAINER,
    KK_FILE_VIEW_CONTAINER_NAME: KKFILEVIEW_CONTAINER,
    MYSQL_PORT: String(MYSQL_PORT),
    MINIO_API_PORT: String(MINIO_API_PORT),
    MINIO_CONSOLE_PORT: String(MINIO_CONSOLE_PORT),
    DB_URL: `jdbc:mysql://127.0.0.1:${MYSQL_PORT}/ccb_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false`,
    DB_USERNAME: 'ccb',
    DB_PASSWORD: MYSQL_PASSWORD,
    MYSQL_PASSWORD,
    MYSQL_ROOT_PASSWORD,
    JWT_SECRET,
    JWT_ACCESS_TTL_MILLIS: '900000',
    JWT_REFRESH_TTL_MILLIS: '604800000',
    MINIO_ACCESS_KEY,
    MINIO_SECRET_KEY,
    MINIO_ENDPOINT: `http://${minioHost}:${MINIO_API_PORT}`,
    MINIO_BUCKET: 'ccb-platform',
    MINIO_PRESIGNED_EXPIRY_SECONDS: '3600',
    FILE_PREVIEW_ENABLED: 'true',
    FILE_PREVIEW_MAX_FILE_SIZE: '50MB',
    FILE_PREVIEW_MAX_FILE_SIZE_BYTES: '52428800',
    FILE_PREVIEW_ALLOWED_EXTENSIONS: 'pdf,doc,docx,xls,xlsx,ppt,pptx,txt,md,csv,jpg,jpeg,png,gif,bmp,webp',
    MOCK_DATA_ENABLED: 'true',
    MOCK_DATA_RESOURCE: 'classpath:mock/mock-data.json',
    WORKFLOW_SEEDED_DEFINITION_PUBLISHER_ENABLED: 'true',
    KK_FILE_VIEW_IMAGE: KKFILEVIEW_IMAGE,
    KK_FILE_VIEW_BASE_URL: `http://127.0.0.1:${KKFILEVIEW_PORT}`,
    KK_FILE_VIEW_PORT: String(KKFILEVIEW_PORT),
    KK_FILE_VIEW_HOST_ALIAS: 'host.docker.internal',
    KK_FILE_VIEW_TRUST_HOST: minioHost,
    KK_FILE_VIEW_NOT_TRUST_HOST: 'localhost,127.0.0.1,0.0.0.0',
    BOOTSTRAP_ADMIN_PASSWORD_HASH: ADMIN_PASSWORD_HASH,
    SPRING_CONFIG_IMPORT: 'optional:classpath:/dev-environment-runner.properties',
    SPRING_FLYWAY_ENABLED: 'true',
    SPRING_PROFILES_ACTIVE: 'local',
  };
}

function isContainerRunning(containerName) {
  const result = commandResult(
    commands.docker,
    ['inspect', '--format', '{{.State.Running}}', containerName],
    { env: devEnvironment },
  );
  return result.status === 0 && result.stdout.trim() === 'true';
}

function isPortOpen(port) {
  return new Promise((resolve) => {
    const socket = net.createConnection({ host: '127.0.0.1', port });
    const finish = (value) => {
      socket.removeAllListeners();
      socket.destroy();
      resolve(value);
    };
    socket.setTimeout(500);
    socket.once('connect', () => finish(true));
    socket.once('timeout', () => finish(false));
    socket.once('error', () => finish(false));
  });
}

async function checkPorts() {
  const ports = [
    { port: MYSQL_PORT, container: MYSQL_CONTAINER },
    { port: MINIO_API_PORT, container: MINIO_CONTAINER },
    { port: MINIO_CONSOLE_PORT, container: MINIO_CONTAINER },
    { port: KKFILEVIEW_PORT, container: KKFILEVIEW_CONTAINER },
    { port: 8080 },
    { port: 5173 },
  ];
  for (const entry of ports) {
    if (!await isPortOpen(entry.port)) {
      continue;
    }
    if (entry.container && isContainerRunning(entry.container)) {
      continue;
    }
    throw new Error(`端口 ${entry.port} 已被其他进程占用，请释放后重试。`);
  }
}

function compose(args, options = {}) {
  return requireSuccess(commands.docker, ['compose', ...args], 'Docker Compose', {
    env: devEnvironment,
    inherit: options.inherit,
  });
}

function sleep(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function waitForMysql() {
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    const result = commandResult(
      commands.docker,
      ['exec', '-e', `MYSQL_PWD=${MYSQL_ROOT_PASSWORD}`, MYSQL_CONTAINER, 'mysqladmin', 'ping', '-h', '127.0.0.1', '-uroot', '--silent'],
      { env: devEnvironment },
    );
    if (result.status === 0) {
      info('MySQL 已就绪。');
      return;
    }
    await sleep(2_000);
  }
  throw new Error('等待 MySQL 就绪超时（90 秒）。');
}

function mysql(sql) {
  return commandResult(
    commands.docker,
    ['exec', '-e', `MYSQL_PWD=${MYSQL_ROOT_PASSWORD}`, MYSQL_CONTAINER, 'mysql', '-uroot', '--batch', '--skip-column-names', '-e', sql],
    { env: devEnvironment },
  );
}

function restoreAdminPasswordIfPresent() {
  const tableCheck = mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='ccb_platform' AND table_name='sys_user';");
  if (tableCheck.error || tableCheck.status !== 0) {
    throw new Error(`检查管理员表失败：${combinedOutput(tableCheck) || tableCheck.error?.message}`);
  }
  if (tableCheck.stdout.trim() !== '1') {
    info('检测到空数据库，管理员账号将由 Flyway 初始化。');
    return;
  }
  const escapedHash = ADMIN_PASSWORD_HASH.replaceAll("'", "''");
  const update = mysql(`UPDATE ccb_platform.sys_user SET password_hash='${escapedHash}' WHERE tenant_id=1 AND id=1 AND username='admin'; SELECT ROW_COUNT();`);
  if (update.error || update.status !== 0) {
    throw new Error(`恢复本地管理员密码失败：${combinedOutput(update) || update.error?.message}`);
  }
  info('本地管理员密码已固定为 admin123。');
}

function prefixStream(stream, label, destination) {
  let pending = '';
  stream.setEncoding('utf8');
  stream.on('data', (chunk) => {
    pending += chunk;
    const lines = pending.split(/\r?\n/);
    pending = lines.pop() ?? '';
    for (const line of lines) {
      destination.write(`[${label}] ${line}\n`);
    }
  });
  stream.on('end', () => {
    if (pending) {
      destination.write(`[${label}] ${pending}\n`);
    }
  });
}

function spawnManaged(label, command, args, options = {}) {
  const invocation = executable(command, args);
  const child = spawn(invocation.command, invocation.args, {
    cwd: options.cwd ?? repositoryRoot,
    env: devEnvironment,
    detached: !isWindows,
    shell: false,
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
  });
  prefixStream(child.stdout, label, process.stdout);
  prefixStream(child.stderr, label, process.stderr);
  child.once('error', (spawnError) => {
    error(`${label} 进程启动失败：${spawnError.message}`);
    void shutdown(1, `${label} 启动失败`);
  });
  child.once('exit', (code, signal) => {
    if (!shuttingDown && !options.allowExit && !expectedProcessExits.has(child)) {
      error(`${label} 进程意外退出：code=${code ?? 'null'} signal=${signal ?? 'null'}`);
      void shutdown(code || 1, `${label} 意外退出`);
    }
  });
  return child;
}

async function ensureFrontendDependencies() {
  if (existsSync(path.join(repositoryRoot, 'web', 'node_modules'))) {
    return;
  }
  info('未检测到 web/node_modules，正在按 package-lock.json 安装前端依赖。');
  requireSuccess(commands.npm, ['--prefix', 'web', 'ci'], '前端依赖安装', {
    env: devEnvironment,
    inherit: true,
  });
}

function installBackendArtifacts() {
  info('正在构建并安装后端 reactor 产物。');
  requireSuccess(
    commands.maven,
    ['-pl', ':ccb-boot', '-am', '-DskipTests', 'install'],
    '后端 reactor 安装',
    { env: devEnvironment, inherit: true },
  );
}

function startBackend() {
  return spawnManaged(
    'backend',
    commands.maven,
    ['-f', 'server/src/platform/boot/pom.xml', 'spring-boot:run', '-Dspring-boot.run.profiles=local'],
  );
}

async function waitForBackendHealth(child = backendProcess) {
  const deadline = Date.now() + 180_000;
  let healthySince;
  while (Date.now() < deadline && !shuttingDown) {
    if (!child || child.exitCode !== null || child.signalCode !== null) {
      throw new Error('后端进程在健康检查完成前退出。');
    }
    try {
      const response = await fetch(`${BACKEND_URL}/actuator/health`, { signal: AbortSignal.timeout(2_000) });
      if (response.ok) {
        const body = await response.json();
        if (body.status === 'UP') {
          healthySince ??= Date.now();
          if (Date.now() - healthySince >= 3_000) {
            return;
          }
          await sleep(1_000);
          continue;
        }
      }
    } catch {
      // 后端启动和 Flyway 执行期间继续等待。
    }
    healthySince = undefined;
    await sleep(2_000);
  }
  if (shuttingDown) {
    throw new Error('后端启动等待已取消。');
  }
  throw new Error('等待后端健康检查超时（180 秒）。');
}

function shouldCompile(filename) {
  const normalized = filename.replaceAll('\\', '/');
  if (normalized.includes('/target/') || normalized.startsWith('target/')) {
    return false;
  }
  return normalized.endsWith('/pom.xml')
    || normalized === 'pom.xml'
    || normalized.includes('/src/main/java/')
    || normalized.includes('/src/main/resources/');
}

function scheduleCompile(filename) {
  if (!shouldCompile(filename)) {
    return;
  }
  clearTimeout(compileTimer);
  compileTimer = setTimeout(() => {
    compileTimer = undefined;
    void compileBackend();
  }, 500);
}

async function compileBackend() {
  if (shuttingDown) {
    return;
  }
  if (compileProcess) {
    compilePending = true;
    return;
  }
  info('检测到后端变更，开始构建 reactor 产物。');
  compileProcess = spawnManaged(
    'backend-build',
    commands.maven,
    ['-pl', ':ccb-boot', '-am', '-DskipTests', 'install'],
    { allowExit: true },
  );
  const exitCode = await new Promise((resolve) => compileProcess.once('exit', (code) => resolve(code ?? 1)));
  compileProcess = undefined;
  if (exitCode === 0) {
    try {
      await restartBackend();
    } catch (restartError) {
      error(`后端重启失败：${restartError instanceof Error ? restartError.message : String(restartError)}`);
      await shutdown(1, '后端重启失败');
      return;
    }
  } else {
    error(`后端构建失败（退出码 ${exitCode}），当前后端进程保持运行。`);
  }
  if (compilePending) {
    compilePending = false;
    await compileBackend();
  }
}

async function restartBackend() {
  if (shuttingDown) {
    return;
  }
  info('后端构建完成，正在重启后端进程。');
  const previousBackend = backendProcess;
  if (previousBackend) {
    expectedProcessExits.add(previousBackend);
    await terminateProcessTree(previousBackend);
  }
  if (shuttingDown) {
    return;
  }
  backendProcess = startBackend();
  await waitForBackendHealth(backendProcess);
  info('后端重启完成，健康检查为 UP。');
}

function startBackendWatcher() {
  serverWatcher = watch(path.join(repositoryRoot, 'server'), { recursive: true }, (_event, filename) => {
    if (filename) {
      scheduleCompile(`server/${filename}`);
    }
  });
  rootPomWatcher = watch(path.join(repositoryRoot, 'pom.xml'), () => scheduleCompile('pom.xml'));
}

async function terminateProcessTree(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) {
    return;
  }
  if (isWindows) {
    commandResult('taskkill', ['/PID', String(child.pid), '/T', '/F'], { inherit: true });
  } else {
    try {
      process.kill(-child.pid, 'SIGTERM');
    } catch {
      return;
    }
  }
  await Promise.race([
    new Promise((resolve) => child.once('exit', resolve)),
    sleep(5_000),
  ]);
  if (child.exitCode === null && child.signalCode === null) {
    if (!isWindows) {
      try {
        process.kill(-child.pid, 'SIGKILL');
      } catch {
        // 进程已退出。
      }
    }
  }
}

async function shutdown(exitCode, reason) {
  if (shuttingDown) {
    return;
  }
  shuttingDown = true;
  info(`正在停止应用进程：${reason}`);
  clearTimeout(compileTimer);
  serverWatcher?.close();
  rootPomWatcher?.close();
  await Promise.all([
    terminateProcessTree(compileProcess),
    terminateProcessTree(frontendProcess),
    terminateProcessTree(backendProcess),
  ]);
  process.exitCode = exitCode;
  completionResolve?.();
}

async function startDevelopmentEnvironment() {
  checkToolchain();
  devEnvironment = buildDevEnvironment();
  await checkPorts();
  info('正在启动 rddmp-dev 专用 MySQL、MinIO 和 kkFileView。');
  compose(['up', '-d', 'mysql', 'minio', 'kkfileview'], { inherit: true });
  await waitForMysql();
  restoreAdminPasswordIfPresent();
  await ensureFrontendDependencies();
  installBackendArtifacts();

  backendProcess = startBackend();
  frontendProcess = spawnManaged(
    'frontend',
    commands.npm,
    ['--prefix', 'web', 'run', 'dev', '--', '--host', '127.0.0.1', '--port', '5173', '--strictPort'],
  );
  await waitForBackendHealth(backendProcess);
  startBackendWatcher();
  if (process.env.RDDMP_DEV_VERIFY_BACKEND_RESTART === '1') {
    info('正在执行后端自动重启验收探针。');
    await compileBackend();
  }

  info(`前端：${FRONTEND_URL}`);
  info(`后端：${BACKEND_URL}`);
  info(`MinIO 控制台：http://127.0.0.1:${MINIO_CONSOLE_PORT}`);
  info(`kkFileView：http://127.0.0.1:${KKFILEVIEW_PORT}`);
  info(`开发账号：${ADMIN_USERNAME}/${ADMIN_PASSWORD}（仅限本地开发）`);
  info('按 Ctrl+C 停止前后端；基础设施和数据卷将保留。');

  await new Promise((resolve) => {
    completionResolve = resolve;
  });
}

function stopInfrastructure() {
  requireSuccess(commands.docker, ['compose', 'version'], 'Docker Compose 检查');
  devEnvironment = buildDevEnvironment();
  info('正在停止 rddmp-dev 基础设施，数据卷将保留。');
  compose(['down'], { inherit: true });
}

async function main() {
  const args = process.argv.slice(2);
  if (args.length > 1 || (args.length === 1 && args[0] !== '--down')) {
    usage();
    process.exitCode = 2;
    return;
  }
  if (args[0] === '--down') {
    stopInfrastructure();
    return;
  }

  process.once('SIGINT', () => void shutdown(0, '收到 Ctrl+C'));
  process.once('SIGTERM', () => void shutdown(0, '收到终止信号'));
  await startDevelopmentEnvironment();
}

main().catch(async (mainError) => {
  if (shuttingDown) {
    return;
  }
  error(mainError instanceof Error ? mainError.message : String(mainError));
  await shutdown(1, '启动失败');
});
