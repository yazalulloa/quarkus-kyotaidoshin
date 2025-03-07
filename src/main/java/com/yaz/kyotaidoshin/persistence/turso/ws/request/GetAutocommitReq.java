package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record GetAutocommitReq(String type, int streamId) implements Request {

  public static GetAutocommitReq create(int streamId) {
    return new GetAutocommitReq("get_autocommit", streamId);
  }

}
