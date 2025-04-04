package com.yaz.kyotaidoshin.persistence.repository;

import com.yaz.kyotaidoshin.persistence.domain.ReceiptCreateRequest;
import com.yaz.kyotaidoshin.persistence.domain.ReceiptQuery;
import com.yaz.kyotaidoshin.persistence.model.ExtraCharge;
import com.yaz.kyotaidoshin.persistence.model.Receipt;
import com.yaz.kyotaidoshin.persistence.turso.TursoWsService;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.NamedArg;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Stmt;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Value;
import com.yaz.kyotaidoshin.persistence.turso.ws.response.ExecuteResp.Row;
import com.yaz.kyotaidoshin.util.SqlUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//@LookupIfProperty(name = "app.repository.impl", stringValue = "turso")
//@Named("turso")
@ApplicationScoped
@RequiredArgsConstructor
public class ReceiptRepository {

  private static final String COLLECTION = "receipts";
  private static final String SELECT_BY_BUILDING = "SELECT * FROM %s WHERE building_id = ? ORDER BY id".formatted(
      COLLECTION);
  private static final String SELECT_WITH_SUM = """
      SELECT receipts.*,
             SUM(expenses.amount) AS total_amount,
             SUM(CASE WHEN expenses.type = 'COMMON' THEN expenses.amount ELSE 0 END) AS total_commons,
             SUM(CASE WHEN expenses.type = 'UNCOMMON' THEN expenses.amount ELSE 0 END) AS total_uncommons
      FROM receipts
      LEFT JOIN expenses ON receipts.building_id = expenses.building_id AND receipts.id = expenses.receipt_id
      %s
      GROUP BY receipts.building_id, receipts.id
      ORDER BY receipts.id DESC
      LIMIT ?
      """;

  private static final String SELECT = """
      SELECT * FROM receipts %s ORDER BY id DESC LIMIT ?
      """;

  private static final String COUNT = "SELECT COUNT(id) FROM receipts %s";
  private static final String CURSOR_QUERY = "receipts.id < ?";
  private static final String INSERT = """
      INSERT INTO %s (building_id, year, month, date, rate_id, sent, last_sent, created_at) VALUES (%s) returning id
      """.formatted(COLLECTION, SqlUtil.params(8));
  private static final String UPDATE_LAST_SENT = "UPDATE %s SET sent = true, last_sent = ? WHERE id = ?".formatted(
      COLLECTION);
  private static final String READ = "SELECT * FROM %s WHERE id = ?".formatted(COLLECTION);
  private static final String DELETE = "DELETE FROM %s WHERE id = ?".formatted(COLLECTION);

  private static final String UPDATE = (
      "UPDATE %s SET year = :year, month = :month, date = :date, rate_id = :rate_id "
          + "WHERE id = :id").formatted(COLLECTION);

  private static final String REMOVE_LAST_SENT = "UPDATE %s SET sent = false WHERE id = ?".formatted(
      COLLECTION);


  private final TursoWsService tursoWsService;
  private final ExpenseRepository expenseRepository;
  private final ExtraChargeRepository extraChargeRepository;
  private final DebtRepository debtRepository;


  public Uni<Long> count() {
    return tursoWsService.count("id", COLLECTION);
  }

  public Uni<Integer> delete(String buildingId, long id) {
    if (true) {
      return tursoWsService.executeQuery(DELETE, Value.number(id))
          .map(executeResp -> executeResp.result().rowCount());
    }

    final var stmts = new Stmt[5];
    stmts[0] = Stmt.stmt(DELETE, Value.number(id));
    stmts[1] = expenseRepository.stmtDeleteByReceipt(buildingId, id);
    final var deleteExtraCharges = extraChargeRepository.stmtDeleteByReceipt(buildingId, String.valueOf(id));
    stmts[2] = deleteExtraCharges[0];
    stmts[3] = deleteExtraCharges[1];
    stmts[4] = debtRepository.stmtDeleteByReceipt(buildingId, id);
    return tursoWsService.executeQueries(stmts)
        .map(SqlUtil::rowCount);
  }

  public Uni<InsertResult> insert(ReceiptCreateRequest request) {

    final var receipt = request.receipt();
    final var insertReceipt = Stmt.stmt(INSERT, Value.text(receipt.buildingId()), Value.number(receipt.year()),
        Value.number(receipt.month()), Value.text(receipt.date()), Value.number(receipt.rateId()),
        Value.bool(receipt.sent()), Value.text(receipt.lastSent()), Value.text(receipt.createdAt()));

    return tursoWsService.selectOne(insertReceipt, row -> row.getLong("id"))
        .map(optional -> optional.orElseThrow(() -> new RuntimeException("Receipt not inserted")))
        .flatMap(id -> {

          final var expenses = request.expenses();

          final var debts = request.debts();

          var numberOfStmt = 0;
          if (!expenses.isEmpty()) {
            numberOfStmt++;
          }

          if (!debts.isEmpty()) {
            numberOfStmt++;
          }

          if (numberOfStmt == 0) {
            return Uni.createFrom().item(new InsertResult(id, 1));
          }

          final var statements = new Stmt[numberOfStmt];

          statements[0] = expenseRepository.stmtInsert(id, expenses);
          statements[1] = debtRepository.stmtInsert(id, debts);

          final var extraCharges = request.extraCharges()
              .stream()
              .map(extraCharge -> extraCharge.toBuilder()
                  .parentReference(String.valueOf(id))
                  .build())
              .toList();

          final var insertExtraCharges = Multi.createFrom().iterable(extraCharges)
              .onItem().transformToUni(extraCharge -> {
                final var apartments = extraCharge.apartments().stream().map(ExtraCharge.Apt::number).collect(Collectors.toSet());
                return extraChargeRepository.insert(extraCharge, apartments)
                    .map(ExtraChargeRepository.InsertResult::count);
              })
              .merge()
              .collect()
              .in(AtomicInteger::new, AtomicInteger::addAndGet)
              .map(AtomicInteger::get);

          final var insert = tursoWsService.executeQueries(statements)
              .map(SqlUtil::rowCount);

          return Uni.combine().all().unis(insert, insertExtraCharges)
              .with(Integer::sum)
              .map(sum -> new InsertResult(id, sum));
        });

  }

  public Uni<Integer> updateLastSent(long id, LocalDateTime localDateTime) {
    return tursoWsService.executeQuery(
            Stmt.stmt(UPDATE_LAST_SENT, Value.text(localDateTime), Value.number(id)))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Optional<Receipt>> read(long id) {
    return tursoWsService.selectOne(Stmt.stmt(READ, Value.number(id)), this::from);
  }

  public Uni<Optional<Long>> count(ReceiptQuery receiptQuery) {
    final var selectWhereClause = new ArrayList<String>();
    var selectValuesSize = 0;

    final var buildings = receiptQuery.buildings().stream()
        .map(StringUtil::trimFilter)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    if (!buildings.isEmpty()) {
      selectWhereClause.add("receipts.building_id IN (" + SqlUtil.params(buildings.size()) + ")");
      selectValuesSize += buildings.size();
    }

    if (receiptQuery.month() != null && receiptQuery.month().length > 0) {
      final var months = receiptQuery.month();
      selectWhereClause.add("receipts.month IN (" + SqlUtil.params(months.length) + ")");
      selectValuesSize += months.length;
    }

    if (receiptQuery.date() != null) {
      selectWhereClause.add("receipts.date <= ?");
      selectValuesSize++;
    }

    final var values = new Value[selectValuesSize];
    var currentIndex = 0;

    if (!buildings.isEmpty()) {

      for (String building : buildings) {
        values[currentIndex++] = Value.text(building);
      }
    }

    if (receiptQuery.month() != null) {
      for (int month : receiptQuery.month()) {
        values[currentIndex++] = Value.number(month);
      }
    }

    if (receiptQuery.date() != null) {
      values[currentIndex++] = Value.text(receiptQuery.date());
    }

    if (selectWhereClause.isEmpty()) {
      return Uni.createFrom().item(Optional.empty());
    }

    final var where = "WHERE " + String.join(SqlUtil.AND, selectWhereClause);
    final var sql = COUNT.formatted(where);

    return tursoWsService.executeQuery(sql, values)
        .map(tursoWsService::extractCount)
        .map(Optional::of);
  }

  public Uni<List<Receipt>> select(ReceiptQuery receiptQuery) {

    final var lastId = receiptQuery.lastId();

    final var selectWhereClause = new ArrayList<String>();

    var selectValuesSize = 1;
    if (lastId != null) {
      selectWhereClause.add(CURSOR_QUERY);
      selectValuesSize += 1;
    }

    final var buildings = receiptQuery.buildings().stream()
        .map(StringUtil::trimFilter)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

    if (!buildings.isEmpty()) {
      selectWhereClause.add("receipts.building_id IN (" + SqlUtil.params(buildings.size()) + ")");
      selectValuesSize += buildings.size();
    }

    if (receiptQuery.month() != null && receiptQuery.month().length > 0) {
      final var months = receiptQuery.month();
      selectWhereClause.add("receipts.month IN (" + SqlUtil.params(months.length) + ")");
      selectValuesSize += months.length;
    }

    if (receiptQuery.date() != null) {
      selectWhereClause.add("receipts.date <= ?");
      selectValuesSize++;
    }

    final var values = new Value[selectValuesSize];
    var currentIndex = 0;
    if (lastId != null) {
      values[0] = Value.number(lastId);
      currentIndex = 1;
    }

    if (!buildings.isEmpty()) {

      for (String building : buildings) {
        values[currentIndex++] = Value.text(building);
      }
    }

    if (receiptQuery.month() != null) {
      for (int month : receiptQuery.month()) {
        values[currentIndex++] = Value.number(month);
      }
    }

    if (receiptQuery.date() != null) {
      values[currentIndex++] = Value.text(receiptQuery.date());
    }

    values[currentIndex] = Value.number(receiptQuery.limit());

    final var where = selectWhereClause.isEmpty() ? "" : "WHERE " + String.join(SqlUtil.AND, selectWhereClause);
    final var sql = SELECT.formatted(where);

    return tursoWsService.selectQuery(Stmt.stmt(sql, values), this::from);
  }

  private Receipt from(Row row) {
    return Receipt.builder()
        .buildingId(row.getString("building_id"))
        .id(row.getLong("id"))
        .year(row.getInt("year"))
        .month(row.getInt("month"))
        .date(row.getLocalDate("date"))
        .rateId(row.getLong("rate_id"))
        .sent(row.getBoolean("sent"))
        .lastSent(row.getLocalDateTime("last_sent"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .build();
  }

  public Uni<Integer> update(Receipt receipt) {
    final var stmt = Stmt.stmt(UPDATE,
        NamedArg.number("year", receipt.year()), NamedArg.number("month", receipt.month()),
        NamedArg.text("date", receipt.date()), NamedArg.number("rate_id", receipt.rateId()),
        NamedArg.number("id", receipt.id()));

    return tursoWsService.executeQuery(stmt)
        .map(r -> r.result().rowCount());
  }

  public Uni<Integer> removeLastSent(long id) {
    return tursoWsService.executeQuery(
            Stmt.stmt(REMOVE_LAST_SENT, Value.number(id)))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public record InsertResult(long id, int sum) {

  }
}
