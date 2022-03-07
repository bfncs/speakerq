package us.byteb.speakerq;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public sealed interface OutgoingMessage {
  @JsonTypeName("JOINED")
  record Joined(String participantId) implements OutgoingMessage {}

  @JsonTypeName("ROOM_STATE_UPDATED")
  record RoomStateUpdated(RoomState room) implements OutgoingMessage {}
}
