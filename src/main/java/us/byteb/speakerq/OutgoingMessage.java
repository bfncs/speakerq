package us.byteb.speakerq;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface OutgoingMessage {
  @JsonTypeName("JOINED")
  record Joined(String participantId) implements OutgoingMessage {}

  @JsonTypeName("ROOM_STATE_UPDATED")
  record RoomStateUpdated(RoomState room) implements OutgoingMessage {}

  record RoomState(List<RoomParticipant> participants, List<String> raisedHands) {}

  record RoomParticipant(String id, String name) {}
}
