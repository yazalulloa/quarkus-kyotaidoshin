package com.yaz;

import com.yaz.kyotaidoshin.api.domain.response.UserTableResponse.NotificationKey;
import com.yaz.kyotaidoshin.persistence.model.NotificationEvent.Event;
import io.vertx.core.json.Json;
import org.junit.jupiter.api.Test;

public class NotificationKeyTest {

  @Test
  void json() {
    final var notificationKey = new NotificationKey("sdf", Event.APP_SHUTTING_DOWN);
    System.out.println(Json.encode(notificationKey));
  }

}
