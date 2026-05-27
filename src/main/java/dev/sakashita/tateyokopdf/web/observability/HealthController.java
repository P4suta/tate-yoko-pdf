package dev.sakashita.tateyokopdf.web.observability;

import dev.sakashita.tateyokopdf.observability.HealthCheck;
import dev.sakashita.tateyokopdf.observability.ShutdownState;
import io.javalin.http.Context;
import java.util.Map;
import java.util.StringJoiner;

public final class HealthController {

  private final HealthCheck healthCheck;
  private final ShutdownState shutdownState;

  public HealthController(HealthCheck healthCheck, ShutdownState shutdownState) {
    this.healthCheck = healthCheck;
    this.shutdownState = shutdownState;
  }

  /** Cheap "the process is alive" probe; no dependency calls. */
  public void liveness(Context ctx) {
    if (shutdownState.isShuttingDown()) {
      ctx.status(503).contentType("application/json").result("{\"status\":\"SHUTTING_DOWN\"}");
      return;
    }
    ctx.contentType("application/json").result("{\"status\":\"UP\"}");
  }

  /** Runs every {@link HealthCheck} probe; 503 if any DOWN or if we are shutting down. */
  public void readiness(Context ctx) {
    if (shutdownState.isShuttingDown()) {
      ctx.status(503)
          .contentType("application/json")
          .result("{\"status\":\"SHUTTING_DOWN\",\"checks\":{}}");
      return;
    }
    HealthCheck.Report report = healthCheck.run();
    ctx.status(report.status() == HealthCheck.Status.UP ? 200 : 503)
        .contentType("application/json")
        .result(toJson(report));
  }

  /** Backwards-compatible "GET /health" → readiness payload. */
  public void health(Context ctx) {
    readiness(ctx);
  }

  static String toJson(HealthCheck.Report report) {
    StringJoiner checks = new StringJoiner(",", "{", "}");
    for (Map.Entry<String, HealthCheck.Check> entry : report.checks().entrySet()) {
      HealthCheck.Check c = entry.getValue();
      String body =
          "{\"status\":\""
              + c.status()
              + "\""
              + (c.detail() == null ? "" : ",\"detail\":\"" + escape(c.detail()) + "\"")
              + "}";
      checks.add("\"" + entry.getKey() + "\":" + body);
    }
    return "{\"status\":\"" + report.status() + "\",\"checks\":" + checks + "}";
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
  }
}
