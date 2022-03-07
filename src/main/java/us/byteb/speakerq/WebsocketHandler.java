package us.byteb.speakerq;

import akka.Done;
import akka.NotUsed;
import akka.actor.typed.ActorRef;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Adapter;
import akka.http.javadsl.model.ws.Message;
import akka.http.javadsl.model.ws.TextMessage;
import akka.japi.Pair;
import akka.stream.OverflowStrategy;
import akka.stream.javadsl.Flow;
import akka.stream.javadsl.Sink;
import akka.stream.javadsl.Source;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.byteb.speakerq.Room.Msg.ParticipantJoined;
import us.byteb.speakerq.Room.Msg.ParticipantLeft;

public class WebsocketHandler {

  private static final Logger LOG = LoggerFactory.getLogger(WebsocketHandler.class);
  private static final JsonMapper OBJECT_MAPPER = JsonMapper.builder().build();

  static Flow<Message, Message, NotUsed> createWebsocketHandler(
      final String roomId, final ActorContext<?> ctx, final ActorRef<Router.Msg> router) {
    final String participantId = UUID.randomUUID().toString();
    final Sink<Message, NotUsed> incomingMessageSink =
        createForwardIncomingMessageToRoomSink(ctx, router, roomId, participantId);
    final OutgoingParticipantMessageWsSource outgoingParticipantMessageWsSource =
        OutgoingParticipantMessageWsSource.create(ctx);

    router.tell(
        new Router.Msg(
            roomId,
            new ParticipantJoined(participantId, outgoingParticipantMessageWsSource.receiver())));

    return Flow.fromSinkAndSourceCoupled(
        incomingMessageSink, outgoingParticipantMessageWsSource.source());
  }

  private static Sink<Message, NotUsed> createForwardIncomingMessageToRoomSink(
      final ActorContext<?> ctx,
      final ActorRef<Router.Msg> router,
      final String roomId,
      final String participantId) {
    final Sink<Message, CompletionStage<Done>> sink =
        Sink.foreach(msg -> forwardIncomingMessageToRoom(router, roomId, participantId, msg));

    final Pair<CompletionStage<Done>, Sink<Message, NotUsed>> materializedSink =
        sink.preMaterialize(ctx.getSystem());
    materializedSink
        .first()
        .thenRun(() -> router.tell(new Router.Msg(roomId, new ParticipantLeft(participantId))));

    return materializedSink.second();
  }

  private static void forwardIncomingMessageToRoom(
      final ActorRef<Router.Msg> router,
      final String roomId,
      final String participantId,
      final Message msg) {
    if (!((msg instanceof TextMessage textMessage) && textMessage.isStrict())) {
      LOG.warn("Ignoring unexpected message");
      return;
    }

    final String payload = textMessage.getStrictText();
    LOG.debug("Received message: {}", payload);
    try {
      final IncomingMessage incomingMessage =
          OBJECT_MAPPER.readValue(payload, IncomingMessage.class);
      router.tell(
          new Router.Msg(roomId, new Room.Msg.ParticipantMessage(participantId, incomingMessage)));
    } catch (JsonProcessingException e) {
      LOG.warn("Unable to parse incoming message ({}): {}", payload, e.getMessage());
    }
  }

  private record OutgoingParticipantMessageWsSource(
      ActorRef<OutgoingMessage> receiver, Source<Message, NotUsed> source) {
    private static OutgoingParticipantMessageWsSource create(final ActorContext<?> ctx) {
      final Source<Message, akka.actor.ActorRef> participantMessageReceiver =
          Source.<OutgoingMessage>actorRef(
                  m -> Optional.empty(), m -> Optional.empty(), 1024, OverflowStrategy.dropHead())
              .map(OBJECT_MAPPER::writeValueAsString)
              .map(TextMessage::create);
      final Pair<akka.actor.ActorRef, Source<Message, NotUsed>> materializedSource =
          participantMessageReceiver.preMaterialize(ctx.getSystem());

      return new OutgoingParticipantMessageWsSource(
          Adapter.toTyped(materializedSource.first()), materializedSource.second());
    }
  }
}
