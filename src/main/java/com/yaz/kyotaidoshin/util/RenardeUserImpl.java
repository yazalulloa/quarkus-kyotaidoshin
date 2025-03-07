package com.yaz.kyotaidoshin.util;

import com.yaz.kyotaidoshin.persistence.model.Permission;
import com.yaz.kyotaidoshin.persistence.model.User;
import io.quarkiverse.renarde.security.RenardeUser;
import java.util.List;
import java.util.Set;
import lombok.Builder;

@Builder
public record RenardeUserImpl(
    User user,
    List<Permission> permissions,
    Set<String> roles

) implements RenardeUser {

  @Override
  public Set<String> roles() {
    return roles;
  }

  @Override
  public String userId() {
    return user.id();
  }

  @Override
  public boolean registered() {
    return true;
  }
}
