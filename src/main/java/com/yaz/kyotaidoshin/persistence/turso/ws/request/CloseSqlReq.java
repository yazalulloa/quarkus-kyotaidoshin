package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record CloseSqlReq(String type, int sqlId) implements Request {

  public static CloseSqlReq create(int sqlId) {
    return new CloseSqlReq("close_sql", sqlId);
  }

}
