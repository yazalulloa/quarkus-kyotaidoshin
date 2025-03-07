package com.yaz.kyotaidoshin.api.rest;


import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.charge.ExtraChargeTableItem;
import com.yaz.kyotaidoshin.api.domain.response.debt.DebtInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.debt.DebtTableItem;
import com.yaz.kyotaidoshin.api.domain.response.expense.ExpenseCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.expense.ExpenseInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.expense.ExpenseTableItem;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.funds.ReserveFundTableItem;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptFileFormDto;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptFormDto;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptFormResponse;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptInitDto;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptInitDto.Apts;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptInitFormDto;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptTableItem;
import com.yaz.kyotaidoshin.api.domain.response.receipt.ReceiptTableResponse;
import com.yaz.kyotaidoshin.api.rest.FragmentController.Fragments;
import com.yaz.kyotaidoshin.core.bean.ServerSideEventHelper;
import com.yaz.kyotaidoshin.core.service.ApartmentService;
import com.yaz.kyotaidoshin.core.service.BuildingService;
import com.yaz.kyotaidoshin.core.service.DebtService;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.ExpenseService;
import com.yaz.kyotaidoshin.core.service.ExtraChargeService;
import com.yaz.kyotaidoshin.core.service.RateService;
import com.yaz.kyotaidoshin.core.service.ReceiptFileService;
import com.yaz.kyotaidoshin.core.service.ReceiptFileService.SendReceiptRequest;
import com.yaz.kyotaidoshin.core.service.ReceiptService;
import com.yaz.kyotaidoshin.core.service.ReserveFundService;
import com.yaz.kyotaidoshin.core.service.csv.CsvReceipt;
import com.yaz.kyotaidoshin.core.service.csv.ReceiptParser;
import com.yaz.kyotaidoshin.core.service.domain.ReceiptRecord;
import com.yaz.kyotaidoshin.persistence.domain.RateQuery;
import com.yaz.kyotaidoshin.persistence.domain.ReceiptCreateRequest;
import com.yaz.kyotaidoshin.persistence.domain.ReceiptQuery;
import com.yaz.kyotaidoshin.persistence.domain.SortOrder;
import com.yaz.kyotaidoshin.persistence.model.Debt;
import com.yaz.kyotaidoshin.persistence.model.Expense;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.Rate;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import com.yaz.kyotaidoshin.persistence.model.ReserveFund;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import com.yaz.kyotaidoshin.util.DateUtil;
import com.yaz.kyotaidoshin.util.MutinyUtil;
import com.yaz.kyotaidoshin.util.PagingJsonFile;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import com.yaz.kyotaidoshin.util.RxUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
import com.yaz.kyotaidoshin.util.TemplateUtil;
import io.quarkiverse.renarde.router.Router;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.PermissionsAllowed;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.Json;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.multipart.FileUpload;

@Slf4j
@Path("receipts")
@PermissionsAllowed(PermissionUtil.Receipts.READ)
@RequiredArgsConstructor
public class ReceiptController extends HxControllerWithUser<RenardeUserImpl> {

  private final ReceiptService receiptService;
  private final BuildingService buildingService;
  private final EncryptionService encryptionService;
  private final ReceiptParser receiptParser;
  private final RateService rateService;
  private final ExpenseService expenseService;
  private final ExtraChargeService extraChargeService;
  private final DebtService debtService;
  private final ApartmentService apartmentService;
  private final ReserveFundService reserveFundService;
  private final ReceiptFileService receiptFileService;
  private final ServerSideEventHelper serverSideEventHelper;

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/")
  public Uni<TemplateInstance> index() {
    return buildingService.ids()
        .map(buildingIds -> {
          if (isHxRequest()) {
            return concatTemplates(
                Templates.index$headerContainer(buildingIds),
                Templates.index$container()
            );
          }

          return Templates.index(buildingIds);

        });

  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> search(
      @RestQuery String lastKey,
      @RestQuery("building_input") Set<String> building,
      @RestQuery("month_input") Set<Integer> months,
      @RestQuery("date_input") String date) {
    final var nextKeys = Optional.ofNullable(lastKey)
        .map(StringUtil::trimFilter)
        .map(str -> encryptionService.decryptObj(str, Receipt.Keys.class));

    final var month = Optional.ofNullable(months)
        .stream()
        .flatMap(Set::stream)
        .filter(Objects::nonNull)
        .filter(i -> i > 0 && i < 13)
        .mapToInt(x -> x)
        .toArray();

    final var receiptQuery = ReceiptQuery.builder()
        .lastId(nextKeys.map(Receipt.Keys::id).orElse(null))
        .buildings(building)
        .month(month)
        .date(DateUtil.isValidLocalDate(date) ? date : null)
        .build();

    return receiptService.table(receiptQuery)
        .map(Templates::receipts);
  }

  @DELETE
  @Path("{keys}")
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Receipts.WRITE)
  public Uni<TemplateInstance> delete(
      @NotBlank @RestPath String keys,
      @RestForm("building_input") Set<String> building,
      @RestForm("month_input") Set<Integer> months,
      @RestForm("date_input") String date) {
    final var key = encryptionService.decryptObj(keys, Receipt.Keys.class);

    return receiptService.delete(key)
        .replaceWith(counters(building, months, date));
  }

  private Uni<TemplateInstance> counters(Set<String> building, Set<Integer> months, String date) {

    return receiptService.counters(ReceiptQuery.builder()
            .buildings(building)
            .month(Optional.ofNullable(months)
                .stream()
                .flatMap(Set::stream)
                .filter(Objects::nonNull)
                .filter(i -> i > 0 && i < 13)
                .mapToInt(x -> x)
                .toArray())
            .date(DateUtil.isValidLocalDate(date) ? date : null)
            .build())
        .map(Templates::counters);
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Uni<Response> backupUpload(@RestForm FileUpload file) {

    final var pagingJsonFile = new PagingJsonFile();
    final var single = pagingJsonFile.pagingJsonFile(1000, file.filePath().toString(), ReceiptRecord.class, list -> {

      return Observable.fromIterable(list)
          .sorted(Comparator.comparing(ReceiptRecord::receipt,
              Comparator.comparing(Receipt::date).thenComparing(Receipt::buildingId)))
//          .map(r -> r.receipt().date())
          .toList()
          /*.doOnSuccess(l -> log.info("Dates {}", l))
          .ignoreElement()*/
          .flatMapObservable(Observable::fromIterable)
          .map(receiptRecord -> {

            final var rateQuery = RateQuery.builder()
                .currencies(Set.of("USD"))
                .sortOrder(SortOrder.ASC)
                .date(receiptRecord.receipt().date().toString())
                .limit(1)
                .build();

            return RxUtil.single(rateService.list(rateQuery).map(List::getFirst))
                .map(rate -> {
                  final var receipt = receiptRecord.receipt().toBuilder()
                      .rateId(rate.id())
                      .build();
                  return ReceiptCreateRequest.builder()
                      .receipt(receipt)
                      .expenses(receiptRecord.expenses())
                      .extraCharges(receiptRecord.extraCharges())
                      .debts(receiptRecord.debts())
                      .build();
                })
                .map(receiptService::insert)
                .flatMap(RxUtil::single)
                .ignoreElement();
          })
          .toList()
          .flatMapCompletable(Completable::concat)
          ;
    }).toSingleDefault(Response.noContent().build());

    return MutinyUtil.toUni(single);
  }

  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @ActivateRequestContext
  @PermissionsAllowed(PermissionUtil.Receipts.WRITE)
  public Uni<TemplateInstance> createFromFile(@RestForm FileUpload file) {

    if (file == null) {
      return Uni.createFrom().item(Fragments.rateInfo("Archivo no encontrado"));
    }

    if (!file.fileName().endsWith(".xls")
        && !file.fileName().endsWith(".xlsx")) {
      return Uni.createFrom().item(Fragments.rateInfo("Solo se permiten archivos .xls o .xlsx"));
    }

    final var listSingle = buildingService.ids();

    final var csvReceiptSingle = receiptParser.parse(file.uploadedFile())
        .map(csvReceipt -> csvReceipt.toBuilder()
            .fileName(file.fileName())
            .build());

    final var localDate = LocalDate.now(DateUtil.VE_ZONE);

    final var rateQuery = RateQuery.builder()
        .currencies(Set.of("USD"))
        .sortOrder(SortOrder.DESC)
        .limit(10)
        .build();

    final var rateListSingle = rateService.table(rateQuery);

    final var originalLanguage = i18n.getLanguage();

    return Uni.combine().all()
        .unis(listSingle, MutinyUtil.toUni(csvReceiptSingle), rateListSingle)
        .with((buildings, csvReceipt, rates) -> {
          //log.info("Receipt parsed: {}", csvReceipt);

          final var json = Json.encode(csvReceipt);
          final var compressed = StringUtil.deflate(json);
          //final var body = encryptionService.encrypt(compressed);

          //log.info("Original {} Compressed {} Encrypted {}", json.length(), compressed.length(), body.length());

          final var fileName = file.fileName();

          final var lowerCase = fileName.toLowerCase();

          i18n.setForCurrentRequest("es");

          final var months = Arrays.stream(Month.values())
              .map(month -> {

                final var monthName = i18n.getMessage("months.number." + month.getValue()).toLowerCase();
                final var monthShort = i18n.getMessage("months.number.%s.short".formatted(month.getValue()))
                    .toLowerCase();
                final var dateStr = "%s%s".formatted(localDate.getYear(),
                    (month.getValue() < 10 ? "0" + month.getValue() : month.getValue()));

                if (lowerCase.contains(monthName) || lowerCase.contains(monthShort) || lowerCase.contains(dateStr)) {
                  return month;
                }

                return null;
              })
              .filter(Objects::nonNull)
              .map(Month::getValue)
              .collect(Collectors.toSet());

          i18n.setForCurrentRequest(originalLanguage);

          final var buildingsMatched = buildings.stream().filter(fileName::contains)
              .toList();

          return ReceiptFileFormDto.builder()
              .fileName(fileName)
              .buildingName(buildingsMatched.size() == 1 ? buildingsMatched.getFirst() : null)
              .month(months.size() == 1 ? months.iterator().next() : localDate.getMonthValue())
              .years(DateUtil.yearsPicker())
              .year(localDate.getYear())
              .receipt(csvReceipt)
              .date(localDate.toString())
              .buildings(buildings)
              .rates(rates)
              .data(compressed)
              .build();
        })
        .map(Templates::newFileDialog)
        .onFailure()
        .recoverWithItem(throwable -> {
          log.error("ERROR_PARSING_RECEIPT_FILE", throwable);
          return Fragments.rateInfo(throwable.getMessage());
        });
  }

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Receipts.WRITE)
  public Uni<Response> create(@BeanParam FileReceiptRequest request) throws IOException {

    final var month = Month.of(request.month);
    final var date = LocalDate.parse(request.date);
    //final var decrypted = encryptionService.decrypt(request.data);
    final var decompress = StringUtil.inflate(request.data);
    //final var decompress = StringUtil.decompress(decrypted);
    final var csvReceipt = Json.decodeValue(decompress, CsvReceipt.class);

    if (!ArrayUtils.contains(DateUtil.yearsPicker(), request.year)) {
      log.info("INVALID YEAR {}", request);
      return Uni.createFrom().item(Response.noContent().build());
    }

    final var rateId = encryptionService.decryptObj(request.rateInput, Rate.Keys.class).id();

    return Uni.combine().all()
        .unis(buildingService.exists(request.building), rateService.read(rateId),
            apartmentService.aptByBuildings(request.building))
        .withUni((buildingExists, rateOptional, apartments) -> {
          final var noContentUni = Uni.createFrom().item(Response.noContent().build());
          if (!buildingExists) {
            log.info("BUILDING_NOT_FOUND {}", request);
            return noContentUni;
          }

          if (rateOptional.isEmpty()) {
            log.info("RATE_NOT_FOUND {}", rateId);
            return noContentUni;
          }

          final var expenses = csvReceipt.expenses()
              .stream()
              .map(expense -> expense.toBuilder()
                  .buildingId(request.building)
                  .build())
              .toList();

          final var debts = apartments.stream()
              .map(apt -> {
                return csvReceipt.debts()
                    .stream().filter(
                        debt -> debt.aptNumber().contains(apt.number()) || apt.number().equals("0" + debt.aptNumber()))
                    .findFirst()
                    .map(debt -> debt.toBuilder()
                        .buildingId(request.building)
                        .aptNumber(apt.number())
                        .build())
                    .orElseGet(() -> Debt.builder()
                        .buildingId(request.building)
                        .aptNumber(apt.number())
                        .amount(BigDecimal.ZERO)
                        .build());
              })
              .toList();

          final var extraCharges = csvReceipt.extraCharges().stream()
              .map(extraCharge -> extraCharge.toBuilder()
                  .buildingId(request.building)
                  .build())
              .toList();

          final var receipt = Receipt.builder()
              .buildingId(request.building)
              .year(request.year)
              .month(request.month)
              .date(date)
              .rateId(rateId)
              .createdAt(DateUtil.utcLocalDateTime())
              .build();

          final var createRequest = ReceiptCreateRequest.builder()
              .receipt(receipt)
              .expenses(expenses)
              .extraCharges(extraCharges)
              .debts(debts)
              .build();

          return receiptService.insert(createRequest)
              .map(res -> {
                final var id = res.id();
                final var keys = new Receipt.Keys(receipt.buildingId(), id, null, 0);
                final var encrypted = encryptionService.encryptObj(keys);

                return Response.ok()
                    .header("HX-Redirect", Router.getURI(ReceiptController::edit, encrypted))
                    .build();
              });
        });
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Receipts.WRITE)
  public Uni<TemplateInstance> edit(@RestPath String id) {
    if (isHxRequest()) {
      return Uni.createFrom().item(concatTemplates(
          Templates.editIndex$headerContainer(id),
          Templates.editIndex$container(id)
      ));
    }

    return Uni.createFrom().item(Templates.editIndex(id));
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Receipts.WRITE)
  public Uni<TemplateInstance> form(@RestPath String id) {
    final var keys = encryptionService.decryptObj(id, Receipt.Keys.class);

    final var receiptUni = receiptService.read(keys.id())
        .map(optional -> optional.orElseThrow(() -> new IllegalArgumentException("RECEIPT_NOT_FOUND")))
        .memoize()
        .forFixedDuration(Duration.ofSeconds(3));

    final var buildingUni = buildingService.get(keys.buildingId());

    final var rateUni = receiptUni.flatMap(receipt -> rateService.read(receipt.rateId()))
        .map(optional -> optional.orElseThrow(() -> new IllegalArgumentException("RATE_NOT_FOUND")));

    final var expensesListUni = expenseService.readByReceipt(keys.id());

    final var extraChargesListUni = Uni.combine()
        .all()
        .unis(extraChargeService.by(keys.buildingId(), keys.buildingId()),
            extraChargeService.by(keys.buildingId(), String.valueOf(keys.id())))
        .with((building, receipt) -> {
          return Stream.concat(building.stream(), receipt.stream())
              .toList();
        });

    final var debtListUni = receiptUni.flatMap(
        receipt -> debtService.readByReceipt(receipt.buildingId(), receipt.id()));

    final var rateQuery = RateQuery.builder()
        .currencies(Set.of("USD"))
        .sortOrder(SortOrder.DESC)
        .limit(10)
        .build();

    final var rateListUni = rateService.table(rateQuery);

    final var reserveFundUni = reserveFundService.listByBuilding(keys.buildingId());

    return Uni.combine().all()
        .unis(receiptUni, rateUni, expensesListUni, extraChargesListUni, debtListUni, rateListUni,
            apartmentService.aptByBuildings(keys.buildingId()), buildingUni, reserveFundUni)
        .with((receipt, rate, expenses, extraCharges, debts, rates, apartments, building, reserveFunds) -> {

          final var expensesCount = expenses.size();
          final var receiptForm = ReceiptFormDto.builder()
              .key(encryptionService.encryptObj(receipt.keysWithHash()))
              .buildingName(receipt.buildingId())
              .month(receipt.month())
              .year(receipt.year())
              .years(DateUtil.yearsPicker())
              .date(receipt.date().toString())
              .rates(rates.toBuilder()
                  .selected(rate.id())
                  .lastKey(null)
                  .build())
              .build();

          final var extraChargeTableItems = extraCharges.stream()
              .map(extraCharge -> {
                final var keys1 = extraCharge.keys(keys.id());
                return ExtraChargeTableItem.builder()
                    .item(extraCharge)
                    .key(encryptionService.encryptObj(keys1))
                    .cardId(keys1.cardId())
                    .build();
              })
              .toList();

          var debtReceiptsTotal = 0;
          var debtTotal = BigDecimal.ZERO;

          final var debtTableItems = new ArrayList<DebtTableItem>();
          for (Debt debt : debts) {
            final var keys1 = debt.keys();
            final var item = DebtTableItem.builder()
                .key(encryptionService.encryptObj(keys1))
                .item(debt)
                .currency(building.debtCurrency())
                .cardId(keys1.cardId())
                .build();

            debtReceiptsTotal += debt.receipts();
            debtTotal = debtTotal.add(debt.amount());

            debtTableItems.add(item);
          }

          final var expenseTotalsBeforeReserveFunds = ConvertUtil.expenseTotals(rate.rate(), expenses);

          final var reserveFundTableItems = reserveFunds.stream()
              .map(reserveFund -> {

                final var reserveFundExpense = ConvertUtil.reserveFundExpense(expenseTotalsBeforeReserveFunds,
                    reserveFund);

                if (reserveFundExpense != null) {
                  expenses.add(reserveFundExpense.item());
                }

                final var keys1 = reserveFund.keys(receipt.id());
                return ReserveFundTableItem.builder()
                    .key(encryptionService.encryptObj(keys1))
                    .item(reserveFund)
                    .cardId(keys1.cardId())
                    .build();
              })
              .toList();

          final var expenseTotals = ConvertUtil.expenseTotals(rate.rate(), expenses);

          final var expenseTableItems = expenses.stream()
              .map(expense -> {

                final var keys1 = expense.keys();
                return ExpenseTableItem.builder()
                    .key(encryptionService.encryptObj(keys1))
                    .cardId(keys1.cardId())
                    .item(expense)
                    .build();
              })
              .toList();

          final var extraChargesTotal = extraCharges.stream()
              .map(extraCharge -> extraCharge.amount() * extraCharge.apartments().size())
              .map(BigDecimal::valueOf)
              .reduce(BigDecimal.ZERO, BigDecimal::add);

          return ReceiptInitFormDto.builder()
              .receiptForm(receiptForm)
              .apts(apartments)

              .expenseDto(ExpenseInitFormDto.builder()
                  .expensesCount(expensesCount)
                  .totalCommonExpenses(expenseTotalsBeforeReserveFunds.formatCommon())
                  .totalUnCommonExpenses(expenseTotalsBeforeReserveFunds.formatUnCommon())
                  .totalCommonExpensesPlusReserveFunds(expenseTotals.formatCommon())
                  .totalUnCommonExpensesPlusReserveFunds(expenseTotals.formatUnCommon())
                  .key(encryptionService.encryptObj(Expense.Keys.of(receipt.buildingId(), receipt.id())))
                  .expenses(expenseTableItems)
                  .build())

              .extraChargeDto(ExtraChargeInitFormDto.builder()
                  .key(encryptionService.encryptObj(ExtraCharge.Keys.newReceipt(receipt.id(), receipt.buildingId())))
                  .total(ConvertUtil.numberFormat(building.mainCurrency()).format(extraChargesTotal))
                  .extraCharges(extraChargeTableItems)
                  .build())

              .reserveFundDto(ReserveFundInitFormDto.builder()
                  .key(encryptionService.encryptObj(ReserveFund.Keys.newReceipt(receipt.id(), receipt.buildingId())))
                  .reserveFunds(reserveFundTableItems)
                  .build())

              .debtDto(DebtInitFormDto.builder()
                  .key("")
                  .debts(debtTableItems)
                  .debtReceiptsTotal(debtReceiptsTotal)
                  .debtTotal(ConvertUtil.numberFormat(building.debtCurrency()).format(debtTotal))
                  .build())
              .build();
        })
        .map(Templates::form);
  }

  @PUT
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Receipts.WRITE)
  public Uni<TemplateInstance> editReceipt(@BeanParam ReceiptEditRequest request) {

    final var keys = encryptionService.decryptObj(request.getKey(), Receipt.Keys.class);
    final var rateId = encryptionService.decryptObj(request.rateInput, ExtraCharge.Keys.class).id();

    if (request.month < 1 || request.month > 12) {
      return TemplateUtil.templateUni(Templates.formResponse(ReceiptFormResponse.error("Mes inválido")));
    }

    if (!ArrayUtils.contains(DateUtil.yearsPicker(), request.year)) {
      return TemplateUtil.templateUni(Templates.formResponse(ReceiptFormResponse.error("Año inválido")));
    }

    final var date = DateUtil.localDateParse(request.date);

    if (date == null) {
      return TemplateUtil.templateUni(Templates.formResponse(ReceiptFormResponse.error("Fecha inválida")));
    }

    final var update = Receipt.builder()
        .buildingId(keys.buildingId())
        .id(keys.id())
        .year(request.year)
        .month(request.month)
        .date(date)
        .rateId(rateId)
        .build();

    final var newKeys = update.keysWithHash();

    if (newKeys.hash() == keys.hash()) {
      return TemplateUtil.templateUni(Templates.formResponse(ReceiptFormResponse.msg("No hay cambios")));
    }

    final var formResponse = ReceiptFormResponse.builder()
        .key(encryptionService.encryptObj(newKeys))
        .generalFieldError("Actualizado")
        .build();

    return Uni.combine().all()
        .unis(buildingService.get(keys.buildingId()), rateService.get(rateId), receiptService.get(keys.id()),
            receiptService.update(update))
        .withUni((building, rate, receipt, i) -> {

          if (Objects.equals(receipt.rateId(), rateId)) {
            return Uni.createFrom().item(formResponse);
          }

          return Uni.combine().all()
              .unis(expenseService.readByReceipt(keys.id()), reserveFundService.listByBuilding(keys.buildingId()))
              .with((expenses, reserveFunds) -> {

                final var isRateNeeded = expenses.stream().map(Expense::currency)
                    .anyMatch(currency -> rate.fromCurrency() == currency);

                if (!isRateNeeded) {
                  return formResponse;
                }

                final var expenseTotalsBeforeReserveFunds = ConvertUtil.expenseTotals(rate.rate(), expenses);

                final var reserveFundExpenses = ConvertUtil.reserveFundExpenses(expenseTotalsBeforeReserveFunds,
                    reserveFunds, expenses);

                final var expenseTotals = ConvertUtil.expenseTotals(rate.rate(), expenses);

                final var countersDto = ExpenseCountersDto.builder()
                    .commonTotal(expenseTotalsBeforeReserveFunds.formatCommon())
                    .unCommonTotal(expenseTotalsBeforeReserveFunds.formatUnCommon())
                    .commonTotalPlusReserveFunds(expenseTotals.formatCommon())
                    .unCommonTotalPlusReserveFunds(expenseTotals.formatUnCommon())
                    .reserveFundExpenses(reserveFundExpenses)
                    .build();

                return formResponse.toBuilder()
                    .expenseCountersDto(countersDto)
                    .build();
              });
        })
        .map(Templates::formResponse);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> sendDialog() {
    final var buildingsUni = buildingService.ids().memoize().indefinitely();
    final var aptUni = buildingsUni.toMulti()
        .flatMap(Multi.createFrom()::iterable)
        .onItem()
        .transformToUni(str -> apartmentService.aptByBuildings(str)
            .map(list -> new Apts(str, list)))
        .merge()
        .collect()
        .asList();

    return Uni.combine().all()
        .unis(buildingsUni, aptUni)
        .with((buildings, apts) -> ReceiptInitDto.builder()
            .buildings(buildings)
            .apts(apts)
            .build())
        .map(Templates::sendDialog);
  }

  @POST
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> sendReceiptsStart(
      @RestForm String key,
      @RestForm String subject,
      @RestForm String msg,
      @RestForm Set<String> apts) {

    final var id = UUID.randomUUID().toString();

    if (apts.isEmpty()) {
      return Uni.createFrom().item(Templates.dialogError("Seleccione un apartmento"));
    }

    final var request = SendReceiptRequest.builder()
        .clientId(id)
        .key(key)
        .subject(subject)
        .msg(msg)
        .apts(apts)
        .language(i18n.getLanguage())
        .build();

    serverSideEventHelper.addSink(id);
    receiptFileService.sendReceipt(request);
    return Uni.createFrom().item(Templates.sendReceiptsStart(id, encryptionService.encryptObj(request)));
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> startSendReceipts(@RestPath String key) {

    final var id = UUID.randomUUID().toString();

    final var request = SendReceiptRequest.builder()
        .clientId(id)
        .key(key)
        .language(i18n.getLanguage())
        .build();

    serverSideEventHelper.addSink(id);
    receiptFileService.sendReceipt(request);
    return Uni.createFrom().item(Templates.sendReceiptsStart(id, encryptionService.encryptObj(request)));

  }

  @DELETE
  public Uni<Void> cancelSending(@RestPath String id) {
    receiptFileService.cancel(id);
    return Uni.createFrom().nullItem();
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Receipts.WRITE)
  public Uni<Response> copy(@RestPath String id) {
    final var keys = encryptionService.decryptObj(id, Receipt.Keys.class);

    return Uni.combine().all().unis(
            receiptService.get(keys.id()),
            expenseService.readByReceipt(keys.id()),
            debtService.readByReceipt(keys.buildingId(), keys.id()),
            extraChargeService.by(keys.buildingId(), String.valueOf(keys.id())))
        .with((receipt, expenses, debts, extraCharges) -> {

          return ReceiptCreateRequest.builder()
              .receipt(receipt.toBuilder()
                  .sent(false)
                  .lastSent(null)
                  .createdAt(DateUtil.utcLocalDateTime())
                  .build())
              .expenses(expenses)
              .extraCharges(extraCharges)
              .debts(debts)
              .build();
        })
        .flatMap(receiptService::insert)
        .map(result -> {

          final var newKeys = Receipt.Keys.builder()
              .buildingId(keys.buildingId())
              .id(result.id())
              .build();

          final var encrypted = encryptionService.encryptObj(newKeys);
          return Response.ok()
              .header(HxResponseHeader.REDIRECT.key(), Router.getURI(ReceiptController::edit, encrypted))
              .build();
        });
  }

  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance index(List<String> buildingIds);

    public static native TemplateInstance index$headerContainer(List<String> buildingIds);

    public static native TemplateInstance index$container();

    public static native TemplateInstance receipts(ReceiptTableResponse res);

    public static native TemplateInstance counters(ReceiptCountersDto dto);

    public static native TemplateInstance sentInfo(ReceiptTableItem item);

    public static native TemplateInstance newFileDialog(ReceiptFileFormDto dto);

    public static native TemplateInstance editIndex(String id);

    public static native TemplateInstance editIndex$headerContainer(String id);

    public static native TemplateInstance editIndex$container(String id);

    public static native TemplateInstance form(ReceiptInitFormDto dto);

    public static native TemplateInstance formResponse(ReceiptFormResponse dto);

    public static native TemplateInstance sendDialog(ReceiptInitDto dto);

    public static native TemplateInstance sendReceiptsStart(String clientId, String key);

    public static native TemplateInstance sendReceiptsProgressUpdate(ReceiptFileService.ProgressUpdate progressUpdate);

    public static native TemplateInstance dialogError(String error);
  }

  @Data
  public static class FileReceiptRequest {

    @NotBlank
    @RestForm
    private String data;
    @NotBlank
    @RestForm
    private String building;
    @RestForm
    private int year;
    @RestForm
    private int month;
    @NotBlank
    @RestForm
    private String date;
    @NotBlank
    @RestForm
    private String rateInput;
  }

  @Data
  public static class ReceiptEditRequest {

    @RestForm
    @NotBlank
    private String key;
    @RestForm
    private int year;
    @RestForm
    private int month;
    @NotBlank
    @RestForm
    private String date;
    @NotBlank
    @RestForm
    private String rateInput;
  }
}
