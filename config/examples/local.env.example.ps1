# Copy this file to config/local.env.ps1 and fill in real local values.
# Never commit config/local.env.ps1: it is ignored by .gitignore.
# The scripts also accept already-exported environment variables with the same names.

$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'bc_course'
$env:USER_DB_NAME = 'bc_user'
$env:LEARNING_DB_NAME = 'bc_learning'
$env:TRADE_DB_NAME = 'bc_trade'
$env:MARKETING_DB_NAME = 'bc_marketing'
$env:ASSIGNMENT_DB_NAME = 'bc_assignment'
$env:INTERACTION_DB_NAME = 'bc_interaction'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = ''
$env:DEMO_PASSWORD = 'Demo@123456'
$env:COURSE_PORT = '18082'
$env:USER_PORT = '18081'
$env:LEARNING_PORT = '18083'
$env:TRADE_PORT = '18084'
$env:MARKETING_PORT = '18087'
$env:ASSIGNMENT_PORT = '18085'
$env:INTERACTION_PORT = '18086'
$env:GATEWAY_PORT = '18080'

# 仅本地/测试：显式启用 MOCK 支付与开发场景；生产必须改为 real 且关闭场景接口。
$env:PAYMENT_MODE = 'mock'
$env:PAYMENT_MOCK_SCENARIO_ENABLED = 'true'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6379'
$env:REDIS_ENABLED = 'true'
$env:REDIS_KEY_PREFIX = 'bc:dev:'

$env:RABBIT_HOST = '127.0.0.1'
$env:RABBIT_PORT = '5672'
$env:RABBIT_USERNAME = 'guest'
$env:RABBIT_PASSWORD = 'guest'
$env:RABBIT_VHOST = '/job-bootcamp-dev'
$env:RABBIT_EXCHANGE = 'bc.domain.v1'
# none：只写 outbox 待投递；rabbit：启动 relay 真实投递。
$env:OUTBOX_TRANSPORT = 'rabbit'
$env:OUTBOX_RELAY_ENABLED = 'true'

# 内部服务凭据；本地可使用开发占位值，生产必须替换为外置强随机值。
$env:INTERNAL_TOKEN_KEY = '<local-dev-only-change-me>'
# 不配置时 user 服务会在 .local-run/jwt 生成开发密钥；生产必须显式提供。
# $env:JWT_PRIVATE_KEY_PATH = 'D:\secure\jwt-private.pem'
# $env:JWT_PUBLIC_KEY_PATH = 'D:\secure\jwt-public.pem'

$env:AI_MODE = 'mock'
$env:AI_WAKEUP_ENABLED = 'true'
$env:AI_MOCK_POLL_MS = '500'
$env:AI_MOCK_DELAY_MS = '0'
# 真实模型密钥只从本机环境变量读取，不写入仓库或日志。
# $env:QWEN_API_KEY = '<qwen-key>'
# $env:DEEPSEEK_API_KEY = '<deepseek-key>'

# DEMO 媒体目录与签名密钥；不要提交真实私密媒体。
$env:DEMO_MEDIA_ROOT = 'media/demo'
$env:MEDIA_SIGNING_KEY = '<local-dev-only-change-me>'
# 真实腾讯云 VOD 凭据未配置时保持关闭并标记 NOT_RUN。
# $env:VOD_ENABLED = 'true'
# $env:VOD_APP_ID = '<app-id>'
# $env:VOD_SECRET_ID = '<secret-id>'
# $env:VOD_SECRET_KEY = '<secret-key>'
# $env:VOD_URL_KEY = '<url-key>'
# $env:VOD_PCFG = '<pcfg>'

# 仅本地验收使用；保持 false 时 /dev 积分测试接口不会注册。
$env:POINTS_TEST_API_ENABLED = 'false'