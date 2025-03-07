package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record BatchReq(String type, int streamId, Batch batch) implements Request {

  public static BatchReq create(int streamId, Batch batch) {
    return new BatchReq("batch", streamId, batch);
  }
}
