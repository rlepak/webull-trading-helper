package org.rl.webulltradinghelper.webull;

import com.webull.openapi.core.http.exception.HttpServerException;

/** Only allowlisted diagnostic categories leave the transport boundary. */
final class WebullFailures {
    private WebullFailures() {}

    static int httpStatus(Throwable failure) {
        for (int depth = 0; failure != null && depth < 10; depth++, failure = failure.getCause()) {
            if (failure instanceof HttpServerException http) return http.getHttpStatus();
        }
        return 0;
    }

    static String category(Throwable failure) {
        for (int depth = 0; failure != null && depth < 10; depth++, failure = failure.getCause()) {
            if (failure instanceof HttpServerException http) {
                return switch (http.getHttpStatus()) {
                    case 401 -> "UNAUTHORIZED";
                    case 403 -> "FORBIDDEN_OR_NOT_ENTITLED";
                    case 429 -> "RATE_LIMITED";
                    default -> "HTTP_FAILURE";
                };
            }
        }
        return "CONNECTION_OR_PROTOCOL_FAILURE";
    }
}
