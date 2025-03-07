package com.yaz.kyotaidoshin.util;

import com.yaz.kyotaidoshin.api.domain.response.InitResponse.Page;
import com.yaz.kyotaidoshin.api.rest.ApartmentController;
import com.yaz.kyotaidoshin.api.rest.BuildingController;
import com.yaz.kyotaidoshin.api.rest.PermissionController;
import com.yaz.kyotaidoshin.api.rest.RateController;
import com.yaz.kyotaidoshin.api.rest.ReceiptController;
import com.yaz.kyotaidoshin.api.rest.SessionController;
import com.yaz.kyotaidoshin.api.rest.UserController;
import io.quarkiverse.renarde.router.Router;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import jakarta.ws.rs.Produces;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PermissionUtil {

  public static final String PAGE_PERMS_KEY = "PAGE_PERMS";

  public static final String SYSTEM = "system";

  @Named(PAGE_PERMS_KEY)
  @Produces
  @Singleton
  Map<String, Page> producePagePerms() {
    final var map = new LinkedHashMap<String, Page>();
    map.put(Apartments.READ, new Page("nav-apartments", Router.getURI(ApartmentController::index), Apartments.LABEL));
    map.put(Buildings.READ, new Page("nav-buildings", Router.getURI(BuildingController::index), Buildings.LABEL));
    map.put(Receipts.READ, new Page("nav-receipts", Router.getURI(ReceiptController::index), Receipts.LABEL));
    map.put(Users.READ, new Page("nav-users", Router.getURI(UserController::index), Users.LABEL));
    map.put(Rates.READ, new Page("nav-rates", Router.getURI(RateController::index), Rates.LABEL));
    map.put(Sessions.READ, new Page("nav-sessions", Router.getURI(SessionController::index), Sessions.LABEL));
    map.put(Permissions.READ,
        new Page("nav-permissions", Router.getURI(PermissionController::index), Permissions.LABEL));

    return Collections.unmodifiableMap(map);
  }


  public static final String[] ALL_PERMS = new String[]{
      Apartments.READ,
      Apartments.WRITE,
      Apartments.UPLOAD_BACKUP,
      Apartments.DOWNLOAD_BACKUP,
      Buildings.READ,
      Buildings.WRITE,
      Receipts.READ,
      Receipts.WRITE,
      Receipts.UPLOAD_BACKUP,
      Receipts.DOWNLOAD_BACKUP,
      Users.READ,
      Users.WRITE,
      Rates.READ,
      Rates.WRITE,
      Rates.HISTORIC,
      Sessions.READ,
      Sessions.WRITE,
      Permissions.READ,
      Permissions.WRITE,
      SYSTEM
  };

  public record Type(String label, String[] perms) {

  }

  public static final Type[] ALL_TYPES = new Type[]{
      new Type(Apartments.LABEL, Apartments.ALL),
      new Type(Buildings.LABEL, Buildings.ALL),
      new Type(Receipts.LABEL, Receipts.ALL),
      new Type(Users.LABEL, Users.ALL),
      new Type(Rates.LABEL, Rates.ALL),
      new Type(Sessions.LABEL, Sessions.ALL),
      new Type(Permissions.LABEL, Permissions.ALL)
  };

  public static class Apartments {

    private Apartments() {
    }

    public static final String LABEL = "main.apartments";
    public static final String READ = "apartments:read";
    public static final String WRITE = "apartments:write";
    public static final String UPLOAD_BACKUP = "apartments:upload-backup";
    public static final String DOWNLOAD_BACKUP = "apartments:download-backup";

    public static final String[] ALL = new String[]{
        READ,
        WRITE,
        UPLOAD_BACKUP,
        DOWNLOAD_BACKUP
    };
  }

  public static class Buildings {

    private Buildings() {
    }

    public static final String LABEL = "main.buildings";
    public static final String READ = "buildings:read";
    public static final String WRITE = "buildings:write";
    public static final String UPLOAD_BACKUP = "buildings:upload-backup";
    public static final String DOWNLOAD_BACKUP = "buildings:download-backup";

    public static final String[] ALL = new String[]{
        READ,
        WRITE,
        UPLOAD_BACKUP,
        DOWNLOAD_BACKUP
    };

  }

  public static class Receipts {

    private Receipts() {
    }

    public static final String LABEL = "main.receipts";
    public static final String READ = "receipts:read";
    public static final String WRITE = "receipts:write";
    public static final String UPLOAD_BACKUP = "receipts:upload-backup";
    public static final String DOWNLOAD_BACKUP = "receipts:download-backup";

    public static final String[] ALL = new String[]{
        READ,
        WRITE,
        UPLOAD_BACKUP,
        DOWNLOAD_BACKUP
    };
  }

  public static class Users {

    private Users() {
    }

    public static final String LABEL = "main.users";
    public static final String READ = "users:read";
    public static final String WRITE = "users:write";

    public static final String[] ALL = new String[]{
        READ,
        WRITE
    };
  }

  public static class Rates {

    private Rates() {
    }

    public static final String LABEL = "main.rates";
    public static final String READ = "rates:read";
    public static final String WRITE = "rates:write";
    public static final String HISTORIC = "rates:historic";

    public static final String[] ALL = new String[]{
        READ,
        WRITE,
        HISTORIC
    };
  }

  public static class Sessions {

    private Sessions() {
    }

    public static final String LABEL = "main.sessions";
    public static final String READ = "sessions:read";
    public static final String WRITE = "sessions:write";

    public static final String[] ALL = new String[]{
        READ,
        WRITE
    };
  }

  public static class Permissions {

    private Permissions() {
    }

    public static final String LABEL = "main.permissions";
    public static final String READ = "permissions:read";
    public static final String WRITE = "permissions:write";

    public static final String[] ALL = new String[]{
        READ,
        WRITE
    };
  }


}
