package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.util.Constants;
import io.quarkiverse.renarde.htmx.HxController;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.Cache;

@ApplicationScoped
@Authenticated
@Path("fragments")
public class FragmentController extends HxController {


  @GET
  @Cache(maxAge = Constants.CACHE_MAX_AGE)
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance currencyData() {
    return Fragments.currencyData();
  }

  @GET
  @Cache(maxAge = Constants.CACHE_MAX_AGE)
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance reserveFundTypes() {
    return Fragments.reserveFundTypes();
  }

  @GET
  @Cache(maxAge = Constants.CACHE_MAX_AGE)
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance expenseTypes() {
    return Fragments.expenseTypes();
  }

  @GET
  @Cache(maxAge = Constants.CACHE_MAX_AGE)
  @Produces(MediaType.TEXT_HTML)
  public TemplateInstance monthDialogPicker() {
    return Fragments.monthDialogPicker();
  }

  @CheckedTemplate
  public static class Fragments {

    public static native TemplateInstance rateInfo(String msg);

    public static native TemplateInstance currencyData();

    public static native TemplateInstance reserveFundTypes();

    public static native TemplateInstance expenseTypes();

    public static native TemplateInstance monthDialogPicker();
  }
}
