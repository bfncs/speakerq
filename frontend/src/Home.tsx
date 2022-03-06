import React, { useState } from "react";

import { RouteComponentProps, useNavigate } from "@reach/router";
import styles from "Home.module.css";

export function Home(props: RouteComponentProps) {
  const [roomId, setRoomId] = useState("");
  const navigate = useNavigate();
  return (
    <form
      className={styles.wrapper}
      onSubmit={(event) => {
        event.preventDefault();
        return navigate("/" + roomId);
      }}
    >
      <header>
        <h1>✋ speakerq</h1>
      </header>
      <main>
        <div>
          <h2>Create room</h2>
          <input
            type="text"
            value={roomId}
            onChange={(e) => setRoomId(e.target.value)}
            autoFocus={true}
            required={true}
          />
        </div>
      </main>
      <footer>
        <input type="submit" value="Start" />
      </footer>
    </form>
  );
}
