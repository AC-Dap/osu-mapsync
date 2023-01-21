import {SongFolderWithMatch} from "./types";
import SongList from "./components/SongList";
import styles from "./styling/SyncPanel.module.css";
import {createEffect, createSignal, onCleanup, Show} from "solid-js";
import {invoke} from "@tauri-apps/api";
import {listen} from "@tauri-apps/api/event";

type SyncPanelProps = {
    songsToSync: SongFolderWithMatch[]
}

export default (props: SyncPanelProps) => {
    const [expanded, setExpanded] = createSignal(false);
    const [syncing, setSyncing] = createSignal(false);
    const [syncPercentage, setSyncPercentage] = createSignal(0);

    const onSyncPress = () => {
        if (!expanded()) {
            setExpanded(true);
            return;
        }
        if (syncing()) return;

        console.log(props.songsToSync);

        // Just pull out the songs before sending to the backend
        const songs = props.songsToSync.map((song) => song.song);
        invoke("request_download", {songsToRequest: songs});
    }

    createEffect(async () => {
        let unlisten = await listen("download-started", () => {
            setSyncing(true);
        });
        onCleanup(unlisten);

        unlisten = await listen("download-progress", (e) => {
            console.log(e.payload);
            setSyncPercentage(e.payload as number);
        });
        onCleanup(unlisten);

        unlisten = await listen("download-finished", () => {
            setSyncing(false);
            setExpanded(false);
        });
        onCleanup(unlisten);
    });

    return <div class={styles.container}>
        <div class={styles.popupPanel}>
            <Show when={expanded()}>
                <button class={styles.collapseButton} onclick={() => setExpanded(false)}>Collapse</button>
                <SongList songs={props.songsToSync} class={styles.songList}/>
            </Show>
        </div>
        <button disabled={syncing()} onclick={onSyncPress} class={styles.syncButton}>
            <Show when={syncing()} fallback={"Sync"}>
                <span style={{width: `${syncPercentage()}%`}}/>
                Sync ({syncPercentage()}%)
            </Show>
        </button>
    </div>
}