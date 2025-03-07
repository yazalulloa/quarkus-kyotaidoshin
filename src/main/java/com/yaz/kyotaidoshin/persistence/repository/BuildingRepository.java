package com.yaz.kyotaidoshin.persistence.repository;

import com.yaz.kyotaidoshin.persistence.domain.BuildingQuery;
import com.yaz.kyotaidoshin.persistence.model.Building;
import com.yaz.kyotaidoshin.persistence.turso.TursoWsService;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Stmt;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Value;
import com.yaz.kyotaidoshin.persistence.turso.ws.response.ExecuteResp;
import com.yaz.kyotaidoshin.util.SqlUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
////@LookupIfProperty(name = "app.repository.impl", stringValue = "turso")
//@Named("turso")
@ApplicationScoped
@RequiredArgsConstructor
public class BuildingRepository {

  private static final String COLLECTION = "buildings";
  private static final String SELECT = """
      SELECT buildings.*, email_configs.email as config_email, COUNT(apartments.building_id) as apt_count
      FROM buildings
      LEFT JOIN email_configs ON buildings.email_config_id = email_configs.id
      LEFT JOIN apartments ON buildings.id = apartments.building_id 
      %s
      GROUP BY buildings.id
      ORDER BY buildings.id
      LIMIT ?
      """;
  private static final String READ = "SELECT * FROM %s WHERE id = ?".formatted(COLLECTION);
  private static final String DELETE = "DELETE FROM %s WHERE id = ?".formatted(COLLECTION);
  private static final String INSERT = """
      INSERT INTO %s (id, name, rif, main_currency, debt_currency, currencies_to_show_amount_to_pay, fixed_pay, fixed_pay_amount, 
      round_up_payments, email_config_id) VALUES (%s);
      """.formatted(COLLECTION, SqlUtil.params(10));

  private static final String UPDATE = """
      UPDATE %s SET name = ?, rif = ?, main_currency = ?, debt_currency = ?, currencies_to_show_amount_to_pay = ?, fixed_pay = ?,
            fixed_pay_amount = ?, round_up_payments = ?, email_config_id = ? WHERE id = ?;
      """.formatted(COLLECTION);

  private static final String INSERT_IGNORE = """
      INSERT INTO %s (id, name, rif, main_currency, debt_currency, currencies_to_show_amount_to_pay, fixed_pay, fixed_pay_amount, 
      round_up_payments) VALUES %s ON CONFLICT DO NOTHING;
      """;

  private static final String EMAIL_CONFIG_DELETED = "UPDATE %s SET email_config_id = NULL WHERE id IN (%s)";

  private static final String EXISTS = "SELECT id FROM %s WHERE id = ? LIMIT 1".formatted(COLLECTION);

  private static final String SELECT_ALL_IDS = "SELECT id FROM %s ORDER BY id".formatted(COLLECTION);
  private static final String SELECT_BY_EMAIL_CONFIG = "SELECT id FROM %s WHERE email_config_id = ?".formatted(
      COLLECTION);


  private final TursoWsService tursoWsService;

  public Uni<Long> count() {
    return tursoWsService.count("id", COLLECTION);
  }

  public Uni<Integer> delete(String id) {

    return tursoWsService.executeQuery(DELETE, Value.text(id))
        .map(e -> e.result().rowCount());
  }

  public Uni<List<Building>> select(BuildingQuery query) {

    final var lastId = StringUtil.trimFilter(query.lastId());
    final var values = new ArrayList<Value>();
    var whereClause = "";

    if (lastId != null) {
      whereClause = "WHERE buildings.id > ?";
      values.add(Value.text(lastId));
    }

    values.add(Value.number(query.limit()));

    final var sql = SELECT.formatted(whereClause);

    return tursoWsService.selectQuery(sql, values, this::from);
  }

  private Building from(ExecuteResp.Row row) {

    final var currenciesToShowAmountToPay = Optional.ofNullable(row.getString("currencies_to_show_amount_to_pay"))
        .map(str -> str.split(","))
        .stream()
        .flatMap(Arrays::stream)
        .collect(Collectors.toSet());

    return Building.builder()
        .id(row.getString("id"))
        .name(row.getString("name"))
        .rif(row.getString("rif"))
        .mainCurrency(row.getString("main_currency"))
        .debtCurrency(row.getString("debt_currency"))
        .currenciesToShowAmountToPay(currenciesToShowAmountToPay)
        .fixedPay(row.getBoolean("fixed_pay"))
        .fixedPayAmount(row.getBigDecimal("fixed_pay_amount"))
        .roundUpPayments(row.getBoolean("round_up_payments"))
        .emailConfigId(row.getString("email_config_id"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
        .configEmail(row.getString("config_email"))
        .aptCount(row.getLong("apt_count"))
        .build();
  }

  public Uni<List<String>> selectAllIds() {
    return tursoWsService.selectQuery(SELECT_ALL_IDS, row -> row.getString("id"));
  }


  public Uni<Boolean> exists(String buildingId) {

    return tursoWsService.selectOne(Stmt.stmt(EXISTS, Value.text(buildingId)), row -> row.getString("id") != null)
        .map(opt -> opt.orElse(false));
  }


  public Uni<Optional<Building>> read(String buildingId) {

    return tursoWsService.selectOne(Stmt.stmt(READ, Value.text(buildingId)), this::from);
  }


  public Uni<Integer> update(Building building) {

    final var currenciesToShowAmountToPay = String.join(",", building.currenciesToShowAmountToPay());

    return tursoWsService.executeQuery(UPDATE, Value.text(building.name()), Value.text(building.rif()),
            Value.text(building.mainCurrency()), Value.text(building.debtCurrency()),
            Value.text(currenciesToShowAmountToPay),
            Value.bool(building.fixedPay()), Value.number(building.fixedPayAmount()),
            Value.bool(building.roundUpPayments()),
            Value.text(building.emailConfigId()), Value.text(building.id()))
        .map(e -> e.result().rowCount());

  }
  public Uni<Integer> insert(Building building) {

    return tursoWsService.executeQuery(INSERT, Value.text(building.id()), Value.text(building.name()),
            Value.text(building.rif()), Value.text(building.mainCurrency()), Value.text(building.debtCurrency()),
            Value.text(String.join(",", building.currenciesToShowAmountToPay())),
            Value.bool(building.fixedPay()), Value.number(building.fixedPayAmount()),
            Value.bool(building.roundUpPayments()), Value.text(building.emailConfigId()))
        .map(e -> e.result().rowCount());
  }

  public Uni<Integer> insertIgnore(Collection<Building> buildings) {

    final var values = buildings.stream().flatMap(building -> {
      return Stream.of(
          Value.text(building.id()), Value.text(building.name()),
          Value.text(building.rif()), Value.text(building.mainCurrency()), Value.text(building.debtCurrency()),
          Value.text(String.join(",", building.currenciesToShowAmountToPay())),
          Value.bool(building.fixedPay()), Value.number(building.fixedPayAmount()),
          Value.bool(building.roundUpPayments())
      );
    }).toArray(Value[]::new);

    final var params = Stream.generate(() -> SqlUtil.params(9))
        .map("(%s)"::formatted)
        .limit(buildings.size())
        .collect(Collectors.joining(","));

    return tursoWsService.executeQuery(INSERT_IGNORE.formatted(COLLECTION, params), values)
        .map(e -> e.result().rowCount());
  }


  public Uni<Integer> updateEmailConfig(Set<String> ids) {
    final var values = ids.stream()
        .map(Value::text)
        .toArray(Value[]::new);

    final var sql = EMAIL_CONFIG_DELETED.formatted(COLLECTION, SqlUtil.params(ids.size()));

    return tursoWsService.executeQuery(sql, values)
        .map(e -> e.result().rowCount());
  }


  public Uni<Set<String>> selectByEmailConfig(String id) {
    return tursoWsService.selectQuerySet(Stmt.stmt(SELECT_BY_EMAIL_CONFIG, Value.text(id)), row -> row.getString("id"));
  }


  public Uni<Integer> updateEmailConfig(Set<String> set, String newConfigId) {
    final var sql = "UPDATE buildings SET email_config_id = ? WHERE id IN (%s)".formatted(
        SqlUtil.params(set.size()));

    final var values = Stream.concat(Stream.of(Value.text(newConfigId)), set.stream().map(Value::text))
        .toArray(Value[]::new);

    return tursoWsService.executeQuery(sql, values)
        .map(e -> e.result().rowCount());
  }


}
