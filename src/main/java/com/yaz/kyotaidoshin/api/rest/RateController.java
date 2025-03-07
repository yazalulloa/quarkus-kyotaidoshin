package com.yaz.kyotaidoshin.api.rest;

import com.yaz.kyotaidoshin.api.HxControllerWithUser;
import com.yaz.kyotaidoshin.api.domain.response.rate.RateCountersDto;
import com.yaz.kyotaidoshin.api.domain.response.rate.RateHistoricProgressUpdate;
import com.yaz.kyotaidoshin.api.domain.response.rate.RateTableResponse;
import com.yaz.kyotaidoshin.core.bean.ServerSideEventHelper;
import com.yaz.kyotaidoshin.core.service.BcvRates;
import com.yaz.kyotaidoshin.core.service.EncryptionService;
import com.yaz.kyotaidoshin.core.service.RateService;
import com.yaz.kyotaidoshin.persistence.domain.RateQuery;
import com.yaz.kyotaidoshin.persistence.domain.SortOrder;
import com.yaz.kyotaidoshin.persistence.model.Rate;
import com.yaz.kyotaidoshin.util.Constants;
import com.yaz.kyotaidoshin.util.ConvertUtil;
import com.yaz.kyotaidoshin.util.PermissionUtil;
import com.yaz.kyotaidoshin.util.RenardeUserImpl;
import com.yaz.kyotaidoshin.util.StringUtil;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.jboss.resteasy.reactive.Cache;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestStreamElementType;

@Slf4j
@Path("rates")
//@RolesAllowed(PermissionUtil.Rates.READ)
@PermissionsAllowed(PermissionUtil.Rates.READ)
@RequiredArgsConstructor
public class RateController extends HxControllerWithUser<RenardeUserImpl> {

  private final RateService rateService;
  private final EncryptionService encryptionService;
  private final ServerSideEventHelper serverSideEventHelper;
  @Inject
  @Channel(BcvRates.CHANNEL_KEY)
  Multi<RateHistoricProgressUpdate> historicRatesChannel;
  @Inject
  Sse sse;
  @Inject
  Event<BcvRates.Start> startEvent;
  @Inject
  Event<BcvRates.Stop> stopEvent;

  @GET
  @Produces(MediaType.TEXT_HTML)
  @Path("/")
  public Uni<TemplateInstance> index() {

    return rateService.currencies().map(currencies -> {
      if (isHxRequest()) {
        return concatTemplates(
            Templates.index$headerContainer(currencies),
            Templates.index$container()
        );
      }

      return Templates.index(currencies);
    });

  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> search(@RestQuery String lastKey,
      @RestQuery("date_input") String date,
      @RestQuery("currency_input") Set<String> currencies,
      @RestQuery("sort_order") String sortOrder) {

    final var keys = Optional.ofNullable(lastKey)
        .map(str -> encryptionService.decryptObj(str, Rate.Keys.class))
        .orElse(null);

    final var lastId = Optional.ofNullable(keys).map(Rate.Keys::id).orElse(0L);

    final var rateQuery = RateQuery.builder()
        .lastId(lastId)
        .date(StringUtil.validLocalDate(date))
        .currencies(currencies)
        .sortOrder(ConvertUtil.valueOfEnum(SortOrder.class, sortOrder))
        .build();

    return rateService.table(rateQuery)
        .map(Templates::rates);
  }

  @DELETE
  @Path("{key}")
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Rates.WRITE)
  public Uni<TemplateInstance> delete(@RestPath String key,
      @RestQuery("date_input") String date,
      @RestQuery("currency_input") Set<String> currencies,
      @RestQuery("sort_order") String sortOrder) {
    final var keys = encryptionService.decryptObj(key, Rate.Keys.class);

    return rateService.delete(keys.id())
        .replaceWith(rateService.counters(date, currencies, ConvertUtil.valueOfEnum(SortOrder.class, sortOrder)))
        .map(Templates::counters);
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  public Uni<TemplateInstance> currencyPicker() {
    return rateService.currencies()
        .map(Templates::currencyPicker);
  }

  @GET
  @Path("/historicRates/{key}")
  @RestStreamElementType(MediaType.TEXT_PLAIN)
  public Multi<OutboundSseEvent> historicRates(@NotBlank @RestPath String key) {
    startEvent.fireAsync(new BcvRates.Start(key));
    return historicRatesChannel
        .concatMap(update -> {

          final var sendUpdate = Templates.historicRatesUpdate(update).createUni()
              .map(data -> sse.newEventBuilder()
                  .name("rates-historic-progress")
                  .data(data)
                  .build())
              .toMulti();

          final Multi<OutboundSseEvent> endMsg =
              update.isEnd() ? Multi.createFrom().item(sse.newEvent("rates-historic-progress-close", ""))
                  : Multi.createFrom().empty();

          return Multi.createBy().concatenating()
              .streams(sendUpdate, endMsg);
        });
  }

  @GET
  @Produces(MediaType.TEXT_HTML)
  @PermissionsAllowed(PermissionUtil.Rates.WRITE)
  public Uni<TemplateInstance> startHistoricRates() {
    final var id = UUID.randomUUID().toString();
    return Uni.createFrom().item(Templates.historicRatesStart(id));

  }

  @DELETE
  @Path("/stopHistoricRates/{key}")
  public void stopHistoricRates(@RestPath String key) {
    stopEvent.fireAsync(new BcvRates.Stop(key));
  }

  @CheckedTemplate
  public static class Templates {

    public static native TemplateInstance index(List<String> currencies);

    public static native TemplateInstance index$headerContainer(List<String> currencies);

    public static native TemplateInstance index$container();

    public static native TemplateInstance rates(RateTableResponse res);

    public static native TemplateInstance counters(RateCountersDto dto);

    public static native TemplateInstance historicRatesStart(String clientId);

    public static native TemplateInstance historicRatesUpdate(RateHistoricProgressUpdate dto);

    public static native TemplateInstance currencyPicker(List<String> currencies);
  }


}
