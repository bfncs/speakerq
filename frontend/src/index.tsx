import React from "react";
import ReactDOM from "react-dom";
import { Router } from "@reach/router";
import { Home } from "./Home";
import { Room } from "./Room";

ReactDOM.render(
  <React.StrictMode>
    <Router style={{ height: "100%" }}>
      <Home path="/" />
      <Room path="/:roomId" />
    </Router>
  </React.StrictMode>,
  document.getElementById("root")
);
