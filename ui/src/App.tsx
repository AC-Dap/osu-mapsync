import type {Component} from 'solid-js';
import RemoteConnection from "./RemoteConnection";
import {createMemo, createSignal} from "solid-js";
import {SongFolder, SongFolderMatch, SongFolderWithMatch} from "./types";
import LocalConnection from "./LocalConnection";
import styles from "./styling/App.module.css";
import SyncPanel from "./SyncPanel";

const App: Component = () => {
    const [localSongs, setLocalSongs] = createSignal<SongFolderWithMatch[]>([]);
    const [remoteSongs, setRemoteSongs] = createSignal<SongFolderWithMatch[]>([]);
    const missingSongs = createMemo(() => (
        remoteSongs().filter((song) => song.match === "Similar" || song.match === "Missing")
    ));

    const updateMatches = function (newLocalSongs: SongFolder[], newRemoteSongs: SongFolder[]) {
        // If one of the sources is empty, we should set our match to "None" rather than "Missing" by default
        let defaultMatch: SongFolderMatch = "Missing";
        if (newLocalSongs.length == 0 || newRemoteSongs.length == 0) {
            defaultMatch = "None";
        }

        // Try and find a match for each song in newLocalSongs
        const newLocalMatches: SongFolderWithMatch[] = newLocalSongs.map((song) => ({song, match: defaultMatch}));
        const newRemoteMatches: SongFolderWithMatch[] = newRemoteSongs.map((song) => ({song, match: defaultMatch}));
        newLocalSongs.forEach((song, i) => {
            const rI = newRemoteSongs.findIndex((rSong) => rSong.id === song.id && rSong.name === song.name);

            if (rI !== -1) {
                newLocalMatches[i].match = newRemoteMatches[rI].match
                    = (song.checksum === newRemoteSongs[rI].checksum) ? "Direct" : "Similar";
            }
        });

        console.log("New song lists:");
        console.log(newLocalMatches);
        console.log(newRemoteMatches);
        setLocalSongs(newLocalMatches);
        setRemoteSongs(newRemoteMatches);
    }

    const updateLocalSongs = function (newLocalSongs: SongFolder[]) {
        // Pull out just the songs from remoteSongs(). Inefficient but should be fine since it's infrequent.
        const newRemoteSongs = remoteSongs().map((song) => song.song);
        updateMatches(newLocalSongs, newRemoteSongs);
    }
    const updateRemoteSongs = function (newRemoteSongs: SongFolder[]) {
        // Pull out just the songs from localSongs(). Inefficient but should be fine since it's infrequent.
        const newLocalSongs = localSongs().map((song) => song.song);
        updateMatches(newLocalSongs, newRemoteSongs);
    }

    return <div class={styles.container}>
        <div class={styles.songSources}>
            <LocalConnection localSongs={localSongs()} updateLocalSongs={updateLocalSongs}/>
            <RemoteConnection remoteSongs={remoteSongs()} updateRemoteSongs={updateRemoteSongs}/>
        </div>
        <SyncPanel songsToSync={missingSongs()}/>
    </div>
};

export default App;
