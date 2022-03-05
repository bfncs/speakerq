package us.byteb.speakerq;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Room {

  private static final Logger LOG = LoggerFactory.getLogger(Room.class);

  public static Behavior<Msg> create(final String roomId) {
    return Behaviors.setup(
        ctx -> {
          final Map<String, Participant> participants = new HashMap<>();
          final List<String> raisedHands = new ArrayList<>();

          return Behaviors.receive(Msg.class)
              .onMessage(
                  Msg.ParticipantJoined.class,
                  msg -> {
                    final Participant participant =
                        new Participant.Initializing(msg.participantId(), msg.receiver());
                    LOG.info("Participant {} joined", participant.participantId());

                    participants.put(participant.participantId(), participant);
                    msg.receiver().tell(new OutgoingMessage.Joined(participant.participantId()));
                    broadcastRoomStateUpdate(participants, raisedHands);

                    return Behaviors.same();
                  })
              .onMessage(
                  Msg.ParticipantLeft.class,
                  msg -> {
                    LOG.info("Participant {} left", msg.participantId());

                    participants.remove(msg.participantId());
                    raisedHands.remove(msg.participantId());
                    broadcastRoomStateUpdate(participants, raisedHands);

                    return participants.isEmpty() ? Behaviors.stopped() : Behaviors.same();
                  })
              .onMessage(
                  Msg.ParticipantMessage.class,
                  msg -> {
                    final String participantId = msg.participantId();
                    LOG.info(
                        "Received message from participant {}: {}", participantId, msg.payload());

                    switch (msg.payload()) {
                      case IncomingMessage.SetName setName -> {
                        participants.put(
                            participantId,
                            participants.get(participantId).withName(setName.name()));
                      }
                      case IncomingMessage.RaiseHand ignored -> {
                        if (!raisedHands.contains(participantId)) {
                          raisedHands.add(participantId);
                        }
                      }
                      case IncomingMessage.LowerHand ignored -> {
                        raisedHands.remove(participantId);
                      }
                    }

                    broadcastRoomStateUpdate(participants, raisedHands);

                    return Behaviors.same();
                  })
              .onMessage(
                  Msg.GetRoomState.class,
                  msg -> {
                    msg.ref().tell(createRoomState(participants, raisedHands));
                    return Behaviors.same();
                  })
              .build();
        });
  }

  private static void broadcastRoomStateUpdate(
      final Map<String, Participant> participants, final List<String> raisedHands) {
    final OutgoingMessage.RoomStateUpdated msg =
        new OutgoingMessage.RoomStateUpdated(createRoomState(participants, raisedHands));

    for (final Participant p : participants.values()) {
      p.receiver().tell(msg);
    }
  }

  private static OutgoingMessage.RoomState createRoomState(
      final Map<String, Participant> participants, final List<String> raisedHands) {
    final List<OutgoingMessage.RoomParticipant> roomParticipants =
        participants.values().stream()
            .flatMap(
                p -> {
                  if (!(p instanceof Participant.Ready activeParticipant)) {
                    return Stream.empty();
                  }
                  return Stream.of(
                      new OutgoingMessage.RoomParticipant(
                          activeParticipant.participantId(), activeParticipant.name()));
                })
            .collect(Collectors.toList());
    final OutgoingMessage.RoomState roomState =
        new OutgoingMessage.RoomState(roomParticipants, raisedHands);
    return roomState;
  }

  public interface Msg {
    record ParticipantJoined(String participantId, ActorRef<OutgoingMessage> receiver)
        implements Msg {}

    record ParticipantLeft(String participantId) implements Msg {}

    record ParticipantMessage(String participantId, IncomingMessage payload) implements Msg {}

    record GetRoomState(ActorRef<OutgoingMessage.RoomState> ref) implements Msg {}
  }
}
