package us.byteb.speakerq;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;

public class Router {

  public static Behavior<Msg> create() {
    return Behaviors.receive(
        (ctx, msg) -> {
          final String roomActorName = ("room-" + msg.roomId()).replaceAll("[^-\\w]+", "");
          final ActorRef<Room.Msg> room =
              ctx.getChild(roomActorName)
                  .<ActorRef<Room.Msg>>map(ActorRef::unsafeUpcast)
                  .orElseGet(() -> ctx.spawn(Room.create(msg.roomId()), roomActorName));

          room.tell(msg.payload());

          return Behaviors.same();
        });
  }

  record Msg(String roomId, Room.Msg payload) {}
}
