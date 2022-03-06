import React, { useState } from "react";
import styles from "Settings.module.css";

export function Settings(props: {
  isOpen: boolean;
  onClose: () => void;
  userName: string;
  setUserName: (userName: string) => void;
}) {
  const [dialogName, setDialogName] = useState(props.userName);
  if (!props.isOpen) {
    return <></>;
  }
  return (
    <div className={styles.wrapper} onClick={props.onClose}>
      <dialog
        open
        className={styles.dialog}
        onClick={(e) => e.stopPropagation()}
      >
        <h2>Change name</h2>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            props.setUserName(dialogName);
            props.onClose();
          }}
        >
          <input
            type="text"
            value={dialogName}
            onChange={(e) => setDialogName(e.target.value)}
            autoFocus={true}
            required={true}
          />
          <input type="submit" />
        </form>
      </dialog>
    </div>
  );
}
