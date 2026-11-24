# Java 求职训练营

面向应届生求职展示的 Java 全栈训练营项目：Spring Boot 4 + Java 21 微服务后端、Vue 3 + TypeScript 双前端，覆盖课程/路线、权益、交易退款、作业与 AI 点评、优惠券积分、问答笔记和积分兑换。业务定义见 [docs/requirements/01-业务需求说明.md](docs/requirements/01-业务需求说明.md)，阶段进度见 [docs/design/09-分阶段开发任务书.md](docs/design/09-分阶段开发任务书.md)。

状态：S01～S16 已完成（含演示种子、权限/DTO 扫描、故障恢复、100 并发与性能/EXPLAIN 检查），当前进入 S17 交付整理。真实腾讯云 VOD、真实支付/退款、真实 Qwen/DeepSeek 联调仍为 **NOT_RUN**。

## 1. 文档入口

| 文档 | 职责 |
|---|---|
| [开发约束](AGENTS.md) | 工作方式、停点、验收与文档规则 |
| [工程架构与版本](docs/design/01-工程架构与版本基线.md) | 服务边界、依赖、鉴权、消息、运行配置 |
| [媒体方案](docs/design/02-媒体复用与迁移方案.md) | VOD 复用、DEMO 授权、导入与播放 |
| [交易权益与退款](docs/design/03-交易权益与退款设计.md) | 分摊抵扣、订单支付、来源、退款、历史路线资格 |
| [作业与 AI](docs/design/04-作业与AI点评设计.md) | 题目版本、提交、任务、供应商调用与格式 |
| [优惠券积分与互动](docs/design/05-优惠券积分与互动设计.md) | 最优优惠、账本、榜单、兑换、问答笔记 |
| [页面与权限](docs/design/06-页面流程与权限矩阵.md) | 页面操作、异常状态、角色和数据范围 |
| [数据库](docs/design/07-数据库设计.md) | 各库字段、索引、约束及快照 |
| [接口与事件](docs/design/08-接口与事件契约.md) | 路径、输入输出、错误、内部命令与事件 |
| [开发任务书](docs/design/09-分阶段开发任务书.md) | 17 阶段、小任务、验收与进度 |

业务以 107 个稳定需求编号为准；设计规定实现细节，发现矛盾应报告并修正，不能自行改变收费、权益或核心规则。

## 2. 实际版本基线

以下为本机实际开发/验证版本，不使用 “latest”：

| 组件 | 版本/说明 |
|---|---|
| 操作系统 | Windows 11 |
| Java | 21.0.5 LTS |
| Maven | Wrapper 3.9.9 |
| Spring Boot | 4.0.9（Spring Framework 7.0.10，Tomcat 11.0.27） |
| Spring Cloud | 2025.1.4（仅 Gateway 使用） |
| MyBatis-Plus | 3.5.18（Boot4 starter） |
| Flyway / Connector/J | 11.14.1 / 9.7.0（由 Boot BOM 解析） |
| MySQL | 8.0.34，utf8mb4 / utf8mb4_0900_ai_ci |
| Redis | 7.4.2（Windows 便携构建，Redis 非官方 Windows 发行） |
| RabbitMQ | 3.13.7，vhost `/job-bootcamp-dev` |
| Erlang/OTP | 26.2.5.16 |
| Node / npm | 22.22.2 / 10.9.7 |
| Vue / Vite | 3.5.34 / 7.3.1 |
| TypeScript / vue-tsc | 5.9.3 / 3.2.5 |
| Vue Router / Pinia | 4.6.4 / 3.0.4 |
| Element Plus / Axios | 2.14.8 / 1.21.1 |
| CodeMirror | codemirror 6.x、@codemirror/lang-java 6.x |
| Nacos | 3.1.1 为设计基线；当前本地未接入，网关使用静态路由 |

## 3. 服务与端口

| 模块 | 端口 | 独占数据库 |
|---|---:|---|
| bootcamp-gateway | 18080 | 无 |
| bootcamp-user | 18081 | bc_user |
| bootcamp-course | 18082 | bc_course |
| bootcamp-learning | 18083 | bc_learning |
| bootcamp-trade | 18084 | bc_trade |
| bootcamp-assignment | 18085 | bc_assignment |
| bootcamp-interaction | 18086 | bc_interaction |
| bootcamp-marketing | 18087 | bc_marketing |
| 学生端 Vite | 15173 | — |
| 管理端/教师端 Vite | 15174 | — |

各服务只连自己的库，不跨库 SQL/外键/事务。

## 4. 首次准备

1. 复制本地环境模板，填写真实本地密码；`config/local.env.ps1` 已被忽略，不能提交：

```powershell
Copy-Item config\examples\local.env.example.ps1 config\local.env.ps1
```

2. 确认 Java 21、Node 22、MySQL 8.0.34、RabbitMQ 3.13.7、Redis 7.4.2 已安装；`scripts/check-deps.ps1` 会检查端口和数据库。

3. 创建 7 个空库（若 MySQL 中还没有）。首次创建或需要清空演示数据时，只能用显式本地命令：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\reset-demo-db.ps1 -DryRun
powershell -ExecutionPolicy Bypass -File scripts\reset-demo-db.ps1 -Confirm
```

`-Confirm` 会删除并重建 `bc_course/bc_user/bc_learning/bc_trade/bc_marketing/bc_assignment/bc_interaction`，**只用于本地演示库**。脚本要求服务已停止；生产或含真实数据的环境禁止执行。完全干净演示时还应停止 RabbitMQ 并清理本地 `.local-run\rabbitmq` 队列数据。

4. 构建：

```powershell
cd backend
.\mvnw.cmd -B verify
cd ..\frontend
npm ci
npm run typecheck
npm run build
```

## 5. 启动与演示种子

### 5.1 最小启动（无 Rabbit/Redis）

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-deps.ps1
powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1
```

学生端 <http://localhost:15173/>，管理端 <http://localhost:15174/>，网关 <http://127.0.0.1:18080/>。日志在 `.local-run/logs`，PID 在 `.local-run/pids.json`；停止只结束自己记录的 PID：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
```

### 5.2 完整事件链（Rabbit/Redis）

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-redis.ps1
powershell -ExecutionPolicy Bypass -File scripts\start-rabbit.ps1
$env:OUTBOX_TRANSPORT = 'rabbit'
$env:OUTBOX_RELAY_ENABLED = 'true'
$env:AI_WAKEUP_ENABLED = 'true'
$env:POINTS_TEST_API_ENABLED = 'true'
powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1
```

停止顺序：`stop-local.ps1` → `stop-redis.ps1` → `stop-rabbit.ps1`。

### 5.3 演示种子

在服务已启动且 Rabbit outbox 已开启时执行：

```powershell
node scripts\seed-demo.mjs
node tests\acceptance\s16a-demo-seed.mjs
```

种子通过 API 创建 3 门演示付费课（各 10 节）、路线 R1/R2、教师分配、章节选择题、活动券与积分兑换、演示账号购课/路线抵扣/免费领取、公开/私密/采集笔记、公开/匿名问答和教师回答。固定名称/幂等键允许重复执行；积分/债务夹具直接更新 marketing 表，只面向虚构演示账号。

演示账号（虚构）：`student01`～`student03`、`teacher01`～`teacher02`、`admin01`；密码默认 `Demo@123456`，可用 `DEMO_PASSWORD` 覆盖。

## 6. 环境变量

模板见 [config/examples/local.env.example.ps1](config/examples/local.env.example.ps1)。常用变量：

| 类别 | 变量 |
|---|---|
| 数据库 | `DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD`、`DB_NAME`、`USER_DB_NAME`、`LEARNING_DB_NAME`、`TRADE_DB_NAME`、`MARKETING_DB_NAME`、`ASSIGNMENT_DB_NAME`、`INTERACTION_DB_NAME` |
| 端口 | `GATEWAY_PORT`、`USER_PORT`、`COURSE_PORT`、`LEARNING_PORT`、`TRADE_PORT`、`ASSIGNMENT_PORT`、`INTERACTION_PORT`、`MARKETING_PORT` |
| Redis | `REDIS_HOST`、`REDIS_PORT`、`REDIS_ENABLED`、`REDIS_KEY_PREFIX` |
| Rabbit/Outbox | `RABBIT_HOST`、`RABBIT_PORT`、`RABBIT_USERNAME`、`RABBIT_PASSWORD`、`RABBIT_VHOST`、`RABBIT_EXCHANGE`、`OUTBOX_TRANSPORT`、`OUTBOX_RELAY_ENABLED` |
| 账号/密钥 | `DEMO_PASSWORD`、`INTERNAL_TOKEN_KEY`、`JWT_PRIVATE_KEY_PATH`、`JWT_PUBLIC_KEY_PATH` |
| 支付/媒体 | `PAYMENT_MODE`、`PAYMENT_MOCK_SCENARIO_ENABLED`、`DEMO_MEDIA_ROOT`、`MEDIA_SIGNING_KEY`、`VOD_ENABLED`、`VOD_APP_ID`、`VOD_SECRET_ID`、`VOD_SECRET_KEY`、`VOD_URL_KEY`、`VOD_PCFG` |
| AI | `AI_MODE`、`AI_WAKEUP_ENABLED`、`AI_MOCK_POLL_MS`、`AI_MOCK_DELAY_MS`、`AI_LEASE_SECONDS`、`AI_LEASE_RENEW_MS`、`QWEN_API_KEY`、`DEEPSEEK_API_KEY`、`QWEN_BASE_URL`、`DEEPSEEK_BASE_URL`、`QWEN_MODEL`、`DEEPSEEK_MODEL` |
| 测试夹具 | `POINTS_TEST_API_ENABLED`（默认 false；仅本地测试注册 `/dev/points/**`） |

密钥只放本机环境变量或未入库的 `config/local.env.ps1`；不写入仓库、日志、截图或聊天。生产必须替换 `INTERNAL_TOKEN_KEY`、JWT 密钥、媒体签名密钥，并关闭所有 mock/dev 控制。

## 7. 主要页面

学生端：`/courses`、`/routes`、`/me/courses`、`/me/routes`、`/activities`、`/exchange`、`/me/exchanges`、`/me/coupons`、`/learning/courses/:id`、`/courses/:id/questions`、`/courses/:id/notes`、`/notes/:id`、`/me/notes`、`/assignments/:id`、`/benefit-assignments/:id`、`/submissions/:id`、`/checkout`、`/orders`、`/orders/:id`、`/sign-in`、`/me/points`、`/boards`。

管理端/教师端：`/admin/categories`、`/admin/courses`、`/admin/routes`、`/admin/teachers`、`/admin/media`、`/admin/activities`、`/admin/exchange-projects`、`/admin/trades`、`/admin/refunds`、`/admin/assignments`、`/admin/moderation`、`/admin/users`、`/teacher/submissions`、`/teacher/benefits`、`/teacher/questions`、`/teacher/notes`。

## 8. 验证入口

| 类别 | 命令/文件 |
|---|---|
| 后端测试 | `backend\mvnw.cmd -B verify`（当前 45 个测试） |
| 前端 | `npm run typecheck`、`npm run build` |
| 演示种子/权限 | `node tests\acceptance\s16a-demo-seed.mjs`、`node tests\acceptance\s16a-security-matrix.mjs` |
| 故障恢复 | `node tests\acceptance\s16b-fault-recovery.mjs` |
| 并发/性能/EXPLAIN | `node tests\acceptance\s16c-concurrency.mjs`、`node tests\acceptance\s16c-price-explain.mjs` |
| 分阶段接口/UI | `tests/acceptance/s02a…` 至 `s15c…` 的脚本；每个阶段记录在 `tests/results/Sxx-验证记录.md` |

S16-c 本机参考数据（单实例、冷热 JVM）：100 相同积分事件热态 p95≈96ms；100 兑换抢 10 库存热态 p95≈330ms；同一代码 attempt 100 次提交 p95≈217ms；100 券 AUTO 最优 p95≈1621ms。仅为本机证据，不代表生产吞吐。

## 9. 真实联调与已知限制

- 真实腾讯云 VOD、真实支付渠道/商户回调、真实退款渠道、真实 Qwen/DeepSeek 账号与计费：**NOT_RUN**，当前只验证 MOCK/DEMO/兼容桩分支。
- Nacos 未接入，网关使用静态路由；Redis 为可重建投影，删除后从 MySQL 恢复。
- 故障注入为本机进程级，不是多实例网络分区/broker 集群演练。
- 管理员福利题治理、全站内容治理分页、移动端和无障碍仍未完成或未测。
- 演示种子直接写积分/债务夹具，只用于本地虚构账号；不要在生产数据上运行 `reset-demo-db.ps1` 或 `seed-demo.mjs`。

## 10. 给开发模型的启动指令

> 按 README 与 AGENTS 阅读本项目文档，本次仅执行任务书指定的一个小任务。遵守已定需求和技术契约，不修改旧项目 `tjxt-ai-master`、不提前实现后续模块。完成后报告实际验证结果、关键设计及启动步骤，更新任务进度并停止，等待用户确认继续；未执行的验证明确标记。