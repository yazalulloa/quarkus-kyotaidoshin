package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record CloseCursorReq(String type, int cursorId) implements Request {

  public static CloseCursorReq create(int requestId) {
    return new CloseCursorReq("close_cursor", requestId);
  }

}
