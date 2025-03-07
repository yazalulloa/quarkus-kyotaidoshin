package com.yaz.kyotaidoshin.persistence.turso.ws.response;

import java.lang.Error;

public record HelloRes(String type, Error error) implements Response {

  public boolean isError() {
    return error != null;
  }
}
