# S05-a event retry evidence
courseId=27
[INFO] Stopping RabbitMQ...
[ OK ] RabbitMQ stopped.
claim-with-broker-down: {"code":"OK","message":"","data":{"claimId":"e570b25c-8830-44b2-a996-7a20d240bb47","courseId":"27","status":"PROCESSING","sourceId":null},"traceId":"53f47983925548dd8d26afb7dbaf17c5"}
ticket	PROCESSING	NULL
outbox	NEW	1	PUBLISH_FAILED
[INFO] Starting RabbitMQ...
[ OK ] RabbitMQ ready at 127.0.0.1:5672, vhost /job-bootcamp-dev (guest/guest).
15
outbox-event: course.free-claim-accepted.v1 delivered after broker restart; queue bc.learning.course-free-claim.v1 consumed
