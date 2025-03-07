package com.yaz.kyotaidoshin.persistence.repository;

import com.yaz.kyotaidoshin.persistence.domain.OidcDbTokenQueryRequest;
import com.yaz.kyotaidoshin.persistence.model.OidcDbToken;
import com.yaz.kyotaidoshin.persistence.model.User;
import com.yaz.kyotaidoshin.persistence.model.domain.IdentityProvider;
import com.yaz.kyotaidoshin.persistence.turso.TursoWsService;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Stmt;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Value;
import com.yaz.kyotaidoshin.persistence.turso.ws.response.ExecuteResp.Row;
import com.yaz.kyotaidoshin.util.DateUtil;
import com.yaz.kyotaidoshin.util.SqlUtil;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//@LookupIfProperty(name = "app.repository.impl", stringValue = "turso")
//@Named("turso")
@ApplicationScoped
@RequiredArgsConstructor
public class OidcDbTokenRepository {

  private static final String COLLECTION = "oidc_db_token_state_manager";
  private static final String SELECT = """
      SELECT oidc_db_token_state_manager.*,users.provider_id, users.provider, users.email, users.username, users.first_name, 
      users.last_name, users.picture
      FROM %s 
      LEFT JOIN users ON oidc_db_token_state_manager.user_id = users.id
      %s
      ORDER BY oidc_db_token_state_manager.id DESC LIMIT ?
      """;
  private static final String DELETE = "DELETE FROM %s WHERE id = ?".formatted(COLLECTION);
  private static final String UPDATE_USER_ID = "UPDATE %s SET user_id = ? WHERE id = ?".formatted(COLLECTION);
  private static final String DELETE_BY_USER = "DELETE FROM %s WHERE user_id = ?".formatted(COLLECTION);
  private static final String READ = "SELECT * FROM %s WHERE id = ?".formatted(COLLECTION);
  private static final String DELETE_IF_EXPIRED = "DELETE FROM %s WHERE expires_in < ?".formatted(COLLECTION);
  private static final String INSERT = "INSERT INTO %s (id, id_token, access_token, refresh_token, expires_in) VALUES (%s)".formatted(
      COLLECTION, SqlUtil.params(5));
  private static final String EXPIRES = "UPDATE %s SET expires_in = ? WHERE id = ?".formatted(COLLECTION);

  private final TursoWsService tursoWsService;

  public Uni<Long> count() {
    return tursoWsService.count("id", COLLECTION);
  }

  public Uni<List<OidcDbToken>> select(OidcDbTokenQueryRequest queryRequest) {

    final var whereClause = queryRequest.lastId() == null ? "" : "WHERE oidc_db_token_state_manager.id < ?";
    final var sql = SELECT.formatted(COLLECTION, whereClause);

    final var values = new ArrayList<Value>();
    if (queryRequest.lastId() != null) {
      values.add(Value.text(queryRequest.lastId()));
    }

    values.add(Value.number(queryRequest.limit()));

    return tursoWsService.selectQuery(sql, values, this::from);

  }

  private OidcDbToken from(Row row) {
    return OidcDbToken.builder()
        .id(row.getString("id"))
        .idToken(row.getString("id_token"))
        .accessToken(row.getString("access_token"))
        .refreshToken(row.getString("refresh_token"))
        .expiresIn(row.getLong("expires_in"))
        .createdAt(row.getLocalDateTime("created_at"))
        .updatedAt(row.getLocalDateTime("updated_at"))
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

  public Uni<Integer> delete(String id) {
    return tursoWsService.executeQuery(DELETE, Value.text(id))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Integer> updateUserId(String id, String userId) {
    return tursoWsService.executeQuery(UPDATE_USER_ID, Value.text(userId), Value.text(id))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Integer> deleteByUser(String userId) {
    return tursoWsService.executeQuery(DELETE_BY_USER, Value.text(userId))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Integer> insert(String idToken, String accessToken, String refreshToken, long expiresIn, String id) {

    return tursoWsService.executeQuery(Stmt.stmt(INSERT, Value.text(id), Value.text(idToken), Value.text(accessToken),
            Value.text(refreshToken), Value.number(expiresIn)))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Optional<OidcDbToken>> read(String id) {
    return tursoWsService.selectOne(Stmt.stmt(READ, Value.text(id)), this::from);
  }

  public Uni<Integer> deleteIfExpired(long expiresIn) {

    return tursoWsService.executeQuery(DELETE_IF_EXPIRED, Value.number(expiresIn))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Integer> expires(String id) {

    return tursoWsService.executeQuery(EXPIRES, Value.number(DateUtil.epochSecond()), Value.text(id))
        .invoke(executeResp -> log.info("Expires: {}", executeResp.result().rowCount()))
        .map(executeResp -> executeResp.result().rowCount());

  }
}
