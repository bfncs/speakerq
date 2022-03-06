import React, { useEffect, useState } from "react";
import { RouteComponentProps } from "@reach/router";
import styles from "Room.module.css";
import { Settings } from "./Settings";

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

export function Room(
  props: RouteComponentProps<{
    roomId: string;
  }> & {
    userName: string;
    setUserName: (userName: string) => void;
  }
) {
  const [isSettingsDialogOpen, setIsSettingsDialogOpen] = useState(false);
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
      `${window.location.protocol === "https:" ? "wss" : "ws"}://${
        window.location.host
      }/api/rooms/${props.roomId}/ws`
    );
    setSocket(ws);

    ws.addEventListener("open", (msg) => {
      ws.send(JSON.stringify({ type: "SET_NAME", name: props.userName }));
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

  const hasRaisedHands = room.raisedHands.length > 0;
  console.log({ hasRaisedHands, raisedHands: room.raisedHands });
  return (
    <div className={styles.wrapper}>
      <Settings
        isOpen={isSettingsDialogOpen}
        onClose={() => {
          setIsSettingsDialogOpen(false);
        }}
        userName={props.userName}
        setUserName={(userName) => {
          if (socket) {
            socket.send(JSON.stringify({ type: "SET_NAME", name: userName }));
          }
          props.setUserName(userName);
        }}
      />
      <header>
        <h1 title={room.participants.map((p) => p.name).join(", ")}>
          #{props.roomId}
          {"{"}
          {room.participants.length}
          {"}"}
        </h1>
        <button
          className={styles.toggleSettings}
          onClick={() => setIsSettingsDialogOpen(true)}
        >
          ⚙️
        </button>
      </header>
      <main>
        <>
          <div className={styles.handWrapper}>
            <span
              className={`${styles.hand} ${
                hasRaisedHands ? styles.handAnimated : ""
              }`}
            >
              <span className={styles.handInner}>✋</span>
            </span>
          </div>
          <div className={styles.speakerList}>
            {hasRaisedHands && (
              <ol>
                {room.raisedHands
                  .flatMap((participantId) => {
                    const roomParticipant = room.participants.find(
                      (p) => p.id === participantId
                    );
                    return roomParticipant ? [roomParticipant] : [];
                  })
                  .map((roomParticipant) => (
                    <li
                      key={roomParticipant.id}
                      className={
                        roomParticipant.id === myParticipantId
                          ? styles.speakerSelf
                          : ""
                      }
                    >
                      {roomParticipant.name}
                    </li>
                  ))}
              </ol>
            )}
          </div>
        </>
      </main>
      <footer>
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
          {myHandIsRaised ? "Put hand down" : "Raise hand"}
        </button>
      </footer>
    </div>
  );
}
