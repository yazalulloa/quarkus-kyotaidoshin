package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record FetchCursorReq(String type, int cursorId, long maxCount) implements Request {

  public static FetchCursorReq create(int cursorId, long maxCount) {
    return new FetchCursorReq("fetch_cursor", cursorId, maxCount);
  }

}
