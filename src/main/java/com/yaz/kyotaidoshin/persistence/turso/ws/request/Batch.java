package com.yaz.kyotaidoshin.persistence.turso.ws.request;

import java.util.List;

public record Batch(List<BatchStep> steps) {

}
