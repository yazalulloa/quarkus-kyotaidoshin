package com.yaz.kyotaidoshin.core.service;

import com.yaz.kyotaidoshin.persistence.domain.UserQuery;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class PermissionFixer {

  private final PermissionService permissionService;
  private final UserService userService;


  public Uni<Void> all() {

    return permissionService.totalCount()
        .flatMap(l -> {

//          if (l > 0) {
//            return Uni.createFrom().voidItem();
//          }

          return userService.list(UserQuery.builder().limit(1000).build())
              .map(list -> {

                return list.stream()
                    .flatMap(user -> {
                      return Stream.of(PermissionUtil.ALL_PERMS)
                          .map(type -> Pair.of(user, type));

                    })
                    .toList();
              })
              .flatMap(permissionService::insert)
              .replaceWithVoid();
        });

  }
}
