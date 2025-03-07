package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.api.domain.response.rate.RateHistoricProgressUpdate;
import com.yaz.kyotaidoshin.persistence.model.Rate;
import com.yaz.kyotaidoshin.persistence.model.Rate.Source;
import com.yaz.kyotaidoshin.util.FileUtil;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import com.yaz.kyotaidoshin.util.PagingProcessor;
import com.yaz.kyotaidoshin.util.PoiUtil;
import com.yaz.kyotaidoshin.util.RxUtil;
import io.micrometer.core.annotation.Timed;
import io.quarkus.scheduler.Scheduled;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.smallrye.mutiny.Uni;
import io.vertx.core.file.OpenOptions;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.rxjava3.ext.web.codec.BodyCodec;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;

@Slf4j
@ApplicationScoped
public class BcvRates {

  public static final String CHANNEL_KEY = "historic-rates-channel";
  private Disposable disposable;

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");
  private static final DateTimeFormatter LOCAL_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

  private final String url = "www.bcv.org.ve";
  private final int port = 443;
  private final Vertx vertx;
  private final WebClient client;
  private final RateService rateService;
  private final NotificationService notificationService;


  public BcvRates(Vertx vertx, RateService rateService, NotificationService notificationService) {
    this.vertx = vertx;
    this.rateService = rateService;
    this.notificationService = notificationService;

    final var options = new WebClientOptions()
        .setReuseAddress(true)
        .setReusePort(true)
        .setProtocolVersion(io.vertx.core.http.HttpVersion.HTTP_2)
        .setSsl(true)
        .setUseAlpn(true)
        .setHttp2ClearTextUpgrade(true)
        .setTrustAll(true)
        .setVerifyHost(false)
        .setKeepAlive(true)
        .setDecompressionSupported(true)
        .setMetricsName("BCV_CLIENT")
        .setShared(true)
        .setName("BCV_CLIENT");

    this.client = WebClient.create(vertx, options);
  }


  @Channel(CHANNEL_KEY)
  @OnOverflow(OnOverflow.Strategy.DROP)
  Emitter<RateHistoricProgressUpdate> emitter;

  @Scheduled(delay = 1, every = "1H")
  Uni<Void> runAsStart() {
    return saveNewBcvRate();
  }

  @Scheduled(cron = "${app.bcv_job_cron_expression}")
  Uni<Void> scheduleFixedRateTaskAsync() {
    return saveNewBcvRate();
  }

  @ConfigProperty(name = "app.bcv_job.enabled")
  boolean enabled;

  @Timed(value = "bcv_job", description = "[BCV Job] A measure of how long it takes to save new BCV rate")
  Uni<Void> saveNewBcvRate() {

    if (!enabled) {
      return Uni.createFrom().voidItem();
    }

    final var dirPath = Paths.get("tmp", "rates", "bcv", UUID.randomUUID().toString());
    final var completable = vertx.fileSystem().rxMkdirs(dirPath.toString())
        .andThen(firstPage())
        .flatMapObservable(Observable::fromIterable)
        .filter(str -> str.endsWith(".xls"))
        .firstElement()
        .flatMapCompletable(fileUrl -> {
          final var fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

          final var path = dirPath.resolve(fileName).toString();
          return download(path, fileUrl)
              .flatMapCompletable(res -> {
                final var headers = res.headers();
                final var fileInfo = new FileInfo(path, fileUrl, headers.get("ETag"), headers.get("Last-Modified"),
                    headers);

                final var pagingProcessor = new WorkbookPagingProcessor(fileInfo);

                return RxUtil.paging(pagingProcessor, ratesBeforeCheck -> {

                  return Observable.fromIterable(ratesBeforeCheck)
                      .flatMapMaybe(rate -> {
                        final var uni = rateService.exists(rate.fromCurrency(), rate.toCurrency(), rate.rate(),
                            rate.dateOfRate());

                        return RxUtil.single(uni)
                            .flatMapMaybe(b -> b ? Maybe.empty() : Maybe.just(rate));
                      })
                      .toList()
                      .map(ratesChecked -> {

                        final var rates = ratesBeforeCheck.stream()
                            .filter(rate -> ratesChecked.stream().anyMatch(
                                r -> r.fromCurrency().equals(rate.fromCurrency()) && r.toCurrency()
                                    .equals(rate.toCurrency())
                                    && r.rate().compareTo(rate.rate()) == 0 && r.dateOfRate().equals(rate.dateOfRate()))
                            ).toList();

                        return rateService.insert(rates)
                            .invoke(i -> {

                              rates.stream().filter(rate -> rate.fromCurrency().equals("USD"))
                                  .findFirst()
                                  .ifPresent(rate -> {
                                    final var msg = "Nueva tasa añadida%n%s%nFecha de la tasa: %s".formatted(
                                        rate.rate(), rate.dateOfRate());
                                    notificationService.sendNewRate(msg)
                                        .subscribeOn(Schedulers.io())
                                        .subscribe(() -> {
                                          log.debug("Notification sent");
                                        }, throwable -> {
                                          log.error("Error sending notification", throwable);
                                        });
                                  });

                            });


                      })
                      .flatMap(RxUtil::single)
                      .ignoreElement();

                });
              })
              .andThen(vertx.fileSystem().rxDelete(path));
        });

    return MutinyUtil.toUni(completable)
        .replaceWithVoid();
  }

  private Single<Set<String>> firstPage() {
    return client.get(port, url, "/estadisticas/tipo-cambio-de-referencia-smc")
        .rxSend()
        .map(response -> {
          final var html = response.bodyAsString();
          if (response.statusCode() != 200) {
            log.error("Error getting BCV rates: {}", html);
            throw new RuntimeException("Error getting BCV rates %s %s".formatted(response.statusCode(), html));
          }

          return html;
        })
        .map(this::pages);
  }

  public Single<List<String>> fileLinks() {

    return firstPage()
        .flatMap(set -> {

          return Observable.fromIterable(set)
              .filter(s -> s.startsWith("/estadisticas"))
              .map(str -> client.get(port, url, str))
              .flatMapSingle(HttpRequest::rxSend)
              .map(response -> {
                final var html = response.bodyAsString();
                if (response.statusCode() != 200) {
                  log.error("Error getting BCV rates: {}", html);
                  throw new RuntimeException("Error getting BCV rates %s %s".formatted(response.statusCode(), html));
                }

                return html;
              })
              .map(this::pages)
              .flatMap(Observable::fromIterable)
              .toList()
              .map(s -> {
                set.addAll(s);
                return set;
              });

        })
        .flatMapObservable(Observable::fromIterable)
        .filter(str -> str.endsWith(".xls"))
        .<String>toList()
        .map(List::reversed);

  }

  public Completable historicRates(String clientId) {

    final var dirPath = Paths.get("tmp", "rates", "bcv");

    final var progressUpdate = RateHistoricProgressUpdate.builder()
        .clientId(clientId)
        .build();
    final var counter = new AtomicInteger(0);
    final var parsedRates = new AtomicInteger(0);
    final var insertedRates = new AtomicInteger(0);

    final var downloadMsg = "Descargando %s ";
    final var msg = "%sTasas de cambio [Parseadas: %d] [Insertadas: %d]";

    return vertx.fileSystem().rxMkdirs(dirPath.toString())
        .andThen(fileLinks())
        .doOnSuccess(list -> {
          progressUpdate.setLeft("Found %s files".formatted(list.size()));
          progressUpdate.setSize(list.size());
          emitter.send(progressUpdate);
        })
        .flatMapObservable(Observable::fromIterable)
        .map(fileUrl -> {
          final var fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);

          final var path = dirPath.resolve(fileName).toString();
          return download(path, fileUrl)
              .doOnSubscribe(d -> {
                progressUpdate.setLeft(
                    msg.formatted(downloadMsg.formatted(fileName), parsedRates.get(), insertedRates.get()));
                emitter.send(progressUpdate);
              })
              .flatMapCompletable(res -> {
                final var headers = res.headers();
                final var fileInfo = new FileInfo(path, fileUrl, headers.get("ETag"), headers.get("Last-Modified"),
                    headers);

                final var pagingProcessor = new WorkbookPagingProcessor(fileInfo);

                return RxUtil.paging(pagingProcessor, ratesBeforeCheck -> {
                  parsedRates.addAndGet(ratesBeforeCheck.size());

                  return Observable.fromIterable(ratesBeforeCheck)
                      .flatMapMaybe(rate -> {
                        final var uni = rateService.exists(rate.fromCurrency(), rate.toCurrency(), rate.rate(),
                            rate.dateOfRate());

                        return RxUtil.single(uni)
                            .flatMapMaybe(b -> b ? Maybe.empty() : Maybe.just(rate));
                      })
                      .toList()
                      .map(ratesChecked -> {

                        return ratesBeforeCheck.stream()
                            .filter(rate -> ratesChecked.stream().anyMatch(
                                r -> r.fromCurrency().equals(rate.fromCurrency()) && r.toCurrency()
                                    .equals(rate.toCurrency())
                                    && r.rate().compareTo(rate.rate()) == 0 && r.dateOfRate().equals(rate.dateOfRate()))
                            ).toList();


                      })
//                      .doOnSuccess(l -> {
//                        final var currencies = rates.stream().map(Rate::fromCurrency).toList();
//                        log.info("Rates currencies {} {}", currencies.size(), currencies);
//                      })
                      .doOnSuccess(list -> insertedRates.addAndGet(list.size()))
                      .map(rateService::insert)
                      .flatMap(RxUtil::single)
                      .doOnSuccess(l -> {
                        progressUpdate.setLeft(
                            msg.formatted(downloadMsg.formatted(fileName), parsedRates.get(), insertedRates.get()));
                        emitter.send(progressUpdate);
                      })
                      .ignoreElement();

                }).doOnComplete(() -> {
                  final var i = counter.incrementAndGet();
                  progressUpdate.setCounter(i);
                });
              })
              .andThen(vertx.fileSystem().rxDelete(path));

        })
        .toList()
        .toFlowable()
        .flatMapCompletable(Completable::concat)
        .doOnComplete(() -> {

          progressUpdate.setLeft(msg.formatted("", parsedRates.get(), insertedRates.get()));
          progressUpdate.setEnd(true);
          emitter.send(progressUpdate);
        })
        .doOnError(throwable -> {
          progressUpdate.setLeft("Error: %s".formatted(throwable.getMessage()));
          progressUpdate.setEnd(true);
          emitter.send(progressUpdate);
        });

  }

  @Builder
  public record FileInfo(
      String path,
      String url,
      String etag,
      String lastModified,
      MultiMap headers
  ) {

  }

  public record Result(int sheets, int ratesFound, List<Rate> rates) {

  }

  private Set<String> pages(String html) {

    final var document = Jsoup.parse(html);
    final var section = document.getElementById("block-system-main");

    if (section == null) {
      log.error("Section not found");
      return Collections.emptySet();
    }

    final var links = section.getElementsByTag("a");

    return links.stream().map(element -> element.attribute("href"))
        .map(Attribute::getValue)
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }

  public Single<HttpResponse<Void>> download(String path, String requestUri) {

    final var duration = Duration.ofMinutes(10);
    return vertx.fileSystem().rxOpen(path, new OpenOptions().setWrite(true).setCreate(true))
        .flatMap(asyncFile -> {
          log.debug("Downloading file: {}", requestUri);
          return client.getAbs(requestUri)
              .timeout(duration.toMillis())
              .connectTimeout(duration.toMillis())
              .idleTimeout(duration.toMillis())
              .as(BodyCodec.pipe(asyncFile))
              .send()
              .doOnError(throwable -> log.error("BCV_HTTP_ERROR {}", requestUri, throwable))
              .doOnSuccess(s -> log.debug("Downloaded file: {}", path));
        });
  }

  public void consumeStop(@ObservesAsync Stop stop) {
    if (disposable != null && !disposable.isDisposed()) {
      disposable.dispose();
      emitter.send(RateHistoricProgressUpdate.builder()
          .clientId(stop.clientId)
          .end(true)
          .left("Stopped")
          .build());
    }
  }

  public void consumeStart(@ObservesAsync Start start) {
    if (disposable != null && !disposable.isDisposed()) {
      log.info("Already running");
      return;
    }

    disposable = historicRates(start.clientId)
        .doOnTerminate(() -> {
          disposable = null;
        })
        .subscribe(() -> {
          log.debug("Completed");
        }, throwable -> {
          log.error("Error", throwable);
        });

  }

  public record Start(String clientId) {

  }

  public record Stop(String clientId) {

  }


  @Slf4j
  @RequiredArgsConstructor
  public static class WorkbookPagingProcessor implements PagingProcessor<List<Rate>> {

    private final FileInfo fileInfo;
    private HSSFWorkbook workbook;
    private long hashFile;
    private boolean hasInit = false;

    private int sheetIndex = 0;
    private String sheetName;
    private int rowIndex = 0;

    private void init() throws IOException {
      if (hasInit) {
        return;
      }

      final var file = new File(fileInfo.path());
      hashFile = FileUtil.hashFile(file);
      workbook = new HSSFWorkbook(new FileInputStream(file));
      hasInit = true;
      sheetIndex = workbook.getNumberOfSheets() - 1;
    }

    @Override
    public Single<List<Rate>> next() {
      return Single.fromCallable(() -> {
        init();

//        final List<Rate> rates = new ArrayList<>();
//        final var sheet = workbook.getSheetAt(sheetIndex);
//        sheetName = sheet.getSheetName();
//        processSheet(sheet, rates);
//        sheetIndex--;
//        rowIndex = 0;

        final var maxSheets = 20;
        final List<Rate> rates = new ArrayList<>(20 * maxSheets);
        int i = 0;
        while (i < maxSheets && !isComplete()) {
          i++;
          final var sheet = workbook.getSheetAt(sheetIndex);
          sheetName = sheet.getSheetName();
          processSheet(sheet, rates);
          sheetIndex--;
          rowIndex = 0;
        }

//        final var currencies = rates.stream().map(Rate::fromCurrency).toList();
//        log.info("Rates currencies {} {}", currencies.size(), currencies);

        return rates;
      }).doOnError(throwable -> {
        log.error("Error processing sheet {} row {}", sheetName, rowIndex, throwable);
      });
    }

    @Override
    public boolean isComplete() {
      return sheetIndex < 0;
    }

    @Override
    public void onTerminate() {
      if (workbook != null) {
        try {
          workbook.close();
        } catch (IOException e) {
          log.error("Error closing workbook {}", fileInfo.path(), e);
        }
      }

    }

    private void processSheet(Sheet sheet, Collection<Rate> rates) {
      LocalDateTime dateOfFile = null;
      LocalDate dateOfRate = null;

      for (Row row : sheet) {
        rowIndex = row.getRowNum();
        if (row.getRowNum() == 0) {
          final var date = PoiUtil.cellToString(row.getCell(6));
          dateOfFile = (date.endsWith("M") ? LocalDateTime.from(DATE_TIME_FORMATTER.parse(date))
              : LocalDateTime.parse(date));
        }

        if (row.getRowNum() == 4) {
          final var dateStr = row.getCell(3).getStringCellValue();
          final var dateOfRateStr = dateStr.substring(dateStr.indexOf(":") + 1).trim();
          dateOfRate = LocalDate.parse(dateOfRateStr, LOCAL_DATE_FORMATTER);
        }

        if (row.getRowNum() >= 10) {

          final var currency = Optional.ofNullable(row.getCell(1)).map(Cell::getStringCellValue)
              .orElse(null);

          if (currency == null || currency.length() != 3) {
            break;
          }

          final var rate = row.getCell(6).getNumericCellValue();

          rates.add(Rate.builder()
              .fromCurrency(currency.toUpperCase())
              .toCurrency("VED")
              .rate(BigDecimal.valueOf(rate))
              .dateOfRate(dateOfRate)
              .source(Source.BCV)
              .dateOfFile(dateOfFile)
//                  .createdAt(createdAt)
              .hash(hashFile)
              .etag(fileInfo.etag())
              .lastModified(fileInfo.lastModified())
              .build());

        }
      }
    }
  }
}
