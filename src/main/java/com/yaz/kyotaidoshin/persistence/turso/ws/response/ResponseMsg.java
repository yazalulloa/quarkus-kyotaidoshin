package com.yaz.kyotaidoshin.persistence.turso.ws.response;

import com.fasterxml.jackson.databind.JsonNode;


public record ResponseMsg(String type, int requestId, JsonNode response, Error error) {

  public boolean hasError() {
    return error != null;
  }

}
