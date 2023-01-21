import {createEffect, createSignal, onCleanup} from "solid-js";
import {invoke} from "@tauri-apps/api";
import {SongFolder, SongFolderWithMatch} from "./types";
import SongList from "./components/SongList";
import {listen} from "@tauri-apps/api/event";
import styles from "./styling/RemoteConnection.module.css";

type RemoteConnectionProps = {
    remoteSongs: SongFolderWithMatch[],
    updateRemoteSongs: (newLocalSongs: SongFolder[]) => void
}
export default (props: RemoteConnectionProps) => {
    const [localAddr, setLocalAddr] = createSignal("");
    const [remoteAddr, setRemoteAddr] = createSignal("");

    const connect = () => {
        invoke("connect_to_server", {addr: remoteAddr()})
            .then((accepted) => {
                console.log("Connection accepted:", accepted);
                invoke("request_remote_files");
            })
            .catch((err) => {
                console.log("Some error occurred:");
                console.error(err);
            })
    }

    createEffect(async () => {
        const unlisten = await listen("remote-songs-updated", async () => {
            const remoteSongs = await invoke("get_remote_files", {}) as SongFolder[];
            props.updateRemoteSongs(remoteSongs);
        });

        onCleanup(unlisten);
    });

    return <div class={styles.container}>
        <div class={styles.header}>
            <input type={"text"} placeholder={"Remote server address..."} oninput={(e) => setRemoteAddr(e.currentTarget.value)}/>
            <button onclick={connect}>Connect</button>
            <button onclick={() => invoke("request_remote_files")}>Refresh</button>
        </div>
        <p class={styles.subtext}>{props.remoteSongs.length} songs loaded</p>
        <SongList songs={props.remoteSongs}/>
    </div>
}