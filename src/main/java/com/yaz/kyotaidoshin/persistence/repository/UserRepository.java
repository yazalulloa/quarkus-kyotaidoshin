package com.yaz.kyotaidoshin.persistence.repository;

import com.yaz.kyotaidoshin.persistence.domain.SortOrder;
import com.yaz.kyotaidoshin.persistence.domain.UserQuery;
import com.yaz.kyotaidoshin.persistence.model.NotificationEvent;
import com.yaz.kyotaidoshin.persistence.model.User;
import com.yaz.kyotaidoshin.persistence.model.domain.IdentityProvider;
import com.yaz.kyotaidoshin.persistence.turso.TursoWsService;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Stmt;
import com.yaz.kyotaidoshin.persistence.turso.ws.request.Value;
import com.yaz.kyotaidoshin.persistence.turso.ws.response.ExecuteResp.Row;
import com.yaz.kyotaidoshin.util.DateUtil;
import com.yaz.kyotaidoshin.util.SqlUtil;
import com.yaz.kyotaidoshin.util.StringUtil;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
//@LookupIfProperty(name = "app.repository.impl", stringValue = "turso")
//@Named("turso")
@ApplicationScoped
@RequiredArgsConstructor
public class UserRepository {

  private static final String COLLECTION = "users";
//  private static final String SELECT = "SELECT * FROM %s %s ORDER BY id DESC LIMIT ?";

  private static final String SELECT = """
      SELECT users.*,telegram_chats.chat_id as telegram_chat_id, telegram_chats.username as telegram_username,
        telegram_chats.first_name as telegram_first_name, GROUP_CONCAT(notifications_events.event) as events
        from users
      left join telegram_chats ON users.id = telegram_chats.user_id
      left join notifications_events ON users.id = notifications_events.user_id
      %s
      GROUP BY users.id
      ORDER BY users.id
      LIMIT ?
      """;
  private static final String DELETE = "DELETE FROM %s WHERE id = ?".formatted(COLLECTION);
  private static final String INSERT = """
      INSERT INTO %s (id, provider_id, provider, email, username, first_name, last_name, picture, data) VALUES (%s)
      """.formatted(COLLECTION, SqlUtil.params(9));
  private static final String UPDATE_LAST_LOGIN_AT = "UPDATE %s SET last_login_at = datetime() WHERE id = ?".formatted(
      COLLECTION);
  private static final String LIKE_QUERY = " concat(email, first_name, last_name, username) LIKE ? ";
  private static final String SELECT_ID_FROM_PROVIDER = "SELECT id FROM %s WHERE provider = ? AND provider_id = ?".formatted(
      COLLECTION);

  private static final String SELECT_FROM_PROVIDER = "SELECT * FROM %s WHERE provider = ? AND provider_id = ?".formatted(
      COLLECTION);

  private static final String READ = "SELECT * FROM %s WHERE id = ?".formatted(COLLECTION);

  private static final String EXISTS = "SELECT id FROM %s WHERE id = ?".formatted(COLLECTION);


  private final TursoWsService tursoWsService;


  public Uni<Long> count() {
    return tursoWsService.count("id", COLLECTION);
  }


  public Uni<Integer> delete(String id) {
    return tursoWsService.executeQuery(DELETE, Value.text(id))
        .map(executeResp -> executeResp.result().rowCount());
  }

  public Uni<Optional<String>> getIdFromProvider(IdentityProvider provider, String providerId) {

    return tursoWsService.selectOne(
        Stmt.stmt(SELECT_ID_FROM_PROVIDER, Value.text(provider.name()), Value.text(providerId)),
        row -> row.getString("id"));
  }

  public Uni<Optional<User>> getFromProvider(IdentityProvider provider, String providerId) {

    return tursoWsService.selectOne(
        Stmt.stmt(SELECT_FROM_PROVIDER, Value.text(provider.name()), Value.text(providerId)), this::from);
  }

  public Uni<Integer> updateLastLoginAt(String id) {
    return tursoWsService.executeQuery(UPDATE_LAST_LOGIN_AT, Value.text(id))
        .map(executeResp -> executeResp.result().rowCount());
  }


  public Uni<String> save(User user) {
    final var now = DateUtil.epochSecond();
    final var id = now + UUID.randomUUID().toString();

    return tursoWsService.executeQuery(INSERT, Value.text(id), Value.text(user.providerId()),
            Value.enumV(user.provider()), Value.text(user.email()), Value.text(user.username()),
            Value.text(user.firstName()), Value.text(user.lastName()), Value.text(user.picture()),
            Value.text(user.data().encode()))
        .replaceWith(id);
  }


  public Uni<List<User>> select(UserQuery query) {
    final var whereParams = new ArrayList<String>();
    final var values = new ArrayList<Value>();

    final var lastId = StringUtil.trimFilter(query.lastId());

    if (lastId != null) {
      final var direction = query.sortOrder() == SortOrder.DESC ? "<" : ">";
      whereParams.add("id %s ?".formatted(direction));
      values.add(Value.text(lastId));
    }

    if (query.identityProvider() != null) {
      whereParams.add("provider = ?");
      values.add(Value.text(query.identityProvider().name()));
    }

    final var q = StringUtil.trimFilter(query.q());

    if (q != null) {
      whereParams.add(LIKE_QUERY);
      values.add(Value.text(q));
    }

    var whereClause = String.join(" AND ", whereParams);
    if (!whereClause.isEmpty()) {
      whereClause = " WHERE " + whereClause;
    } else {
      whereClause = "";
    }

    values.add(Value.number(query.limit()));

    final var sql = SELECT.formatted(whereClause);

    return tursoWsService.selectQuery(sql, values, this::from);
  }


  public Uni<Optional<User>> read(String id) {
    return tursoWsService.selectOne(Stmt.stmt(READ, Value.text(id)), this::from);
  }


  public Uni<Boolean> exists(String id) {
    return tursoWsService.selectOne(Stmt.stmt(EXISTS, Value.text(id)), row -> row.getString("id"))
        .map(Optional::isPresent);
  }


  public Uni<Integer> update(User user) {
    final var update = """
        UPDATE %s SET email = ?, username = ?, first_name = ?, last_name = ?, picture = ?, data = ?
        WHERE id = ?
        """.formatted(COLLECTION);

    return tursoWsService.executeQuery(update, Value.text(user.email()), Value.text(user.username()),
            Value.text(user.firstName()), Value.text(user.lastName()), Value.text(user.picture()),
            Value.text(user.data().encode()), Value.text(user.id()))
        .map(executeResp -> executeResp.result().rowCount());
  }

  private User from(Row row) {
    final var events = Optional.ofNullable(row.getString("events"))
        .map(Object::toString)
        .map(str -> Arrays.stream(str.split(","))
            .map(NotificationEvent.Event::valueOf)
            .collect(Collectors.toSet()))
        .orElseGet(Collections::emptySet);

    return User.builder()
        .id(row.getString("id"))
        .providerId(row.getString("provider_id"))
        .provider(IdentityProvider.valueOf(row.getString("provider")))
        .email(row.getString("email"))
        .username(row.getString("username"))
        .firstName(row.getString("first_name"))
        .lastName(row.getString("last_name"))
        .picture(row.getString("picture"))
        .data(row.getJsonObject("data"))
        .createdAt(row.getLocalDateTime("created_at"))
        .lastLoginAt(row.getLocalDateTime("last_login_at"))
        .telegramChat(User.TelegramChat.builder()
            .chatId(row.getLong("telegram_chat_id"))
            .username(row.getString("telegram_username"))
            .firstName(row.getString("telegram_first_name"))
            .build())
        .notificationEvents(events)
        .build();
  }
}
