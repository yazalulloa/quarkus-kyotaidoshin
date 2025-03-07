package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record DescribeReq(String type, int streamId, String sql, int sqlId) implements Request {

  public static DescribeReq create(int streamId, String sql, int sqlId) {
    return new DescribeReq("describe", streamId, sql, sqlId);
  }

}
