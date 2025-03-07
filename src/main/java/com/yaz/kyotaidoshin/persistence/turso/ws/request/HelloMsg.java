package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record HelloMsg(String type, String jwt) {

  public static HelloMsg create(String jwt) {
    return new HelloMsg("hello", jwt);
  }

}
