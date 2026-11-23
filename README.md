# Java求职训练营：开发入口

状态：S01 已完成（工程骨架、Flyway 最小迁移、两个 Vue3＋TS 应用、PowerShell 启停脚本与本地构建验证）；S02 已完成（账号会话、网关与内部凭据、教师/管理员账号管理、登录页与角色菜单；双标签页并发刷新已验证）；S03 已完成（分类闭包与课程目录、路线版本与分摊、教师分配、本地 outbox、媒体导入/校验/管理员预览与演示视频；真实腾讯云 VOD 因无凭据 NOT_RUN）；S04 已完成（积分账本、签到连续奖励、月榜、Redis 投影与故障回退、学生端签到/积分/月榜页面）；S05 已完成（learning 服务、免费领取、播放授权、学习进度、首次完成事件、我的课程与学习页面）；S06-a 已完成（trade 服务、可信商品快照、5 分钟报价、30 分钟待支付订单、order_course/order_lesson/pending_course、创建幂等与重叠订单冲突；优惠券固定 NONE）；S06-b 已完成（PaymentPort 与 MOCK 支付、HMAC 回调验签与回执流水、trade outbox 授课、paid/fulfillment 分离、learning 付费授予与回执、取消/到期/未知处理、SYSTEM_RETURN 退款通道）；S06-c 已完成（学生确认订单、支付页、订单列表/详情，管理端开发支付场景面板；付费课程购买入口与免费领取入口）；S07-a 已完成（路线报价、按目标课程最早 ACTIVE 来源抵扣、purchase_hold/deduction_link、路线订单金额快照、部分已有只付差额、全部已有拒绝零元单）；S07-b 已完成（路线支付、ROUTE_ORDER 来源、路线资格、免费课自动 FREE 来源、未来 epoch 补发与移除保留）；S07-c 已完成（路线详情购买入口、我的路线、路线确认页抵扣明细与退款依赖提示）；S08-a 已完成（券规则/活动/库存限领、活动免费领券、我的券、管理端活动与券规则页面）；S08-b 已完成（统一券分摊、AUTO 全局最优、MANUAL 排列、分类范围快照、500ms 异步报价与租约恢复）；S08-c 已完成（下单 all-or-none 锁券、支付核销、取消/到期释放、营销宽限对账恢复，以及结算选券与订单券明细页面）；S09-a 已完成（退款申请序列、购买时固定小节清单与完成快照、来源冻结、交易自动资格检查和待付抵扣依赖协调）；S09-b 已完成（退款状态机、渠道 query-before-retry、MOCK UNKNOWN/成功/关闭、撤权恢复、同一 refundNo 重试和券不退）；S09-c 已完成（学生退款申请/状态/详情页面、订单退款入口和管理端异常退款对账页面）；S10-a 已完成（新增 assignment 服务、章节选择题/课程代码题草稿、发布不可变版本、下架和管理端题目配置页）；S10-b 已完成（attempt 绑定版本、选择题判分、跨设备草稿冲突、提交历史、代码题 mock 异步点评与单任务约束）；S10-c 已完成（学生章节自测/代码 slot 页面、当前版本最近最高成绩、提交解析与参考实现、AI 点评轮询、教师负责课程只读作答记录）；S11-a 已完成（ai_attempt、worker 租约/fencing、MQ 唤醒与扫描兜底、10 分钟超时、6 次预算和失败释放名额）；S11-b 已完成（Qwen/DeepSeek HTTP 适配、review-v1 提示词、严格 schema、格式修复、错误分类与 6 次调用上限；真实账号 NOT_RUN）；S11-c 已完成（状态轨道与自动轮询、完成/失败面板、安全错误提示、教师只读反馈、无模型选择/独立重试）；S12-a 已完成（教师课程＋路线双范围、福利单题版本、首次发布绑定路线 epoch、epoch 变化新题）；S12-b 已完成（课程＋路线双权益、历史 epoch、退款冻结、下架与教师调离边界）；S12-c 已完成（教师福利题页面、路线课程福利入口、福利选择/代码题作答与教师只读反馈）；S13-a 已完成（interaction 服务、问答/回答/评论、匿名投影、教师身份、逻辑墓碑、管理员隐藏与匿名追溯）；S13-b 已完成（原创公开/私密笔记、采集唯一与作者快照、退款后本人管理例外、逻辑删除和管理员隐藏笔记）。下一任务 S13-c。新工程目录D:\Develop\JAVA\IdeaProjects\job-bootcamp；旧工程tjxt-ai-master只读参考。

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

前置：Java 21、Node 22、MySQL 8.0（本机 8.0.34）。数据库 `bc_course`、`bc_user`、`bc_learning`、`bc_trade`、`bc_marketing`、`bc_assignment` 和 `bc_interaction` 需要预先创建：

```sql
CREATE DATABASE bc_course CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_user CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_trade CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_marketing CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_assignment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE bc_interaction CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
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

学生端 <http://localhost:15173/>，管理端 <http://localhost:15174/>，网关 <http://127.0.0.1:18080/>，课程服务健康 <http://127.0.0.1:18082/actuator/health>，账号服务健康 <http://127.0.0.1:18081/actuator/health>，学习服务健康 <http://127.0.0.1:18083/actuator/health>，交易服务健康 <http://127.0.0.1:18084/actuator/health>，作业服务健康 <http://127.0.0.1:18085/actuator/health>，互动服务健康 <http://127.0.0.1:18086/actuator/health>，营销服务健康 <http://127.0.0.1:18087/actuator/health>。脚本启动网关、课程、账号、学习、交易、营销、作业、互动八个后端服务与两个前端；停止时只结束自己记录的 PID，不按端口杀进程。运行日志在 `.local-run/logs`。

当前前端 Vite 的 `/api` 已指向网关 18080，`/actuator` 仍直连 18082 用于首页健康展示；网关的受保护接口需要 access token，内部 `/int/v1/**` 不对外暴露。网关本地使用静态路由，Nacos/Redis 尚未接入。

学生端登录页 <http://localhost:15173/login>，课程页 `/courses`、路线页 `/routes`、我的课程 `/me/courses`、我的路线 `/me/routes`、活动页 `/activities`、我的券 `/me/coupons`、学习页 `/learning/courses/:id`（含章自测/代码题入口）、课程问答 `/courses/:id/questions`、课程公开笔记 `/courses/:id/notes`、笔记详情 `/notes/:id`、我的笔记 `/me/notes`、正式作业页 `/assignments/:id`、路线福利页 `/routes/:id/benefits`、福利题作答页 `/benefit-assignments/:id`、提交详情 `/submissions/:id`、确认订单 `/checkout?courseId=:id`（路线用 `/checkout?productType=ROUTE&productId=:id`）、订单列表 `/orders`、订单详情 `/orders/:id`、模拟支付 `/orders/:id/pay`、退款申请 `/orders/:id/refund`、退款申请状态 `/refund-applications/:requestId`、退款单详情 `/refunds/:id`、签到页 `/sign-in`、积分页 `/me/points`、月榜页 `/boards`；管理端登录页 <http://localhost:15174/login>，分类/课程/路线/教师分配/媒体管理在 `/admin/categories`、`/admin/courses`、`/admin/routes`、`/admin/teachers`、`/admin/media`，优惠活动与券规则在 `/admin/activities`，开发支付场景在 `/admin/trades`，异常退款对账在 `/admin/refunds`，正式作业题目在 `/admin/assignments`，教师学生作答只读记录在 `/teacher/submissions`，教师福利题页在 `/teacher/benefits`，教师答疑在 `/teacher/questions`，负责课程公开笔记在 `/teacher/notes`。演示账号（虚构）：student01～03、teacher01～02、admin01，默认密码 `Demo@123456`，可用 `DEMO_PASSWORD` 覆盖。首次启动账号服务会在 `.local-run/jwt` 生成开发用 RS256 密钥；生产必须通过 `JWT_PRIVATE_KEY_PATH`/`JWT_PUBLIC_KEY_PATH` 提供并关闭自动生成。内部服务凭据使用 `INTERNAL_TOKEN_KEY`，本地有 dev 占位值，生产必须注入真实密钥。演示目录包含 Java 分类树、免费课程“Java 入门体验”（已上架）和付费课程“Java 并发实战”（草稿）；路线由管理员页面或验收脚本创建。演示媒体 `media/demo/flower.mp4` 为 MDN CC0 示例视频（5.055 秒），管理端媒体页可导入、校验并生成 120 秒管理员预览地址；`DEMO_MEDIA_ROOT`、`MEDIA_SIGNING_KEY` 可覆盖本地默认值。腾讯云 VOD 默认关闭，需配置 `VOD_ENABLED`、`VOD_APP_ID`、`VOD_SECRET_ID`、`VOD_SECRET_KEY`、`VOD_URL_KEY`、`VOD_PCFG` 后才走真实适配器；无凭据时导入媒体标记为异常，不伪装可用。学生积分接口为 `GET /api/v1/me/points`、`GET /api/v1/me/points/ledger`、`POST/GET /api/v1/sign-ins`，月榜为 `GET /api/v1/boards/monthly?month=YYYY-MM&page=1&pageSize=20`，由网关转发到营销服务；签到和积分仅面向学生，月榜登录后可看。学习权益接口为 `GET /api/v1/me/courses`、`GET /api/v1/me/routes`、`POST /api/v1/courses/{id}/claim`、`GET /api/v1/claims/{id}`，由 learning 服务（18083）提供；免费领取在课程库受理后写 `course.free-claim-accepted.v1` outbox，经 Rabbit 由 learning 建立 FREE 来源，再回传 `learning.free-claim-completed.v1` 更新 claim 状态。播放接口为 `POST /api/v1/lessons/{id}/play-authorizations`，进度为 `PUT /api/v1/learning/lessons/{id}/progress`，只接收 position；课程学习目录为 `GET /api/v1/learning/courses/{id}`；首次完成经 `learning.lesson-completed.v1` 到营销消费。学生端已有我的课程和学习页面，播放 DEMO 视频并显示进度/首次完成提示。交易接口为 `POST /api/v1/trade/quotes`、`GET /api/v1/trade/quotes/{id}`、`POST /api/v1/orders`（可选 `Idempotency-Key`）、`GET /api/v1/orders`、`GET /api/v1/orders/{id}`，由 trade 服务（18084）提供；报价支持 `COURSE` 与 `ROUTE`。S07-a 路线报价取路线内部快照和每门课最早 ACTIVE 来源，已拥有课程按套餐分摊价抵扣，订单只保存本次新购付费课，取消会释放 `purchase_hold`/`deduction_link`；全部课程已拥有返回 `ALREADY_OWNED`，不创建零元订单。S07-b 已开放路线支付：有效回调提交 hold/deduction_link，learning 建立 `ROUTE_ORDER`、`route_entitlement`、`grant_route_member`，免费成员建立独立 FREE 来源；后续路线新增课程按新 epoch 的 joinedAt 补发，移除只关闭 epoch、不撤销已有 grant。S06-b 增加 `POST /api/v1/orders/{id}/payment`（当前仅 `MOCK`）、`POST /api/v1/orders/{id}/cancel`、`POST /callbacks/payments/{channel}`（HMAC 签名）、`GET /api/v1/refunds/{id}` 和开发场景 `POST /api/v1/dev/payments/{id}/scenario`（仅 ADMIN，默认关闭，需 `PAYMENT_MOCK_SCENARIO_ENABLED=true`）；S06-c 增加 `POST /api/v1/orders/{id}/payment/mock-pay`（学生固定 MOCK 成功动作）和 `GET /api/v1/admin/orders`（ADMIN 运营列表）；`payment_request` 不可变，`payment` 只记录确认的收款流水，`channel_receipt` 保存回调回执；订单 `PAID` 与 `fulfillment_state=PENDING/RUNNING/DONE` 分离，trade outbox 发 `trade.payment-confirmed.v1`，learning 建 `COURSE_ORDER` 来源并回传 `learning.order-granted.v1`。S08-c 起确认订单页默认请求 AUTO 报价并展示逐券分摊，支持切换手选最多 3 张或不用券；下单把报价中确认的券 ID 精确提交，营销 `coupon_lock` 一次性锁定全部券，支付成功后核销，取消/到期释放；订单详情展示逐券基数、优惠和课程分摊。营销按宽限期扫描 LOCKED 记录，通过交易内部状态接口补齐核销或释放。S09-a 增加学生退款申请 `POST /api/v1/orders/{id}/refund-applications` 与 `GET /api/v1/refund-applications/{requestId}`，由 learning 在 user_guard 内分配序列、保存购买时固定小节清单与完成快照并冻结本订单来源；交易内部 `GET /int/v1/orders/{id}/refund-basis`、`POST /int/v1/refunds/qualify` 校验 7 天窗口、≤20%、订单快照和抵扣依赖，待付依赖无渠道单时自动关闭。S09-b 增加 `POST /int/v1/refunds/start`、`POST /int/v1/sources/revoke|restore` 和 `POST /api/v1/admin/refunds/{id}/reconcile`；MOCK 可用 `POST /api/v1/dev/refunds/{orderId}/scenario` 切换 UNKNOWN/COMPLETED/CLOSED/FAILED，渠道 UNKNOWN 只能查询、不能重复退款。S09-c 增加 `GET /api/v1/refunds/by-order/{orderId}`、`GET /api/v1/admin/refunds`，以及学生退款申请/状态/详情和管理端异常退款对账页面。S10-a 新增 assignment 服务（18085、bc_assignment），管理端 `/admin/assignments` 配置章节选择题/课程代码题草稿、发布不可变版本和下架。S10-b 增加 `POST /api/v1/assignments/{id}/attempts`、`GET /api/v1/attempts/{id}`、`PUT /api/v1/attempts/{id}/draft`、`POST /api/v1/attempts/{id}/submissions`、提交历史和 review API；选择题按 attempt 固定版本判分，代码题进入 mock 异步点评并受单学生 active task 约束，`POST /api/v1/dev/assignments/mock-review` 仅本地 mock + ADMIN。S10-c 增加 `GET /api/v1/assignments/{id}/progress`（只统计当前版本最近/最高成绩并返回未提交 attempt）、提交详情中的选择题 `correctAnswers`/`analysis`，学生 `/assignments/:id` 选择题与 CodeMirror 代码 slot 编辑、2 秒草稿、冲突提示和 `/submissions/:id` 解析/参考实现/AI 点评轮询；教师只读接口为 `GET /api/v1/teacher/courses/{courseId}/assignments`、`GET /api/v1/teacher/courses/{courseId}/submissions`、`GET /api/v1/teacher/submissions/{id}`，每次经课程内部 `POST /int/v1/teachers/scope` 校验当前分配，学生与管理员默认 403。S11-a 增加 `ai_attempt` 调用记录与正式 AI worker：代码提交后发送 `assignment.review-requested.v1` 唤醒 Rabbit 队列 `bc.assignment.review-requested.v1`，扫描兜底；worker 用租约领取/续租，完成必须带 leaseToken，租约过期可恢复，10 分钟总期限或 6 次调用预算耗尽时任务 FAILED 并释放 `ai_user_slot`。本地默认 `AI_MODE=mock`；需要 MQ 唤醒时设 `AI_WAKEUP_ENABLED=true`，`AI_MOCK_POLL_MS` 控制扫描间隔，`AI_MOCK_DELAY_MS`/`AI_LEASE_SECONDS`/`AI_LEASE_RENEW_MS` 用于验收租约。真实 Qwen/DeepSeek 由 S11-b 接入：设 `AI_MODE=http`、`QWEN_API_KEY`/`DEEPSEEK_API_KEY`（外置、不入仓库），`QWEN_BASE_URL`/`DEEPSEEK_BASE_URL`/`QWEN_MODEL`/`DEEPSEEK_MODEL` 可覆盖；Qwen 不可请求时本轮切 DeepSeek，格式错误只修同供应商一次，400 不重试，最多 6 次出站，单次 HTTP 连接 5 秒/读取 45 秒。没有真实账号时保持默认 `AI_MODE=mock`；`tests/acceptance/s11b-ai-http-api.sh` 用本地 OpenAI 兼容桩验证协议分支，真实账号与计费标 NOT_RUN。S11-c 的点评状态只通过 `GET /api/v1/submissions/{id}/review` 读取，重复轮询不创建任务、不调用供应商；失败只返回 `safeErrorCode` 通用类别。学生 `/submissions/:id` 显示状态轨道、自动刷新、完成/失败面板和 MOCK/外部 AI 标识，教师 `/teacher/submissions` 只读同一反馈；没有模型选择、独立重试或改分入口，失败后需返回作业重新作答形成新任务。S12-a 增加教师福利题 API：`/api/v1/teacher/benefit-assignments` 的草稿、列表、详情、发布/下架/删除；类型为 BENEFIT_CHOICE（严格一道选择）或 BENEFIT_CODE，课程和 1～20 条路线必须同时属于教师当前分配，路线需 ONLINE 且包含该课程。首次发布在作业库事务内绑定每条路线当前 epoch，内容版本不复绑；路线移除后重新加入形成新 epoch 时，旧题保留旧 epoch，新范围必须另建福利题。管理员正式作业接口与学生正式作业列表均不包含福利题。S12-b 增加学生路线福利接口：`GET /api/v1/routes/{routeId}/benefit-assignments?courseId=`、`GET /api/v1/benefit-assignments/{id}`、`POST .../attempts`、`.../progress`、`.../submissions`；学习内部 `POST /int/v1/access/benefit` 同时校验课程有效 grant 和至少一条路线在题目绑定 epoch 上的 grant_route_member。历史 epoch 让移除课程前购买的老学员保留访问，移除后购买的新学员没有旧 epoch 资格；来源冻结/撤销立即拒绝读取和提交，题目下架拒绝新 attempt 但历史提交可读，教师调离后读写均 403。当前每次读写即时验权，未单独签发 5 秒 operationPermit。S12-c 增加教师 `/teacher/benefits` 页面、学生路线详情“查看福利”与 `/routes/:id/benefits` 列表；福利题作答复用 `/benefit-assignments/:id` 工作台（选择题/CodeMirror、草稿、历史、代码 AI 点评），提交详情和教师学生作答页支持福利题解析/参考实现/反馈。S13-a 新增 interaction 服务（18086、bc_interaction）与问答 API：`/api/v1/courses/{id}/questions`、`/api/v1/questions/{id}` 及 answers/comments 子资源；学生需课程权益，教师需负责课程并在回答中显示 TEACHER 身份。匿名问题返回“匿名学员”且 authorId 为空、mine 仅用于本人管理；问题删除/隐藏后整线程 404，回答/评论逻辑删除后从公开列表移除。管理员 `POST /api/v1/admin/content/{type}/{id}/hide` 必须填 reason 并写审计，`GET /api/v1/admin/questions/{id}/author?reason=` 需 anonymous:trace 权限；管理员不默认通过学生接口读取问答。S13-b 增加笔记 API：`/api/v1/courses/{id}/notes`、`/api/v1/me/notes`、`/api/v1/notes/{id}` 与 `/collect`；原创可公开/私密，采集副本唯一且强制私人，保存原文作者快照，原文更新/删除不覆盖副本。`/me/notes` 与本人笔记读写不要求当前课程权益，退款后仍可管理；无权益不能新增、采集或访问他人公开笔记。逻辑删除后公开列表移除、owner 列表保留 state；管理员 `POST /admin/content/NOTE/{id}/hide` 必填 reason 并审计。S13-c 增加回答/评论点赞 `PUT/DELETE /api/v1/likes/{type}/{id}`：每人每条唯一记录，重复点赞和取消幂等，不能自赞，隐藏/删除目标 404，无额外积分；回答/评论返回 `likeCount`/`liked`。学生端新增课程问答、课程公开笔记、笔记详情与我的笔记页面；教师端新增 `/teacher/questions` 负责课程答疑（教师徽标）与 `/teacher/notes` 公开笔记只读工作区。S14-a 增加教师评优 `PUT/DELETE /api/v1/teacher/notes/{id}/excellent`：只能评负责课程公开未隐藏的原创笔记，锁内容版本，同一笔记最多一条奖励与一个 `interaction.note-excellent.v1` 事件，营销按 `(EXCELLENT,noteId)` 幂等发 5 分；多教师/重复评优不重发，普通取消不扣分，作者修改或改私密自动取消展示但保留原奖励版本。评优需要 Rabbit outbox 投递积分事件。S14-b 增加管理员违规撤回 `POST /api/v1/admin/notes/{id}/revoke-reward`（权限 `points:revoke`，必填原因）：撤回奖励事实并写 `REVOKE_EXCELLENT` 审计和 `interaction.reward-revoked.v1`；营销扣回 `min(可用积分,5)`，不足部分记待扣回债务，原获奖月份月榜减 5 且不低于 0；撤回先于奖励时先存 pending，奖励到达后同账户锁内先奖后撤净 0。演示债务/历史撤回使用直接数据库夹具，反序使用默认关闭的 `POINTS_TEST_API_ENABLED=true` dev 夹具。学习服务停机时事件留在 Rabbit 队列，恢复后补发；晚于到期时间或重复/额外收款不发权益，自动创建 `SYSTEM_RETURN`。默认 `OUTBOX_TRANSPORT=none` 时发权事件只落库待投递，需按下方 Rabbit 步骤启动。确认订单、支付页、订单页与管理场景面板属 S06-c；真实支付渠道未配置。Redis 投影 key 使用 `bc:dev:` 前缀，删除后可自动从 MySQL 重建；停机时月榜回退 MySQL。积分/签到验收需要临时设置 `POINTS_TEST_API_ENABLED=true` 才注册 `/dev/points/**` 测试夹具，默认 false，生产不暴露加分接口。RabbitMQ 3.13.7 与 Erlang/OTP 26.2.5.16 已在本机便携目录中，可用 `scripts/start-rabbit.ps1`/`stop-rabbit.ps1` 启停；数据目录隔离在 `.local-run/rabbitmq`，vhost `/job-bootcamp-dev`（guest/guest）。`OUTBOX_TRANSPORT` 默认 `none`，outbox 行保持待投递并记录 `TRANSPORT_DISABLED`；以 `OUTBOX_TRANSPORT=rabbit`、`OUTBOX_RELAY_ENABLED=true` 启动后，课程免费领取事件走 `bc.domain.v1` topic exchange，`bc.learning.course-free-claim.v1` 与 `bc.course.learning-free-claim.v1` 队列完成双向事件链；营销投影队列 `bc.marketing.projection.v1` 也已实测。接口验收入口：`tests/acceptance/s02a-auth-api.sh`、`s02b-gateway-api.sh`、`s03a-catalog-api.sh`、`s03b-route-api.sh`、`s03c-media-api.sh`、`s04a-points-api.sh`、`s04b-sign-board-api.sh`、`s05a-entitlement-api.sh`、`s05b-progress-api.sh`、`s06a-order-api.sh`、`s06b-payment-api.sh`、`s06c-ui.mjs`、`s07a-route-purchase-api.sh`、`s07b-route-payment-api.sh`、`s07c-ui.mjs`、`s08a-coupon-api.sh`、`s08a-ui.mjs`、`s08b-pricing-api.sh`、`s08c-coupon-order-api.sh`、`s08c-ui.mjs`、`s09a-refund-intent-api.sh`、`s09b-refund-channel-api.sh`、`s09c-refund-pages-api.sh`、`s09c-ui.mjs`、`s10a-assignment-admin-api.sh`、`s10a-ui.mjs`、`s10b-assignment-attempt-api.sh`、`s10c-assignment-pages-api.sh`、`s10c-ui.mjs`、`s11a-ai-worker-api.sh`、`s11a-scan-fallback.sh`、`s11b-ai-http-api.sh`、`s11c-review-state-api.sh`、`s11c-ui.mjs`、`s12a-benefit-assignment-api.sh`、`s12b-benefit-access-api.sh`、`s12c-ui.mjs`、`s13a-question-api.sh`、`s13b-note-api.sh`、`s13c-likes-api.sh`、`s13c-ui.mjs`、`s14a-excellent-note-api.sh`、`s14b-reward-revocation-api.sh`；浏览器验证记录见 `tests/results/S01-验证记录.md`、`S02-验证记录.md`、`S03-验证记录.md`、`S04-验证记录.md`、`S05-验证记录.md`、`S06-验证记录.md`、`S07-验证记录.md`、`S08-验证记录.md`、`S09-验证记录.md`、`S10-验证记录.md`、`S11-验证记录.md`、`S12-验证记录.md`、`S13-验证记录.md`、`S14-验证记录.md`（每个阶段一份，小任务内容合并在同一文件）。

## 给开发模型的启动指令

> 按README与AGENTS阅读本项目文档，本次仅执行任务书指定的一个小任务（当前下一任务 S13-c）。遵守已定需求和技术契约，不修改旧项目、不提前实现后续模块。完成后报告实际验证结果、关键设计及启动步骤，更新任务进度并停止，等待我确认继续；未执行的验证明确标记。

之后可指定“继续S06-a”等编号。缺少真实云凭据时完成显式模拟路径，真实联调另标NOT_RUN；文档校验不等于应用已运行通过。
