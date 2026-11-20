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
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = ''
$env:COURSE_PORT = '18082'
$env:USER_PORT = '18081'
$env:LEARNING_PORT = '18083'
$env:TRADE_PORT = '18084'
$env:MARKETING_PORT = '18087'
$env:GATEWAY_PORT = '18080'

# 仅本地/测试：显式启用 MOCK 支付与开发场景；生产必须改为 real 且关闭场景接口。
$env:PAYMENT_MODE = 'mock'
$env:PAYMENT_MOCK_SCENARIO_ENABLED = 'true'
$env:REDIS_HOST = '127.0.0.1'
$env:REDIS_PORT = '6379'

# 仅本地验收使用；保持 false 时 /dev 积分测试接口不会注册。
$env:POINTS_TEST_API_ENABLED = 'false'