package us.byteb.speakerq;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(IncomingMessage.SetName.class),
  @JsonSubTypes.Type(IncomingMessage.RaiseHand.class),
  @JsonSubTypes.Type(IncomingMessage.LowerHand.class)
})
public sealed interface IncomingMessage {
  @JsonTypeName("SET_NAME")
  record SetName(String name) implements IncomingMessage {}

  @JsonTypeName("RAISE_HAND")
  record RaiseHand() implements IncomingMessage {}

  @JsonTypeName("LOWER_HAND")
  record LowerHand() implements IncomingMessage {}
}
