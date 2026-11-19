# Copy this file to config/local.env.ps1 and fill in real local values.
# Never commit config/local.env.ps1: it is ignored by .gitignore.
# The scripts also accept already-exported environment variables with the same names.

$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '3306'
$env:DB_NAME = 'bc_course'
$env:USER_DB_NAME = 'bc_user'
$env:MARKETING_DB_NAME = 'bc_marketing'
$env:DB_USERNAME = 'root'
$env:DB_PASSWORD = ''
$env:COURSE_PORT = '18082'
$env:USER_PORT = '18081'
$env:MARKETING_PORT = '18087'
$env:GATEWAY_PORT = '18080'

# 仅本地验收使用；保持 false 时 /dev 积分测试接口不会注册。
$env:POINTS_TEST_API_ENABLED = 'false'