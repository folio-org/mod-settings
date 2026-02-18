package org.folio.settings.server.util;

import io.vertx.ext.web.RoutingContext;
import java.util.UUID;
import org.folio.okapi.common.XOkapiHeaders;

public class UserUtil {

  private UserUtil() {
  }

  /**
   * Get user ID from routing context headers.
   * @param ctx the routing context
   * @return the user ID, or null if not present
   */
  public static UUID getUserId(RoutingContext ctx) {
    String userIdParameter = ctx.request().getHeader(XOkapiHeaders.USER_ID);
    UUID currentUserId = null;
    if (userIdParameter != null) {
      currentUserId = UUID.fromString(userIdParameter);
    }
    return currentUserId;
  }
}
