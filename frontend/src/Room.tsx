import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "@reach/router";
import {
  uniqueNamesGenerator,
  adjectives,
  colors,
  animals,
} from "unique-names-generator";

const initialName = uniqueNamesGenerator({
  dictionaries: [colors, adjectives, animals],
  style: "capital",
  length: 2,
  separator: "",
});

type ParticipantId = string;

interface RoomState {
  participants: RoomParticipant[];
  raisedHands: ParticipantId[];
}
interface RoomParticipant {
  id: ParticipantId;
  name?: string;
}
interface RoomStateUpdated {
  type: "ROOM_STATE_UPDATED";
  room: RoomState;
}
interface Joined {
  type: "JOINED";
  participantId: ParticipantId;
}
type IncomingMessage = RoomStateUpdated | Joined;

export function Room(props: RouteComponentProps<{ roomId: string }>) {
  const [myParticipantId, setMyParticipantId] = useState<ParticipantId | null>(
    null
  );
  const [room, setRoomState] = useState<RoomState>({
    participants: [],
    raisedHands: [],
  });
  const [socket, setSocket] = useState<WebSocket | null>(null);
  useEffect(() => {
    const ws = new WebSocket(
      `${window.location.protocol === "https:" ? "wss" : "ws"}://${window.location.host}/api/rooms/${props.roomId}/ws`
    );
    setSocket(ws);

    ws.addEventListener("open", (msg) => {
      console.log("Event listener open");
      ws.send(JSON.stringify({ type: "SET_NAME", name: initialName }));
    });

    ws.addEventListener("close", (msg) => {
      console.log("Event listener closed");
    });

    ws.addEventListener("message", (msg) => {
      try {
        const data = JSON.parse(msg.data);
        console.log("Received message", data);
        if (!("type" in data)) {
          return;
        }

        switch ((data as IncomingMessage).type) {
          case "JOINED":
            console.log(
              "Successfully joined room, got participantId " +
                data.participantId
            );
            setMyParticipantId(data.participantId);
            break;
          case "ROOM_STATE_UPDATED":
            console.log("Updating room state", data.room);
            setRoomState(data.room);
        }
      } catch (e) {
        console.error("Ignoring invalid message: " + msg);
      }
    });

    return () => {
      ws.close();
    };
  }, []);

  if (!myParticipantId) return <div>Connecting</div>;
  const myHandIsRaised = room.raisedHands.includes(myParticipantId);

  return (
    <div>
      <h1>
        Room: {props.roomId}{" "}
        <span title={room.participants.map((p) => p.name).join(", ")}>
          ({room.participants.length})
        </span>
      </h1>

      <h2>Raised hands</h2>
      <ul>
        {room.raisedHands
          .flatMap((participantId) => {
            const roomParticipant = room.participants.find(
              (p) => p.id === participantId
            );
            return roomParticipant ? [roomParticipant] : [];
          })
          .map((roomParticipant) => (
            <li key={roomParticipant.id}>{roomParticipant.name}</li>
          ))}
      </ul>

      <button
        onClick={() => {
          if (!socket) return;
          if (myHandIsRaised) {
            socket.send(JSON.stringify({ type: "LOWER_HAND" }));
          } else {
            socket.send(JSON.stringify({ type: "RAISE_HAND" }));
          }
        }}
      >
        {myHandIsRaised ? "👇 Lower hand" : "✋ Raise hand"}
      </button>
    </div>
  );
}
