package us.byteb.speakerq;

import java.util.List;

public record RoomState(List<RoomParticipant> participants, List<String> raisedHands) {
  public static final record RoomParticipant(String id, String name) {}
}
