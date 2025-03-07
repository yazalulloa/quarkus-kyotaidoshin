package com.yaz.kyotaidoshin.core.service.domain;

import java.io.File;
import lombok.Builder;

@Builder
public record FileResponse(
    String fileName,
    File path,
    String contentType,
    boolean deleteFile,
    long fileSize) {


}