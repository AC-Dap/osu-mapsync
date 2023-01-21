#![cfg_attr(
all(not(debug_assertions), target_os = "windows"),
windows_subsystem = "windows"
)]

use std::path::PathBuf;
use std::sync::{Arc, Mutex};
use std::time::Instant;
use file_manager::SongFolder;
use tauri::api::dialog::blocking::FileDialogBuilder;
use tauri::Manager;
use networking::packets::PacketManager;
use crate::networking::packets::{DownloadRequestPacket, MapListRequestPacket};

mod networking;
mod file_manager;
#[cfg(test)]
mod test;

#[derive(Debug)]
struct SynchronizerState {
    local_path: Mutex<Option<PathBuf>>,
    local_songs: Arc<Mutex<Vec<SongFolder>>>,
    remote_songs: Arc<Mutex<Vec<SongFolder>>>,
    packet_manager: Arc<Mutex<PacketManager>>
}

impl SynchronizerState {
    fn new() -> Self {
        Self {
            local_path: Mutex::new(None),
            local_songs: Arc::new(Mutex::new(Vec::new())),
            remote_songs: Arc::new(Mutex::new(Vec::new())),
            packet_manager: Arc::new(Mutex::new(PacketManager::new()))
        }
    }
}

#[tauri::command]
async fn get_local_path(state: tauri::State<'_, SynchronizerState>) -> Result<String, ()> {
    let folder_path = FileDialogBuilder::new()
        .set_title("Choose your osu! Song directory")
        .pick_folder();

    if folder_path.is_some() {
        println!("New folder path: {:?}", folder_path);
        let mut local_path = state.local_path.lock().unwrap();
        *local_path = folder_path;
        Ok(local_path.as_ref().unwrap().to_string_lossy().to_string())
    } else {
        println!("Action canceled");
        Err(())
    }
}

#[tauri::command]
async fn read_local_files(state: tauri::State<'_, SynchronizerState>) -> Result<Vec<SongFolder>, String> {
    let local_path = state.local_path.lock().unwrap().clone();
    if let Some(path) = local_path {
        println!("Reading all songs from {:?}", path);
        let now = Instant::now();
        let read_songs = file_manager::read_local_files(&path).await;
        let dur = now.elapsed().as_micros();
        println!("Took {:?} ms", dur);

        return match read_songs {
            Ok(songs) => {
                *state.local_songs.lock().unwrap() = songs.clone();
                Ok(songs)
            },
            Err(err) => {
                Err(format!("An error occurred while trying to read the files: {:?}", err))
            }
        }
    }

    Err("No local path specified.".to_string())
}

#[tauri::command]
async fn get_remote_files(state: tauri::State<'_, SynchronizerState>) -> Result<Vec<SongFolder>, ()> {
    let remote_songs = state.remote_songs.lock().unwrap();
    Ok(remote_songs.clone())
}

#[tauri::command]
async fn connect_to_server(addr: String, state: tauri::State<'_, SynchronizerState>) -> Result<bool, String> {
    networking::connect_to_server(addr, &state.packet_manager).await
        .map_err(|err| { format!("An error occurred: {err:?} ") })
}

#[tauri::command]
fn request_remote_files(state: tauri::State<'_, SynchronizerState>) {
    state.packet_manager.lock().unwrap().send_packet(Box::new(MapListRequestPacket::new()));
}

#[tauri::command]
fn request_download(songs_to_request: Vec<SongFolder>, state: tauri::State<'_, SynchronizerState>) {
    state.packet_manager.lock().unwrap().send_packet(Box::new(DownloadRequestPacket::new(songs_to_request)));
}


#[tokio::main]
async fn main() {
    tauri::Builder::default()
        .manage(SynchronizerState::new())
        .invoke_handler(tauri::generate_handler![
            get_local_path, read_local_files, get_remote_files,
            connect_to_server, request_remote_files, request_download
        ])
        .setup(|app| {
            let state = app.state::<SynchronizerState>();
            let main_window = app.get_window("main").unwrap();

            // Pass in the main window to our server listener for message emitting
            networking::start_listening_server(main_window.clone(), state.packet_manager.clone());

            // Let the packet manager know about our app so it can communicate with it
            state.packet_manager.lock().unwrap()
                .connect_to_app(state.local_songs.clone(), state.remote_songs.clone(), main_window);

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");

    println!("End");
}
