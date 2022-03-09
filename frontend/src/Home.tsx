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
            onChange={(e) =>
              setRoomId(
                e.target.value
                  .toLowerCase()
                  .replaceAll(/\s+/g, "-")
                  .replaceAll(/[^-\w]+/g, "")
              )
            }
            autoFocus={true}
            required={true}
          />
        </div>
        <a href="https://github.com/bfncs/speakerq">src</a>
      </main>
      <footer>
        <input type="submit" value="Start" />
      </footer>
    </form>
  );
}
