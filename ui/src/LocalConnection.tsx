import {createSignal} from "solid-js";
import {invoke} from "@tauri-apps/api";
import {SongFolder, SongFolderWithMatch} from "./types";
import SongList from "./components/SongList";
import styles from "./styling/LocalConnection.module.css";

type LocalConnectionProps = {
    localSongs: SongFolderWithMatch[],
    updateLocalSongs: (newLocalSongs: SongFolder[]) => void
}
export default (props: LocalConnectionProps) => {
    const [dirPath, setDirPath] = createSignal("");
    const [subtext, setSubtext] = createSignal("No songs loaded, choose your osu! songs directory above to get started.");

    const chooseDir = () => {
        invoke("get_local_path")
            .then(async (newPath: string) => {
                console.log(newPath);
                setDirPath(newPath);
                setSubtext("Loading... (this may take a while)");

                const newSongs = await invoke("read_local_files") as SongFolder[];
                props.updateLocalSongs(newSongs);
                setSubtext(`${newSongs.length} songs loaded`);
            })
            .catch(() => {
                console.log("Action canceled.");
            });
    }

    return <div class={styles.container}>
        <div class={styles.header}>
            <input type={"text"} placeholder={"Please choose your osu! songs directory"} value={dirPath()} readOnly/>
            <button onClick={chooseDir}>
                Choose Directory
            </button>
        </div>
        <p class={styles.subtext}>{subtext()}</p>
        <SongList songs={props.localSongs}/>
    </div>
}