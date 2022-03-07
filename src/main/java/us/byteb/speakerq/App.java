package us.byteb.speakerq;

import static akka.http.javadsl.server.Directives.*;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.actor.typed.javadsl.AskPattern;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.model.*;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.http.javadsl.server.PathMatchers;
import akka.http.javadsl.server.Route;
import akka.japi.Pair;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.byteb.speakerq.Room.Msg.GetRoomState;
import us.byteb.speakerq.Room.Msg.ParticipantJoined;
import us.byteb.speakerq.Room.Msg.ParticipantLeft;
import us.byteb.speakerq.Room.Msg.ParticipantMessage;

public class App {

  private static final Logger LOG = LoggerFactory.getLogger(App.class);

  private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
  private static final boolean DEVELOPMENT = System.getenv("DEVELOPMENT") != null;
  private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();
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
                                        createWebsocketHandler(roomId, ctx, router))),
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
                                                    .thenApply(
                                                        roomState -> jsonResponse(roomState))))))));

    final Route api = pathPrefix("api", () -> concat(apiRooms, complete(StatusCodes.NOT_FOUND)));

    final Route frontend =
        DEVELOPMENT
            ? concat(getFromDirectory("frontend/public"), getFromFile("frontend/public/index.html"))
            : concat(getFromResourceDirectory("public"), getFromResource("public/index.html"));

    return concat(api, frontend);
  }

  private static Flow<Message, Message, NotUsed> createWebsocketHandler(
      final String roomId, final ActorContext<?> ctx, final ActorRef<Router.Msg> router) {

    final Source<Message, akka.actor.ActorRef> participantMessageReceiver =
        Source.<OutgoingMessage>actorRef(
                m -> Optional.empty(), m -> Optional.empty(), 1024, OverflowStrategy.dropHead())
            .map(OBJECT_MAPPER::writeValueAsString)
            .map(TextMessage::create);
    final Pair<akka.actor.ActorRef, Source<Message, NotUsed>> materializedSource =
        participantMessageReceiver.preMaterialize(ctx.getSystem());

    final String participantId = UUID.randomUUID().toString();
    final ActorRef<OutgoingMessage> participantReceiver =
        Adapter.toTyped(materializedSource.first());
    router.tell(new Router.Msg(roomId, new ParticipantJoined(participantId, participantReceiver)));

    final Sink<Message, CompletionStage<Done>> sink =
        Sink.foreach(
            msg -> {
              if (!((msg instanceof TextMessage textMessage) && textMessage.isStrict())) {
                LOG.error("Ignoring unexpected message");
                return;
              }

              final String payload = textMessage.getStrictText();
              LOG.info("Received message: {}", payload);
              try {
                final IncomingMessage incomingMessage =
                    OBJECT_MAPPER.readValue(payload, IncomingMessage.class);
                router.tell(
                    new Router.Msg(roomId, new ParticipantMessage(participantId, incomingMessage)));
              } catch (JsonProcessingException e) {
                LOG.warn("Unable to parse incoming message ({}): {}", payload, e.getMessage());
              }
            });
    final Pair<CompletionStage<Done>, Sink<Message, NotUsed>> materializedSink =
        sink.preMaterialize(ctx.getSystem());
    materializedSink
        .first()
        .thenRun(
            () -> {
              router.tell(new Router.Msg(roomId, new ParticipantLeft(participantId)));
            });

    return Flow.fromSinkAndSourceCoupled(materializedSink.second(), materializedSource.second());
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
