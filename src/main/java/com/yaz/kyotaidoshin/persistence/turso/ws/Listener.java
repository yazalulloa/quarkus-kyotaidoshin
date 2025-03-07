package com.yaz.kyotaidoshin.persistence.turso.ws;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import java.util.List;

public record Listener(int[] requests, Handler<AsyncResult<List<TursoResult>>> handler) {


}
