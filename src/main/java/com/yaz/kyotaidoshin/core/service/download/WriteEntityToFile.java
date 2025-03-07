package com.yaz.kyotaidoshin.core.service.download;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yaz.kyotaidoshin.core.service.domain.FileResponse;
import com.yaz.kyotaidoshin.util.PagingProcessor;
import com.yaz.kyotaidoshin.util.RxUtil;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class WriteEntityToFile {

  private final Vertx vertx;
  private final ObjectMapper mapper;


  public <T> Single<FileResponse> downloadFile(String fileName, PagingProcessor<List<T>> pagingProcessor) {

    return Single.defer(() -> {
//      final var tempFileName = temPath + fileName;

      final var timestamp = System.currentTimeMillis();
      final var temPath = Paths.get("tmp", String.valueOf(timestamp));

      final var fileResponseSingle = Single.defer(() -> {
        final var tempFileName = temPath.resolve(fileName).toString();
        final var fileOutputStream = new FileOutputStream(tempFileName);
        final var gzipOutputStream = new GZIPOutputStream(fileOutputStream);
        final var jsonGenerator = mapper.getFactory().createGenerator(gzipOutputStream, JsonEncoding.UTF8);

        jsonGenerator.writeStartArray();

        log.info("START WRITING FILE {}", fileName);

        return RxUtil.paging(pagingProcessor, collection -> {

              return vertx.executeBlocking(() -> {
                for (var obj : collection) {
                  mapper.writeValue(jsonGenerator, obj);
                }

                return true;
              }).ignoreElement();
            })
            .doOnComplete(jsonGenerator::writeEndArray)
            .doOnTerminate(jsonGenerator::close)
            .doOnTerminate(gzipOutputStream::close)
            .doOnTerminate(fileOutputStream::close)
            .doOnTerminate(() -> log.info("END WRITING FILE {} {}ms", fileName, System.currentTimeMillis() - timestamp))
            .toSingleDefault(FileResponse.builder()
                .fileName(fileName)
                .path(new File(tempFileName))
                .contentType("application/gzip")
                .build());
      });

      return vertx.fileSystem().rxMkdirs(temPath.toString())
          .andThen(fileResponseSingle);
    });
  }
}
