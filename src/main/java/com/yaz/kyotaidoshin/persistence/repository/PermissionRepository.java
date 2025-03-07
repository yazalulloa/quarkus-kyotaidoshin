package com.yaz.kyotaidoshin.persistence.repository;

import com.yaz.kyotaidoshin.persistence.domain.PermissionQuery;
import com.yaz.kyotaidoshin.persistence.domain.SortOrder;
import com.yaz.kyotaidoshin.persistence.model.Permission;
import com.yaz.kyotaidoshin.persistence.model.User;
import com.yaz.kyotaidoshin.persistence.model.domain.IdentityProvider;
import com.yaz.kyotaidoshin.persistence.turso.TursoWsService;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Stmt;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Value;
import com.yaz.kyotaidoshin.persistence.turso.ws.response.ExecuteResp.Row;
import com.yaz.kyotaidoshin.util.SqlUtil;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PermissionRepository {

  private static final String COLLECTION = "permissions";

  private static final String SELECT = """
      SELECT permissions.*,users.provider_id, users.provider, users.email, users.username, users.first_name, 
      users.last_name, users.picture
      FROM %s 
      LEFT JOIN users ON permissions.user_id = users.id
      %s
      ORDER BY permissions.created_at %s, permissions.user_id, permissions.type LIMIT ?
      """;

  private static final String READ = "SELECT * FROM %s WHERE user_id = ? AND type = ?".formatted(COLLECTION);
  private static final String DELETE = "DELETE FROM %s WHERE user_id = ? AND type = ?".formatted(COLLECTION);
  private static final String INSERT_IGNORE = "INSERT INTO %s (user_id, type) VALUES %s ON CONFLICT DO NOTHING";
  private static final String QUERY_COUNT = "SELECT COUNT(%s) as query_count FROM %s WHERE %s";
  private static final String SELECT_BY_USER = "SELECT * FROM %s WHERE user_id = ?".formatted(COLLECTION);

  private static final String DELETE_WHERE = "DELETE FROM %s WHERE user_id = ? AND type NOT IN (%s)";
  private static final String DELETE_BY_USER = "DELETE FROM %s WHERE user_id = ?".formatted(COLLECTION);

  private final TursoWsService tursoWsService;

  public Uni<Long> count() {
    return tursoWsService.count("*", COLLECTION);
  }

  public Uni<Optional<Permission>> read(String userId, String type) {
    return tursoWsService.selectOne(Stmt.stmt(READ, Value.text(userId), Value.text(type)), this::from);
  }

  public Uni<List<Permission>> selectByUser(String userId) {
    return tursoWsService.selectQuery(Stmt.stmt(SELECT_BY_USER, Value.text(userId)), this::from);
  }

  public Uni<List<Permission>> select(PermissionQuery permissionQuery) {

    final var whereParams = new ArrayList<String>();
    final var values = new ArrayList<Value>();

    Optional.ofNullable(permissionQuery.lastKeys())
        .ifPresent(keys -> {
          whereParams.add("(permissions.created_at, permissions.user_id, permissions.type) > (?, ?, ?)");
          values.add(Value.text(keys.createdAt()));
          values.add(Value.text(keys.userId()));
          values.add(Value.text(keys.type()));
        });

    Optional.ofNullable(permissionQuery.userIds())
        .filter(c -> !c.isEmpty())
        .ifPresent(userIds -> {
          whereParams.add("permissions.user_id IN (%s)".formatted(SqlUtil.params(userIds.size())));
          userIds.forEach(c -> values.add(Value.text(c)));
        });

    Optional.ofNullable(permissionQuery.types())
        .filter(c -> !c.isEmpty())
        .ifPresent(types -> {
          whereParams.add("permissions.type IN (%s)".formatted(SqlUtil.params(types.size())));
          types.forEach(c -> values.add(Value.text(c)));
        });

    var whereClause = "";
    if (!whereParams.isEmpty()) {
      whereClause = " WHERE " + String.join(" AND ", whereParams);
    }

    final var sql = SELECT.formatted(COLLECTION, whereClause, SortOrder.ASC);

    values.add(Value.number(permissionQuery.limit()));

    return tursoWsService.selectQuery(sql, values, this::from);
  }

  private Permission from(Row row) {
    return Permission.builder()
        .userId(row.getString("user_id"))
        .type(row.getString("type"))
        .createdAt(row.getLocalDateTime("created_at"))

        .user(User.builder()
            .id(row.getString("user_id"))
            .providerId(row.getString("provider_id"))
            .provider(row.getEnum("provider", IdentityProvider::valueOf))
            .email(row.getString("email"))
            .username(row.getString("username"))
            .firstName(row.getString("first_name"))
            .lastName(row.getString("last_name"))
            .picture(row.getString("picture"))
            .build())
        .build();
  }

  public Uni<Integer> delete(String userId, String type) {

    return tursoWsService.executeQuery(Stmt.stmt(DELETE, Value.text(userId), Value.text(type)))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Integer> insert(List<Pair<User, String>> pairs) {

    final var values = new Value[pairs.size() * 2];
    var i = 0;
    for (Pair<User, String> pair : pairs) {
      values[i++] = Value.text(pair.getLeft().id());
      values[i++] = Value.text(pair.getRight());
    }
    final var params = Stream.generate(() -> SqlUtil.params(2))
        .map("(%s)"::formatted)
        .limit(pairs.size())
        .collect(Collectors.joining(","));

    final var sql = Stmt.stmt(INSERT_IGNORE.formatted(COLLECTION, params), values);

    return tursoWsService.executeQuery(sql)
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Optional<Long>> queryCount(PermissionQuery permissionQuery) {
    final var whereParams = new ArrayList<String>();
    final var values = new ArrayList<Value>();

    Optional.ofNullable(permissionQuery.userIds())
        .filter(c -> !c.isEmpty())
        .ifPresent(userIds -> {
          whereParams.add("permissions.user_id IN (%s)".formatted(SqlUtil.params(userIds.size())));
          userIds.forEach(c -> values.add(Value.text(c)));
        });

    Optional.ofNullable(permissionQuery.types())
        .filter(c -> !c.isEmpty())
        .ifPresent(types -> {
          whereParams.add("permissions.type IN (%s)".formatted(SqlUtil.params(types.size())));
          types.forEach(c -> values.add(Value.text(c)));
        });

    if (whereParams.isEmpty()) {
      return Uni.createFrom().item(Optional.empty());
    }

    final var whereClause = String.join(" AND ", whereParams);

    final var sql = QUERY_COUNT.formatted("*", COLLECTION, whereClause);
    return tursoWsService.count(sql, values.toArray(new Value[0]))
        .map(Optional::of);
  }

  public Uni<Integer> update(String userId, Set<String> perms) {

    final var values = new Value[perms.size() * 2];
    var i = 0;
    for (String perm : perms) {
      values[i++] = Value.text(userId);
      values[i++] = Value.text(perm);
    }

    final var insertStmt = Stmt.stmt(INSERT_IGNORE.formatted(COLLECTION, SqlUtil.valuesParams(2, perms.size())),
        values);

    final var deleteValues = new Value[perms.size() + 1];
    deleteValues[0] = Value.text(userId);
    i = 1;
    for (String perm : perms) {
      deleteValues[i++] = Value.text(perm);
    }

    final var deleteStmt = Stmt.stmt(DELETE_WHERE.formatted(COLLECTION, SqlUtil.params(perms.size())), deleteValues);

    return tursoWsService.executeQueries(insertStmt, deleteStmt)
        .map(SqlUtil::rowCount);


  }

  public Uni<Integer> deleteByUser(String userId) {
    return tursoWsService.executeQuery(DELETE_BY_USER, Value.text(userId))
        .map(executeResp -> executeResp.result().rowCount());
  }
}
