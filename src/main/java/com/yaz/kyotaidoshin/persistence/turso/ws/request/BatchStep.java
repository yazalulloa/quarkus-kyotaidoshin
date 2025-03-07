package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record BatchStep(BatchCond condition, Stmt stmt) {

  public static BatchStep stmt(Stmt stmt) {
    return new BatchStep(null, stmt);
  }

}
