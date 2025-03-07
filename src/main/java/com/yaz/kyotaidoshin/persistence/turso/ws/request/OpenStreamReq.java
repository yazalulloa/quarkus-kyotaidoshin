package com.yaz.kyotaidoshin.persistence.turso.ws.request;

public record OpenStreamReq(String type, int streamId) implements Request {

  public static OpenStreamReq create(int streamId) {
    return new OpenStreamReq("open_stream", streamId);
  }

}
