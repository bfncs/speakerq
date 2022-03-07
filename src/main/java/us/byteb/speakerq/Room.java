package us.byteb.speakerq;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import us.byteb.speakerq.IncomingMessage.LowerHand;
import us.byteb.speakerq.IncomingMessage.RaiseHand;
import us.byteb.speakerq.IncomingMessage.SetName;
import us.byteb.speakerq.OutgoingMessage.Joined;
import us.byteb.speakerq.OutgoingMessage.RoomStateUpdated;
import us.byteb.speakerq.Participant.Ready;
import us.byteb.speakerq.Room.Msg.GetRoomState;
import us.byteb.speakerq.Room.Msg.ParticipantJoined;
import us.byteb.speakerq.Room.Msg.ParticipantLeft;
import us.byteb.speakerq.Room.Msg.ParticipantMessage;
import us.byteb.speakerq.RoomState.RoomParticipant;

public class Room {

  private static final Logger LOG = LoggerFactory.getLogger(Room.class);

  public static Behavior<Msg> create() {
    return Behaviors.setup(
        ctx -> {
          final State state = State.empty();

          return Behaviors.receiveMessage(
              message ->
                  switch (message) {
                    case ParticipantJoined msg -> handleParticipantJoined(state, msg);
                    case ParticipantLeft msg -> handleParticipantLeft(state, msg);
                    case ParticipantMessage msg -> handleParticipantMessage(state, msg);
                    case GetRoomState msg -> handleGetRoomState(state, msg);
                  });
        });
  }

  private static Behavior<Msg> handleParticipantJoined(
      final State state, final ParticipantJoined msg) {
    final Participant participant =
        new Participant.Initializing(msg.participantId(), msg.receiver());
    LOG.info("Participant {} joined", participant.participantId());

    state.participants().put(participant.participantId(), participant);
    msg.receiver().tell(new Joined(participant.participantId()));
    broadcastRoomStateUpdate(state);

    return Behaviors.same();
  }

  private static Behavior<Msg> handleParticipantLeft(final State state, final ParticipantLeft msg) {
    LOG.info("Participant {} left", msg.participantId());

    state.participants().remove(msg.participantId());
    state.raisedHands().remove(msg.participantId());
    broadcastRoomStateUpdate(state);

    return state.participants().isEmpty() ? Behaviors.stopped() : Behaviors.same();
  }

  private static Behavior<Msg> handleGetRoomState(final State state, final GetRoomState msg) {
    msg.ref().tell(createRoomState(state));
    return state.participants().isEmpty() ? Behaviors.stopped() : Behaviors.same();
  }

  private static void broadcastRoomStateUpdate(final State state) {
    final RoomStateUpdated msg = new RoomStateUpdated(createRoomState(state));

    for (final Participant participant : state.participants().values()) {
      participant.receiver().tell(msg);
    }
  }

  private static Behavior<Msg> handleParticipantMessage(
      final State state, final ParticipantMessage msg) {
    final String participantId = msg.participantId();
    LOG.info("Received message from participant {}: {}", participantId, msg.payload());

    switch (msg.payload()) {
      case SetName setName -> {
        state
            .participants()
            .put(participantId, state.participants().get(participantId).withName(setName.name()));
      }
      case RaiseHand ignored -> {
        if (!state.raisedHands().contains(participantId)) {
          state.raisedHands().add(participantId);
        }
      }
      case LowerHand ignored -> {
        state.raisedHands().remove(participantId);
      }
    }
    broadcastRoomStateUpdate(state);

    return Behaviors.same();
  }

  private static RoomState createRoomState(final State state) {
    final List<RoomParticipant> roomParticipants =
        state.participants().values().stream()
            .flatMap(
                p -> {
                  if (!(p instanceof Ready readyParticipant)) {
                    return Stream.empty();
                  }

                  final RoomParticipant roomParticipant =
                      new RoomParticipant(
                          readyParticipant.participantId(), readyParticipant.name());
                  return Stream.of(roomParticipant);
                })
            .toList();

    return new RoomState(roomParticipants, state.raisedHands());
  }

  private record State(Map<String, Participant> participants, List<String> raisedHands) {
    private static State empty() {
      return new State(new HashMap<>(), new ArrayList<>());
    }
  }

  public sealed interface Msg {
    record ParticipantJoined(String participantId, ActorRef<OutgoingMessage> receiver)
        implements Msg {}

    record ParticipantLeft(String participantId) implements Msg {}

    record ParticipantMessage(String participantId, IncomingMessage payload) implements Msg {}

    record GetRoomState(ActorRef<RoomState> ref) implements Msg {}
  }
}
