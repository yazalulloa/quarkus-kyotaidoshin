package com.yaz.kyotaidoshin.core.service;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptTableItem;
import com.yaz.kyotaidoshin.api.rest.ReceiptController;
import com.yaz.kyotaidoshin.api.rest.ReceiptViewController.Templates;
import com.yaz.kyotaidoshin.core.bean.ServerSideEventHelper;
import com.yaz.kyotaidoshin.core.service.mailer.MailerService;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import com.yaz.kyotaidoshin.util.DateUtil;
import com.yaz.kyotaidoshin.util.ZipUtility;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.runtime.ExecutorRecorder;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.unchecked.Unchecked;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.mail.MailAttachment;
import io.vertx.ext.mail.MailMessage;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.ws.rs.sse.Sse;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ReceiptFileService {

  private final Map<String, Cancellable> cancellableMap = new HashMap<>();
  private final Map<String, ProgressUpdate> latestMsg = new HashMap<>();

  private final Vertx vertx;
  private final CalculateReceiptService calculateReceiptService;
  private final EncryptionService encryptionService;
  private final MailerService mailerService;
  private final Sse sse;
  private final ServerSideEventHelper serverSideEventHelper;
  private final ReceiptService receiptService;
  private final Event<ProgressUpdate> progressUpdateEvent;
  private final I18NService i18NService;


  @Builder
  public record SendReceiptRequest(
      String key,
      String clientId,
      String subject,
      String msg,
      Set<String> apts,
      String language
  ) {

  }

  @Builder(toBuilder = true)
  public record AptFile(
      Path path,
      String number,
      String name,
      Set<String> emails) {

  }

  @Data
  @Builder
  public static final class ProgressUpdate {

    private String clientId;
    private int counter;
    private int size;
    private String building;
    private String month;
    private String date;
    private String apt;
    private String aptName;
    private String from;
    private String to;
    private String error;
    private ReceiptTableItem item;
    private boolean finished;

  }

  public void cancel(String clientId) {
    final var cancellable = cancellableMap.get(clientId);
    if (cancellable != null) {
      final var message = i18NService.getMessage("main.description.cancelled");
      var progressUpdate = latestMsg.get(clientId);
      cancellable.cancel();

      if (progressUpdate != null) {
        progressUpdate.setFinished(true);
        progressUpdate.setError(message);
        progressUpdate.setApt(null);

      } else {
        progressUpdate = ProgressUpdate.builder()
            .clientId(clientId)
            .error(message)
            .finished(true)
            .build();
      }

      progressUpdateEvent.fireAsync(progressUpdate);
    }

  }

  public void sendReceipt(SendReceiptRequest request) {
    final var keys = encryptionService.decryptObj(request.key(), Receipt.Keys.class);

    final var progressUpdate = ProgressUpdate.builder()
        .clientId(request.clientId())
        .build();


    i18NService.setLanguage(request.language());
    calculateReceiptService.setLanguage(request.language());
    final var cancellable = calculateReceiptService.calculate(keys.buildingId(), keys.id())
        .flatMap(Unchecked.function(receipt -> {
          progressUpdate.setBuilding(receipt.building().id());
          progressUpdate.setMonth(receipt.monthStr());
          progressUpdate.setDate(receipt.date().toString());

          if (receipt.building().emailConfigId() == null) {
            throw new IllegalArgumentException("No email config found for building " + receipt.building().id());
          }

          final var path = Paths.get("tmp", "receipts", receipt.building().id(), String.valueOf(receipt.id()),
              UUID.randomUUID().toString());
          Files.createDirectories(path);
          final var fileUnis = new ArrayList<Uni<AptFile>>();

          receipt.apartmentRecords()
              .forEach(apartmentRecord -> {

                if ((request.apts != null && !request.apts.isEmpty() && !request.apts.contains(
                    apartmentRecord.apartment().number()))
                    || apartmentRecord.apartment().emails() == null
                    || apartmentRecord.apartment().emails().isEmpty()) {
                  return;
                }

                final var fileUni = fileUni(Templates.apt(receipt, apartmentRecord),
                    path.resolve(apartmentRecord.apartment().number() + ".pdf").toFile())
                    .map(file -> AptFile.builder()
                        .path(file.toPath())
                        .number(apartmentRecord.apartment().number())
                        .name(apartmentRecord.apartment().name())
                        .emails(apartmentRecord.apartment().emails())
                        .build());

                fileUnis.add(fileUni);
              });

          final var subject = Optional.ofNullable(request.subject())
              .orElse("AVISO DE COBRO") + " %s %s Adm. %s APT: %s";

          final var counter = new AtomicInteger();
          progressUpdate.setCounter(counter.get());
          progressUpdate.setSize(fileUnis.size());
          progressUpdateEvent.fireAsync(progressUpdate);

          return Uni.join().all(fileUnis)
              .andFailFast()
              .toMulti()
              .flatMap(Multi.createFrom()::iterable)
              .map(aptFile -> {

                return vertx.fileSystem().open(aptFile.path().toFile().getAbsolutePath(),
                        new OpenOptions().setRead(true).setCreate(false))
//                    .flatMap(af -> af.toMulti()
//                        .map(io.vertx.mutiny.core.buffer.Buffer::getDelegate)
//                        .onTermination().call((r, f) -> af.close())
//                        .collect().in(Buffer::buffer, Buffer::appendBuffer))
                    .flatMap(asyncFile -> {

                      return asyncFile.size().flatMap(size -> {

                            final var mailMessage = new MailMessage();
                            mailMessage.setTo(new ArrayList<>(aptFile.emails()));
                            mailMessage.setSubject(
                                subject.formatted(receipt.monthStr(), receipt.year(), receipt.building().name(),
                                    aptFile.number()));
                            mailMessage.setText(Optional.ofNullable(request.msg()).orElse("AVISO DE COBRO"));
                            final var attachment = MailAttachment.create()
                                .setName(aptFile.path().toFile().getName())
                                .setContentType("application/pdf")
                                .setSize(Math.toIntExact(size))
                                .setStream(asyncFile.getDelegate());
                            mailMessage.setAttachment(attachment);

                            progressUpdate.setApt(aptFile.number());
                            progressUpdate.setAptName(aptFile.name());
                            progressUpdate.setFrom(mailMessage.getFrom());
                            progressUpdate.setTo(String.join(",", mailMessage.getTo()));
                            progressUpdateEvent.fireAsync(progressUpdate);

                            return mailerService.send(receipt.building().emailConfigId(), mailMessage)
                                .invoke(v -> {
                                  progressUpdate.setCounter(counter.incrementAndGet());
                                  progressUpdateEvent.fireAsync(progressUpdate);
                                });
                          })
                          .onTermination()
                          .call(asyncFile::close);
                    });

              })
              .collect()
              .asList()
              .flatMap(list -> {
                return Uni.join().all(list)
                    .usingConcurrencyOf(1)
                    .andFailFast()
                    .replaceWithVoid();
              })
              .flatMap(v -> {
                final var lastSent = DateUtil.utcLocalDateTime();
                final var item = ReceiptTableItem.builder()
                    .key(request.key())
                    .item(receipt.receipt().toBuilder()
                        .sent(true)
                        .lastSent(lastSent)
                        .build())
                    .cardId(keys.cardId())
                    .sentInfoOutOfBounds(true)
                    .build();

                progressUpdate.setItem(item);
                progressUpdateEvent.fireAsync(progressUpdate);
                return receiptService.updateLastSent(receipt.id(), lastSent)
                    .replaceWithVoid();
              })
              .onTermination()
              .call(() -> vertx.fileSystem().deleteRecursive(path.toString(), true));
        }))
        .runSubscriptionOn(ExecutorRecorder.getCurrent())
        .onTermination()

        .invoke(() -> {
          cancellableMap.remove(request.clientId());
          latestMsg.remove(request.clientId());

        })

        .subscribe()
        .with(v -> {
              progressUpdate.setFinished(true);
              progressUpdateEvent.fireAsync(progressUpdate);
            },
            throwable -> {
              log.error("Error sending receipt", throwable);
              progressUpdate.setError(throwable.getMessage());
              progressUpdate.setFinished(true);
              progressUpdateEvent.fireAsync(progressUpdate);
            });

    cancellableMap.put(request.clientId(), cancellable);
  }

  public Uni<Path> zip(Receipt.Keys keys) {
    return calculateReceiptService.calculate(keys.buildingId(), keys.id())
        .flatMap(Unchecked.function(receipt -> {

          final var path = Paths.get("tmp", "receipts", receipt.building().id(), String.valueOf(receipt.id()),
              UUID.randomUUID().toString());
          Files.createDirectories(path);
          final var fileUnis = new ArrayList<Uni<File>>();

          fileUnis.add(fileUni(Templates.building(receipt), path.resolve(receipt.building().id() + ".pdf").toFile()));

          receipt.apartmentRecords().forEach(apartmentRecord -> {
            fileUnis.add(fileUni(Templates.apt(receipt, apartmentRecord),
                path.resolve(apartmentRecord.apartment().number() + ".pdf").toFile()));
          });

          return Multi.createFrom().iterable(fileUnis)
              .onItem()
              .transformToUni(uni -> uni)
              .merge()
              .collect()
              .asList()
              .map(Unchecked.function(files -> {
                final var zipFileName = "%s_%s_%s.zip".formatted(receipt.building().id(),
                    receipt.monthStr().toUpperCase(), receipt.date());
                final var finalPath = path.resolve(zipFileName);
                final var zipFile = finalPath.toFile();
                ZipUtility.zip(files, zipFile);
                return finalPath;
              }))
              .onTermination()
              .invoke(() -> {
                vertx.setTimer(Duration.ofSeconds(30).toMillis(), l -> {
                  vertx.getDelegate().fileSystem().deleteRecursive(path.toString(), true, ignore -> {
                    if (ignore.failed()) {
                      log.error("Error deleting path {}", path, ignore.cause());
                    }
                  });
                });
              });

        }));
  }

  private Uni<File> fileUni(TemplateInstance templateInstance, File file) {
    return templateInstance.createUni()
        .flatMap(str -> vertx.executeBlocking(() -> {
          final var builder = new PdfRendererBuilder();
          builder.withProducer("kyotaidoshin");
//          final var baseURI = uriInfo.getBaseUri().toString();
          final var baseURI = "";
          builder.withHtmlContent(str, baseURI);
          final var out = new FileOutputStream(file);
          builder.toStream(out);
          try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
            renderer.createPDF();
          }
          return file;
        }));
  }


  public void progressUpdateEvent(@ObservesAsync ProgressUpdate progressUpdate) {
    latestMsg.put(progressUpdate.getClientId(), progressUpdate);
    ReceiptController.Templates.sendReceiptsProgressUpdate(progressUpdate).createUni()
        .subscribe()
        .with(data -> {
          final var sseEvent = sse.newEventBuilder()
              .name("receipt-progress")
              .data(data)
              .build();

          serverSideEventHelper.sendSseEvent(progressUpdate.getClientId(), sseEvent);

          if (progressUpdate.isFinished()) {
            final var closeEvent = sse.newEventBuilder()
                .name("receipt-progress-close")
                .data(data)
                .build();
            serverSideEventHelper.sendSseEvent(progressUpdate.getClientId(), closeEvent);
            serverSideEventHelper.close(progressUpdate.getClientId());
            latestMsg.remove(progressUpdate.getClientId());
          }

        }, throwable -> {
          log.error("Error sending ProgressUpdate {}", progressUpdate, throwable);
        });


  }

}
