package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record OpenCursorReq(String type, int streamId, int cursorId, Batch batch) implements Request {

  public static OpenCursorReq create(int streamId, int cursorId, Batch batch) {
    return new OpenCursorReq("open_cursor", streamId, cursorId, batch);
  }
}
