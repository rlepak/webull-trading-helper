## Design
Use Spring @Scheduled with fixed delay and initial delay from health-log.interval-ms,
default 300000. Enable scheduling in the existing configuration; Spring cancels the
task at shutdown. Use shared Chicago Clock for session/time ages. Ages are seconds,
with never for absent observations; do not infer success from a connected socket.
Use actual MQTT acknowledgment/disconnect state rather than worker-running state.
Use atomic interval counters in the event receiver and volatile last receive/poll
success instants. Reporter drains interval counters each summary; recorder written
count is cumulative (not a claim of durable acknowledgment). Disabled, failed,
stopped and running recording states remain distinct.
Move routine operational messages to DEBUG, guard event JSON rendering by DEBUG
enabled, and set root INFO plus health reporter INFO in application.properties.
Existing SDK suppression remains. Warnings/errors stay immediate. This explicitly
changes the earlier routine INFO behavior, keeping detailed logs opt-in.

Keep the Spring Boot banner and normal INFO startup logs visible. Application routine event, subscription, polling and rotation messages remain DEBUG, with MarketDataLogger explicitly WARN. The periodic health summary still starts after five minutes.

Invoke the summary on ApplicationReadyEvent as well, after normal startup logs. Keep the periodic schedule at five minutes. Synchronize summary generation so startup and scheduled reports cannot drain counters concurrently. Report actual state even if Webull connection or data arrival is still pending.
