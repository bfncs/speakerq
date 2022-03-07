package us.byteb.speakerq;

import static akka.http.javadsl.server.Directives.*;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.byteb.speakerq.Room.Msg.GetRoomState;

public class App {

  private static final Logger LOG = LoggerFactory.getLogger(App.class);
  private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

  private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
  private static final boolean DEVELOPMENT = System.getenv("DEVELOPMENT") != null;
  private static final Duration ASK_TIMEOUT = Duration.ofSeconds(1);

  public static void main(String[] args) {
    LOG.info(
        "Starting app on port %d ...(dev mode %s)".formatted(PORT, DEVELOPMENT ? "on" : "off"));

    ActorSystem.create(
        Behaviors.setup(
            ctx -> {
              final ActorRef<Router.Msg> router = ctx.spawn(Router.create(), "router");

              Http.get(ctx.getSystem()).newServerAt("0.0.0.0", PORT).bind(createRoute(ctx, router));

              return Behaviors.empty();
            }),
        "speakerq");
  }

  private static Route createRoute(
      final ActorContext<Object> ctx, final ActorRef<Router.Msg> router) {
    final Route apiRooms =
        pathPrefix(
            "rooms",
            () ->
                pathPrefix(
                    PathMatchers.segment(),
                    roomId ->
                        concat(
                            path(
                                "ws",
                                () ->
                                    handleWebSocketMessages(
                                        WebsocketHandler.createWebsocketHandler(
                                            roomId, ctx, router))),
                            path(
                                "raisedHands",
                                () ->
                                    get(
                                        () ->
                                            optionalHeaderValueByName(
                                                "Accept",
                                                accept ->
                                                    completeWithFuture(
                                                        AskPattern.<Router.Msg, RoomState>ask(
                                                                router,
                                                                ref ->
                                                                    new Router.Msg(
                                                                        roomId,
                                                                        new GetRoomState(ref)),
                                                                ASK_TIMEOUT,
                                                                ctx.getSystem().scheduler())
                                                            .thenApply(
                                                                roomState -> {
                                                                  if (accept
                                                                      .map(
                                                                          value ->
                                                                              value
                                                                                  .equalsIgnoreCase(
                                                                                      "text/plain"))
                                                                      .orElse(false)) {
                                                                    return HttpResponse.create()
                                                                        .withEntity(
                                                                            String.valueOf(
                                                                                roomState
                                                                                    .raisedHands()
                                                                                    .size()));
                                                                  } else {
                                                                    return jsonResponse(
                                                                        roomState.raisedHands());
                                                                  }
                                                                }))))),
                            pathEnd(
                                () ->
                                    get(
                                        () ->
                                            completeWithFuture(
                                                AskPattern.<Router.Msg, RoomState>ask(
                                                        router,
                                                        ref ->
                                                            new Router.Msg(
                                                                roomId, new GetRoomState(ref)),
                                                        ASK_TIMEOUT,
                                                        ctx.getSystem().scheduler())
                                                    .thenApply(App::jsonResponse)))))));

    final Route api = pathPrefix("api", () -> concat(apiRooms, complete(StatusCodes.NOT_FOUND)));

    final Route frontend =
        DEVELOPMENT
            ? concat(getFromDirectory("frontend/public"), getFromFile("frontend/public/index.html"))
            : concat(getFromResourceDirectory("public"), getFromResource("public/index.html"));

    return concat(api, frontend);
  }

  private static HttpResponse jsonResponse(final Object payload) {
    try {
      return HttpResponse.create().withEntity(OBJECT_MAPPER.writeValueAsString(payload));
    } catch (JsonProcessingException e) {
      LOG.error("Unable to serialize response payload ({}): {}", payload, e.getMessage());
      return HttpResponse.create().withStatus(StatusCodes.INTERNAL_SERVER_ERROR);
    }
  }
}
