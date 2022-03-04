package us.byteb.speakerq;

import akka.actor.typed.ActorRef;

public interface Participant {
  String participantId();

  ActorRef<OutgoingMessage> receiver();

  default Participant withName(String name) {
    return new Ready(participantId(), receiver(), name);
  }

  record Initializing(String participantId, ActorRef<OutgoingMessage> receiver)
      implements Participant {}

  record Ready(String participantId, ActorRef<OutgoingMessage> receiver, String name)
      implements Participant {}
}
