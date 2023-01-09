import {createSignal, For} from "solid-js";
import {invoke} from "@tauri-apps/api";

export default () => {
    const [dirPath, setDirPath] = createSignal("Please choose your osu! songs directory");
    const [localSongs, setLocalSongs] = createSignal<any[]>([]);
    const [remoteSongs, setRemoteSongs] = createSignal<any[]>([]);

    const annotateSongs = (local, remote) => {
        let annotatedLocal = local.map((song) => ({shared: 'red', ...song}));
        let annotatedRemote = remote.map((song) => ({shared: 'red', ...song}));

        annotatedLocal.forEach((song) => {
            let remoteMatch = annotatedRemote.find((rSong) => rSong.id === song.id);
            if(remoteMatch) {
                song.shared = remoteMatch.shared = (song.checksum === remoteMatch.checksum) ? 'green' : 'orange';
            }
        });


        console.log(annotatedLocal);
        console.log(annotatedRemote);

        setLocalSongs(annotatedLocal);
        setRemoteSongs(annotatedRemote);
    }
    const chooseDir = () => {
        invoke("get_local_path")
            .then(async (newPath: string) => {
                console.log(newPath);
                setDirPath(newPath);

                const newSongs = await invoke("read_local_files") as any[];
                console.log(newSongs);
                // setLocalSongs(newSongs);

                annotateSongs(newSongs, remoteSongs());
            })
            .catch(() => {
                console.log("Action canceled.");
            });
    }

    const loadRemote = async () => {
        const newRemoteSongs = await invoke("get_remote_files") as any[];
        console.log(newRemoteSongs);
        // setRemoteSongs(newRemoteSongs);

        annotateSongs(localSongs(), newRemoteSongs);
    }

    return <div style={{display: "flex"}}>
        <div>
            <input type={"text"} value={dirPath()} readonly style={{width: "500px"}}/>
            <button onclick={chooseDir}>
                Choose Directory
            </button>
            <For each={localSongs()}>{(song) =>
                <p style={{"background-color": (remoteSongs().length === 0)? 'transparent' : song.shared}}>{song.name}</p>
            }</For>
        </div>

        <div>
            <button onclick={loadRemote}>
                Load Remote
            </button>
            <For each={remoteSongs()}>{(song) =>
                <p style={{"background-color": (localSongs().length === 0)? 'transparent' : song.shared}}>{song.name}</p>
            }</For>
        </div>
    </div>;
};
