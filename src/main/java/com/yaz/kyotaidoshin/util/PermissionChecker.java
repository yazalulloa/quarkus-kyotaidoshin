package com.yaz.kyotaidoshin.util;

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Named("PermissionChecker")
@RequestScoped
public class PermissionChecker {

//  @Inject
//  RenardeSecurity renardeSecurity;

  @Inject
  CurrentIdentityAssociation currentIdentityAssociation;

  @Inject
  SecurityIdentity securityIdentity;

  private boolean hasPerm(String perm) {
//    log.info("Checking permission: {}", perm);

    try {
//      return true;
//      return currentIdentityAssociation.getDeferredIdentity().await().atMost(Duration.ofSeconds(3)).hasRole(perm);
      return securityIdentity.hasRole(perm);
//      return renardeSecurity.getUser().roles().contains(perm);
    } catch (Exception e) {
      log.error("Error checking permission", e);
      return false;
    }
  }

  public boolean hasApartmentsRead() {
    return hasPerm(PermissionUtil.Apartments.READ);
  }

  public boolean hasApartmentsWrite() {
    return hasPerm(PermissionUtil.Apartments.WRITE);
  }

  public boolean hasApartmentsUploadBackup() {
    return hasPerm(PermissionUtil.Apartments.UPLOAD_BACKUP);
  }

  public boolean hasApartmentsDownloadBackup() {
    return hasPerm(PermissionUtil.Apartments.DOWNLOAD_BACKUP);
  }

  public boolean hasBuildingsRead() {
    return hasPerm(PermissionUtil.Buildings.READ);
  }

  public boolean hasBuildingsWrite() {
    return hasPerm(PermissionUtil.Buildings.WRITE);
  }

  public boolean hasBuildingsUploadBackup() {
    return hasPerm(PermissionUtil.Buildings.UPLOAD_BACKUP);
  }

  public boolean hasBuildingsDownloadBackup() {
    return hasPerm(PermissionUtil.Buildings.DOWNLOAD_BACKUP);
  }

  public boolean hasRatesRead() {
    return hasPerm(PermissionUtil.Rates.READ);
  }

  public boolean hasRatesWrite() {
    return hasPerm(PermissionUtil.Rates.WRITE);
  }

  public boolean hasReceiptsRead() {
    return hasPerm(PermissionUtil.Receipts.READ);
  }

  public boolean hasReceiptsWrite() {
    return hasPerm(PermissionUtil.Receipts.WRITE);
  }

  public boolean hasReceiptsUploadBackup() {
    return hasPerm(PermissionUtil.Receipts.UPLOAD_BACKUP);
  }

  public boolean hasReceiptsDownloadBackup() {
    return hasPerm(PermissionUtil.Receipts.DOWNLOAD_BACKUP);
  }

  public boolean hasUsersRead() {
    return hasPerm(PermissionUtil.Users.READ);
  }

  public boolean hasUsersWrite() {
    return hasPerm(PermissionUtil.Users.WRITE);
  }

  public boolean hasSessionsRead() {
    return hasPerm(PermissionUtil.Sessions.READ);
  }

  public boolean hasSessionsWrite() {
    return hasPerm(PermissionUtil.Sessions.WRITE);
  }

  public boolean hasPermissionsRead() {
    return hasPerm(PermissionUtil.Permissions.READ);
  }

  public boolean hasPermissionsWrite() {
    return hasPerm(PermissionUtil.Permissions.WRITE);
  }


}
