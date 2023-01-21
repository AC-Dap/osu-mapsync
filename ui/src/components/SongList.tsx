import {SongFolderMatch, SongFolderWithMatch} from "../types";
import {For, JSX, mergeProps, splitProps} from "solid-js";
import styles from "../styling/SongList.module.css";

type SongProp = SongFolderWithMatch;

type SongListProps = {
    songs: SongFolderWithMatch[]
} & JSX.HTMLAttributes<HTMLDivElement>

function matchToColor(match: SongFolderMatch) {
    switch (match) {
        case "None": return "transparent";
        case "Direct": return "green";
        case "Similar": return "yellow";
        case "Missing": return "red";
    }
}

function Song(props : SongProp) {
    return <div class={styles.songContainer}>
        <p class={styles.songName}>{props.song.name}</p>
        <span class={styles.dot} style={{"background-color": matchToColor(props.match)}}/>
    </div>
}

export default (props: SongListProps) => {
    let [songs, divProps] = splitProps(props, ["songs"]);
    return <div {...divProps } class={`${styles.container} ${divProps.class || ""}`}>
        <For each={songs.songs}>
            {(song) => <Song song={song.song} match={song.match} />}
        </For>
    </div>
}
