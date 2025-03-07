package com.yaz.kyotaidoshin.persistence.repository;

import com.yaz.kyotaidoshin.persistence.domain.RateQuery;
import com.yaz.kyotaidoshin.persistence.domain.SortOrder;
import com.yaz.kyotaidoshin.persistence.model.Rate;
import com.yaz.kyotaidoshin.persistence.turso.TursoWsService;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Stmt;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Value;
import com.yaz.kyotaidoshin.persistence.turso.ws.response.ExecuteResp.Row;
import com.yaz.kyotaidoshin.util.SqlUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
//@LookupIfProperty(name = "app.repository.impl", stringValue = "turso")
//@Named("turso")
@ApplicationScoped
@RequiredArgsConstructor
public class RateRepository {

  private static final String COLLECTION = "rates";
  private static final String SELECT = "SELECT * FROM %s %s ORDER BY id %s LIMIT ?";
  private static final String DELETE_BY_ID = "DELETE FROM %s WHERE id = ?".formatted(COLLECTION);
  private static final String LAST = "SELECT * FROM %s WHERE from_currency = ? AND to_currency = ? ORDER BY id DESC LIMIT 1".formatted(
      COLLECTION);
  private static final String HASH_EXISTS = "SELECT id FROM %s WHERE hash = ? LIMIT 1".formatted(COLLECTION);

  private static final String INSERT_BULK = """
      INSERT INTO %s (from_currency, to_currency, rate, date_of_rate, date_of_file, source, hash, etag, last_modified) VALUES %s
      """;

  private static final String INSERT = """
      INSERT INTO %s (from_currency, to_currency, rate, date_of_rate, date_of_file, source, hash, etag, last_modified) VALUES (%s) returning id
      """.formatted(COLLECTION, SqlUtil.params(9));

  private static final String READ = "SELECT * FROM %s WHERE id = ?".formatted(COLLECTION);

  private static final String EXISTS = "SELECT id FROM %s WHERE from_currency = ? AND to_currency = ? AND rate = ? AND date_of_rate = ? LIMIT 1".formatted(
      COLLECTION);

  private static final String CURRENCIES = "SELECT DISTINCT from_currency FROM %s".formatted(COLLECTION);


  private static final String QUERY_COUNT = "SELECT COUNT(%s) as query_count FROM %s WHERE %s";

  private final TursoWsService tursoWsService;


  public Uni<Long> count() {
    return tursoWsService.count("id", COLLECTION);
  }


  public Uni<Integer> delete(long id) {

    return tursoWsService.executeQuery(Stmt.stmt(DELETE_BY_ID, Value.number(id)))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Optional<Long>> queryCount(RateQuery rateQuery) {
    final var pair = whereClause(rateQuery);
    var whereClause = String.join(" AND ", pair.getKey());

    if (whereClause.isEmpty()) {
      return Uni.createFrom().item(Optional.empty());
    }

    final var sql = QUERY_COUNT.formatted("id", COLLECTION, whereClause);
    return tursoWsService.count(sql, pair.getValue().toArray(new Value[0]))
        .map(Optional::of);
  }

  public Pair<ArrayList<String>, ArrayList<Value>> whereClause(RateQuery rateQuery) {
    final var whereParams = new ArrayList<String>(2);
    final var values = new ArrayList<Value>(2);

    if (rateQuery.currencies() != null && !rateQuery.currencies().isEmpty()) {
      whereParams.add("from_currency IN (%s)".formatted(SqlUtil.params(rateQuery.currencies().size())));
      rateQuery.currencies().forEach(c -> values.add(Value.text(c)));
    }

    final var date = StringUtil.trimFilter(rateQuery.date());

    if (date != null) {

      final var direction = rateQuery.sortOrder() == SortOrder.DESC ? "<=" : ">=";
      whereParams.add("date_of_rate %s ?".formatted(direction));
      values.add(Value.text(date));
    }

    return Pair.of(whereParams, values);
  }


  public Uni<List<Rate>> listRows(RateQuery rateQuery) {

    final var pair = whereClause(rateQuery);
    final var whereParams = pair.getKey();
    final var values = pair.getValue();

    Optional.of(rateQuery.lastId())
        .filter(l -> l > 0)
        .ifPresent(lastId -> {

          final var direction = rateQuery.sortOrder() == SortOrder.DESC ? "<" : ">";
          whereParams.add("id %s ?".formatted(direction));
          values.add(Value.number(lastId));
        });

    var whereClause = "";
    if (!whereParams.isEmpty()) {
      whereClause = " WHERE " + String.join(" AND ", whereParams);
    }

    values.add(Value.number(rateQuery.limit()));
    final var sql = SELECT.formatted(COLLECTION, whereClause, rateQuery.sortOrder());
    return tursoWsService.selectQuery(sql, values, this::from);
  }


  public Uni<Optional<Long>> save(Rate rate) {

    final var stmt = Stmt.stmt(INSERT, Value.text(rate.fromCurrency()), Value.text(rate.toCurrency()),
        Value.number(rate.rate()), Value.text(rate.dateOfRate()), Value.text(rate.dateOfFile()),
        Value.enumV(rate.source()), Value.number(rate.hash()), Value.text(rate.etag()),
        Value.text(rate.lastModified()));
    return tursoWsService.selectOne(stmt, row -> row.getLong("id"));
  }


  public Uni<Integer> insert(Collection<Rate> rates) {
    final var paramSize = 9;
    final var values = new Value[rates.size() * paramSize];

    var i = 0;
    for (Rate rate : rates) {
      values[i++] = Value.text(rate.fromCurrency());
      values[i++] = Value.text(rate.toCurrency());
      values[i++] = Value.number(rate.rate());
      values[i++] = Value.text(rate.dateOfRate());
      values[i++] = Value.text(rate.dateOfFile());
      values[i++] = Value.enumV(rate.source());
      values[i++] = Value.number(rate.hash());
      values[i++] = Value.text(rate.etag());
      values[i++] = Value.text(rate.lastModified());
    }

    final var sql = INSERT_BULK.formatted(COLLECTION, SqlUtil.valuesParams(paramSize, rates.size()));

    return tursoWsService.executeQuery(sql, values)
        .map(executeResp -> executeResp.result().rowCount());
  }

  private Rate from(Row row) {
    return Rate.builder()
        .id(row.getLong("id"))
        .fromCurrency(row.getString("from_currency"))
        .toCurrency(row.getString("to_currency"))
        .rate(row.getBigDecimal("rate"))
        .dateOfRate(row.getLocalDate("date_of_rate"))
        .source(row.getEnum("source", Rate.Source::valueOf))
        .dateOfFile(row.getLocalDateTime("date_of_file"))
        .createdAt(row.getLocalDateTime("created_at"))
        .description(row.getString("description"))
        .hash(row.getLong("hash"))
        .etag(row.getString("etag"))
        .lastModified(row.getString("last_modified"))
        .build();
  }


  public Uni<Optional<Rate>> last(String fromCurrency, String toCurrency) {
    return tursoWsService.selectOne(Stmt.stmt(LAST, Value.text(fromCurrency), Value.text(toCurrency)), this::from);
  }

  public Uni<Boolean> exists(long hash) {
    return tursoWsService.selectOne(Stmt.stmt(HASH_EXISTS, Value.number(hash)), row -> row.getLong("id") != null)
        .map(opt -> opt.orElse(false));
  }

  public Uni<Boolean> exists(BigDecimal rate, LocalDate dateOfRate) {
    final var sql = "SELECT id FROM %s WHERE rate = ? AND date_of_rate = ? LIMIT 1".formatted(COLLECTION);
    return tursoWsService.exists(Stmt.stmt(sql, Value.number(rate), Value.text(dateOfRate)));
  }


  public Uni<Optional<Rate>> read(long id) {
    return tursoWsService.selectOne(Stmt.stmt(READ.formatted(COLLECTION), Value.number(id)), this::from);
  }

  public Uni<Boolean> exists(String fromCurrency, String toCurrency, BigDecimal rate, LocalDate dateOfRate) {
    final var stmt = Stmt.stmt(EXISTS, Value.text(fromCurrency), Value.text(toCurrency), Value.number(rate),
        Value.text(dateOfRate));
    return tursoWsService.exists(stmt);
  }

  public Uni<List<String>> currencies() {
    return tursoWsService.selectQuery(CURRENCIES, row -> row.getString("from_currency"));
  }
}
