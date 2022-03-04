import React, { useState } from "react";

import { RouteComponentProps, useNavigate } from "@reach/router";

export function Home(props: RouteComponentProps) {
  const [roomId, setRoomId] = useState("");
  const navigate = useNavigate();
  return (
    <div>
      <h1>Hi, this is speakerq! 👋</h1>
      <form
        onSubmit={(event) => {
          event.preventDefault();
          return navigate("/" + roomId);
        }}
      >
        <input
          type="text"
          value={roomId}
          onChange={(e) => setRoomId(e.target.value)}
        />
        <input type="submit" />
      </form>
    </div>
  );
}
