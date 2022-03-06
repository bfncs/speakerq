import React, { useState } from "react";
import ReactDOM from "react-dom";
import { Router } from "@reach/router";
import {
  adjectives,
  animals,
  colors,
  uniqueNamesGenerator,
} from "unique-names-generator";
import { Home } from "./Home";
import { Room } from "./Room";

const LOCALSTORAGE_KEY_USERNAME = "username";

const initialName =
  window.localStorage.getItem(LOCALSTORAGE_KEY_USERNAME) ||
  uniqueNamesGenerator({
    dictionaries: [colors, adjectives, animals],
    style: "capital",
    length: 2,
    separator: "",
  });

function App() {
  const [userName, setUserName] = useState(initialName);
  return (
    <Router style={{ height: "100%" }}>
      <Home path="/" />
      <Room
        path="/:roomId"
        userName={userName}
        setUserName={(name) => {
          window.localStorage.setItem(LOCALSTORAGE_KEY_USERNAME, name);
          setUserName(name);
        }}
      />
    </Router>
  );
}

ReactDOM.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
  document.getElementById("root")
);
