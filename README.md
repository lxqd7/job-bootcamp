# Java求职训练营：开发入口

状态：S01 已完成（工程骨架、Flyway 最小迁移、两个 Vue3＋TS 应用、PowerShell 启停脚本与本地构建验证）；S02 已完成（账号会话、网关与内部凭据、教师/管理员账号管理、登录页与角色菜单；双标签页并发刷新已验证）；S03 已完成（分类闭包与课程目录、路线版本与分摊、教师分配、本地 outbox、媒体导入/校验/管理员预览与演示视频；真实腾讯云 VOD 因无凭据 NOT_RUN）；S04 已完成（积分账本、签到连续奖励、月榜、Redis 投影与故障回退、学生端签到/积分/月榜页面）；S05 已完成（learning 服务、免费领取、播放授权、学习进度、首次完成事件、我的课程与学习页面）；S06-a 已完成（trade 服务、可信商品快照、5 分钟报价、30 分钟待支付订单、order_course/order_lesson/pending_course、创建幂等与重叠订单冲突；优惠券固定 NONE）；S06-b 已完成（PaymentPort 与 MOCK 支付、HMAC 回调验签与回执流水、trade outbox 授课、paid/fulfillment 分离、learning 付费授予与回执、取消/到期/未知处理、SYSTEM_RETURN 退款通道）；S06-c 已完成（学生确认订单、支付页、订单列表/详情，管理端开发支付场景面板；付费课程购买入口与免费领取入口）；S07-a 已完成（路线报价、按目标课程最早 ACTIVE 来源抵扣、purchase_hold/deduction_link、路线订单金额快照、部分已有只付差额、全部已有拒绝零元单）；S07-b 已完成（路线支付、ROUTE_ORDER 来源、路线资格、免费课自动 FREE 来源、未来 epoch 补发与移除保留）；S07-c 已完成（路线详情购买入口、我的路线、路线确认页抵扣明细与退款依赖提示）；S08-a 已完成（券规则/活动/库存限领、活动免费领券、我的券、管理端活动与券规则页面）；S08-b 已完成（统一券分摊、AUTO 全局最优、MANUAL 排列、分类范围快照、500ms 异步报价与租约恢复）；S08-c 已完成（下单 all-or-none 锁券、支付核销、取消/到期释放、营销宽限对账恢复，以及结算选券与订单券明细页面）。下一任务 S09-a。新工程目录D:\Develop\JAVA\IdeaProjects\job-bootcamp；旧工程tjxt-ai-master只读参考。

## 阅读顺序

首次读[开发约束](AGENTS.md)、[业务需求](docs/requirements/01-业务需求说明.md)及[阶段任务书](docs/design/09-分阶段开发任务书.md)。之后按当前任务阅读对应设计，不反复通读全部文件。

| 文档 | 职责 |
|---|---|
| [工程架构与版本](docs/design/01-工程架构与版本基线.md) | 服务边界、依赖、鉴权、消息、运行配置 |
| [媒体方案](docs/design/02-媒体复用与迁移方案.md) | VOD复用、DEMO授权、导入与播放 |
| [交易权益与退款](docs/design/03-交易权益与退款设计.md) | 分摊抵扣、订单支付、来源、退款、历史路线资格 |
| [作业与AI](docs/design/04-作业与AI点评设计.md) | 题目版本、提交、任务、供应商调用与格式 |
| [优惠券积分与互动](docs/design/05-优惠券积分与互动设计.md) | 最优优惠、账本、榜单、兑换、问答笔记 |
| [页面与权限](docs/design/06-页面流程与权限矩阵.md) | 页面操作、异常状态、角色和数据范围 |
| [数据库](docs/design/07-数据库设计.md) | 各库字段、索引、约束及快照 |
| [接口与事件](docs/design/08-接口与事件契约.md) | 路径、输入输出、错误、内部命令与事件 |
| [开发任务书](docs/design/09-分阶段开发任务书.md) | 17阶段、51小任务、验收与进度 |

业务以107个稳定需求编号为准；设计规定实现细节，若发现矛盾应报告并修正，不能自行改变收费或权益。文件名连续编号；README.md、AGENTS.md保留工具约定名称。

## 本地运行

前置：Java 21、Node 22、MySQL 8.0（本机 8.0.34）。数据库 `bc_course`、`bc_user`、`bc_learning`、`bc_trade` 和 `bc_marketing` 需要预先创建：

```sql
CREATE DATABASE bc_course CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_user CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_trade CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_marketing CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

1. 复制环境模板并填写密码，该文件不入库：

```powershell
Copy-Item config\examples\local.env.example.ps1 config\local.env.ps1
```

2. 构建后端与前端：

```powershell
cd backend; .\mvnw.cmd -B verify; cd ..\frontend; npm install
```

3. 检查依赖、启动、停止：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\check-deps.ps1
powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1
powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
```

4. 需要月榜 Redis 投影时启动 Redis；需要验证免费领取事件链或 Rabbit 投递时启动 broker，并开启课程 outbox relay：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-redis.ps1
powershell -ExecutionPolicy Bypass -File scripts\start-rabbit.ps1
$env:OUTBOX_TRANSPORT = 'rabbit'
$env:OUTBOX_RELAY_ENABLED = 'true'
powershell -ExecutionPolicy Bypass -File scripts\start-local.ps1
# 验收完成后
powershell -ExecutionPolicy Bypass -File scripts\stop-local.ps1
powershell -ExecutionPolicy Bypass -File scripts\stop-redis.ps1
powershell -ExecutionPolicy Bypass -File scripts\stop-rabbit.ps1
```

学生端 <http://localhost:15173/>，管理端 <http://localhost:15174/>，网关 <http://127.0.0.1:18080/>，课程服务健康 <http://127.0.0.1:18082/actuator/health>，账号服务健康 <http://127.0.0.1:18081/actuator/health>，学习服务健康 <http://127.0.0.1:18083/actuator/health>，交易服务健康 <http://127.0.0.1:18084/actuator/health>，营销服务健康 <http://127.0.0.1:18087/actuator/health>。脚本启动网关、课程、账号、学习、交易、营销六个后端服务与两个前端；停止时只结束自己记录的 PID，不按端口杀进程。运行日志在 `.local-run/logs`。

当前前端 Vite 的 `/api` 已指向网关 18080，`/actuator` 仍直连 18082 用于首页健康展示；网关的受保护接口需要 access token，内部 `/int/v1/**` 不对外暴露。网关本地使用静态路由，Nacos/Redis 尚未接入。

学生端登录页 <http://localhost:15173/login>，课程页 `/courses`、路线页 `/routes`、我的课程 `/me/courses`、我的路线 `/me/routes`、活动页 `/activities`、我的券 `/me/coupons`、学习页 `/learning/courses/:id`、确认订单 `/checkout?courseId=:id`（路线用 `/checkout?productType=ROUTE&productId=:id`）、订单列表 `/orders`、订单详情 `/orders/:id`、模拟支付 `/orders/:id/pay`、签到页 `/sign-in`、积分页 `/me/points`、月榜页 `/boards`；管理端登录页 <http://localhost:15174/login>，分类/课程/路线/教师分配/媒体管理在 `/admin/categories`、`/admin/courses`、`/admin/routes`、`/admin/teachers`、`/admin/media`，优惠活动与券规则在 `/admin/activities`，开发支付场景在 `/admin/trades`。演示账号（虚构）：student01～03、teacher01～02、admin01，默认密码 `Demo@123456`，可用 `DEMO_PASSWORD` 覆盖。首次启动账号服务会在 `.local-run/jwt` 生成开发用 RS256 密钥；生产必须通过 `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH` 提供并关闭自动生成。内部服务凭据使用 `INTERNAL_TOKEN_KEY`，本地有 dev 占位值，生产必须注入真实密钥。演示目录包含 Java 分类树、免费课程“Java 入门体验”（已上架）和付费课程“Java 并发实战”（草稿）；路线由管理员页面或验收脚本创建。演示媒体 `media/demo/flower.mp4` 为 MDN CC0 示例视频（5.055 秒），管理端媒体页可导入、校验并生成 120 秒管理员预览地址；`DEMO_MEDIA_ROOT`、`MEDIA_SIGNING_KEY` 可覆盖本地默认值。腾讯云 VOD 默认关闭，需配置 `VOD_ENABLED`、`VOD_APP_ID`、`VOD_SECRET_ID`、`VOD_SECRET_KEY`、`VOD_URL_KEY`、`VOD_PCFG` 后才走真实适配器；无凭据时导入媒体标记为异常，不伪装可用。学生积分接口为 `GET /api/v1/me/points`、`GET /api/v1/me/points/ledger`、`POST/GET /api/v1/sign-ins`，月榜为 `GET /api/v1/boards/monthly?month=YYYY-MM&page=1&pageSize=20`，由网关转发到营销服务；签到和积分仅面向学生，月榜登录后可看。学习权益接口为 `GET /api/v1/me/courses`、`GET /api/v1/me/routes`、`POST /api/v1/courses/{id}/claim`、`GET /api/v1/claims/{id}`，由 learning 服务（18083）提供；免费领取在课程库受理后写 `course.free-claim-accepted.v1` outbox，经 Rabbit 由 learning 建立 FREE 来源，再回传 `learning.free-claim-completed.v1` 更新 claim 状态。播放接口为 `POST /api/v1/lessons/{id}/play-authorizations`，进度为 `PUT /api/v1/learning/lessons/{id}/progress`，只接收 position；课程学习目录为 `GET /api/v1/learning/courses/{id}`；首次完成经 `learning.lesson-completed.v1` 到营销消费。学生端已有我的课程和学习页面，播放 DEMO 视频并显示进度/首次完成提示。交易接口为 `POST /api/v1/trade/quotes`、`GET /api/v1/trade/quotes/{id}`、`POST /api/v1/orders`（可选 `Idempotency-Key`）、`GET /api/v1/orders`、`GET /api/v1/orders/{id}`，由 trade 服务（18084）提供；报价支持 `COURSE` 与 `ROUTE`。S07-a 路线报价取路线内部快照和每门课最早 ACTIVE 来源，已拥有课程按套餐分摊价抵扣，订单只保存本次新购付费课，取消会释放 `purchase_hold`/`deduction_link`；全部课程已拥有返回 `ALREADY_OWNED`，不创建零元订单。S07-b 已开放路线支付：有效回调提交 hold/deduction_link，learning 建立 `ROUTE_ORDER`、`route_entitlement`、`grant_route_member`，免费成员建立独立 FREE 来源；后续路线新增课程按新 epoch 的 joinedAt 补发，移除只关闭 epoch、不撤销已有 grant。S06-b 增加 `POST /api/v1/orders/{id}/payment`（当前仅 `MOCK`）、`POST /api/v1/orders/{id}/cancel`、`POST /callbacks/payments/{channel}`（HMAC 签名）、`GET /api/v1/refunds/{id}` 和开发场景 `POST /api/v1/dev/payments/{id}/scenario`（仅 ADMIN，默认关闭，需 `PAYMENT_MOCK_SCENARIO_ENABLED=true`）；S06-c 增加 `POST /api/v1/orders/{id}/payment/mock-pay`（学生固定 MOCK 成功动作）和 `GET /api/v1/admin/orders`（ADMIN 运营列表）；`payment_request` 不可变，`payment` 只记录确认的收款流水，`channel_receipt` 保存回调回执；订单 `PAID` 与 `fulfillment_state=PENDING/RUNNING/DONE` 分离，trade outbox 发 `trade.payment-confirmed.v1`，learning 建 `COURSE_ORDER` 来源并回传 `learning.order-granted.v1`。S08-c 起确认订单页默认请求 AUTO 报价并展示逐券分摊，支持切换手选最多 3 张或不用券；下单把报价中确认的券 ID 精确提交，营销 `coupon_lock` 一次性锁定全部券，支付成功后核销，取消/到期释放；订单详情展示逐券基数、优惠和课程分摊。营销按宽限期扫描 LOCKED 记录，通过交易内部状态接口补齐核销或释放。学习服务停机时事件留在 Rabbit 队列，恢复后补发；晚于到期时间或重复/额外收款不发权益，自动创建 `SYSTEM_RETURN`。默认 `OUTBOX_TRANSPORT=none` 时发权事件只落库待投递，需按下方 Rabbit 步骤启动。确认订单、支付页、订单页与管理场景面板属 S06-c；真实支付渠道未配置。Redis 投影 key 使用 `bc:dev:` 前缀，删除后可自动从 MySQL 重建；停机时月榜回退 MySQL。积分/签到验收需要临时设置 `POINTS_TEST_API_ENABLED=true` 才注册 `/dev/points/**` 测试夹具，默认 false，生产不暴露加分接口。RabbitMQ 3.13.7 与 Erlang/OTP 26.2.5.16 已在本机便携目录中，可用 `scripts/start-rabbit.ps1`/`stop-rabbit.ps1` 启停；数据目录隔离在 `.local-run/rabbitmq`，vhost `/job-bootcamp-dev`（guest/guest）。`OUTBOX_TRANSPORT` 默认 `none`，outbox 行保持待投递并记录 `TRANSPORT_DISABLED`；以 `OUTBOX_TRANSPORT=rabbit`、`OUTBOX_RELAY_ENABLED=true` 启动后，课程免费领取事件走 `bc.domain.v1` topic exchange，`bc.learning.course-free-claim.v1` 与 `bc.course.learning-free-claim.v1` 队列完成双向事件链；营销投影队列 `bc.marketing.projection.v1` 也已实测。接口验收入口：`tests/acceptance/s02a-auth-api.sh`、`s02b-gateway-api.sh`、`s03a-catalog-api.sh`、`s03b-route-api.sh`、`s03c-media-api.sh`、`s04a-points-api.sh`、`s04b-sign-board-api.sh`、`s05a-entitlement-api.sh`、`s05b-progress-api.sh`、`s06a-order-api.sh`、`s06b-payment-api.sh`、`s06c-ui.mjs`、`s07a-route-purchase-api.sh`、`s07b-route-payment-api.sh`、`s07c-ui.mjs`、`s08a-coupon-api.sh`、`s08a-ui.mjs`、`s08b-pricing-api.sh`、`s08c-coupon-order-api.sh`、`s08c-ui.mjs`；浏览器验证记录见 `tests/results/S01-验证记录.md`、`S02-验证记录.md`、`S03-验证记录.md`、`S04-验证记录.md`、`S05-验证记录.md`、`S06-验证记录.md`、`S07-验证记录.md`、`S08-验证记录.md`（每个阶段一份，小任务内容合并在同一文件）。

## 给开发模型的启动指令

> 按README与AGENTS阅读本项目文档，本次仅执行任务书指定的一个小任务（当前下一任务 S09-a）。遵守已定需求和技术契约，不修改旧项目、不提前实现后续模块。完成后报告实际验证结果、关键设计及启动步骤，更新任务进度并停止，等待我确认继续；未执行的验证明确标记。

之后可指定“继续S06-a”等编号。缺少真实云凭据时完成显式模拟路径，真实联调另标NOT_RUN；文档校验不等于应用已运行通过。
