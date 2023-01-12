import {createSignal} from "solid-js";
import {invoke} from "@tauri-apps/api";

export default () => {
    const [localAddr, setLocalAddr] = createSignal("");
    const [remoteAddr, setRemoteAddr] = createSignal("");

    const connect = () => {
        invoke("connect_to_server", {addr: remoteAddr()})
            .then((accepted) => {
                console.log("Connection accepted:", accepted);
            })
            .catch((err) => {
                console.log("Some error occurred:");
                console.error(err);
            })
    }

    return <div>
        {/*@ts-ignore*/}
        <input type={"text"} placeholder={"Remote server address..."} oninput={(e) => setRemoteAddr(e.target.value)}/>
        <button onclick={connect}>Connect</button>
    </div>
}