#![cfg_attr(
all(not(debug_assertions), target_os = "windows"),
windows_subsystem = "windows"
)]

use std::path::{Path, PathBuf};
use std::sync::Mutex;
use std::time::Instant;
use file_manager::SongFolder;
use tauri::api::dialog::blocking::FileDialogBuilder;
use tauri::Manager;

mod networking;
mod file_manager;

#[derive(Debug)]
struct SynchronizerState {
    local_path: Mutex<Option<PathBuf>>,
    local_songs: Mutex<Vec<SongFolder>>,
    remote_songs: Mutex<Vec<SongFolder>>
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
async fn get_remote_files() -> Result<Vec<SongFolder>, String> {
    let path = Path::new(r"D:\cuian\Documents\Programming Projects\osu-mapsync\src\test\testsongs");
    println!("Reading all songs from {:?}", path);
    let now = Instant::now();
    let read_songs = file_manager::read_local_files(&path).await;
    let dur = now.elapsed().as_micros();
    println!("Took {:?} ms", dur);

    Ok(read_songs.unwrap())
}

#[tauri::command]
async fn connect_to_server(addr: String) -> Result<bool, String> {
    networking::connect_to_server(addr).await
        .map_err(|err| { format!("An error occurred: {err:?} ") })
}


#[tokio::main]
async fn main() {
    tauri::Builder::default()
        .manage(SynchronizerState::default())
        .invoke_handler(tauri::generate_handler![
            get_local_path, read_local_files, get_remote_files,
            connect_to_server
        ])
        .setup(|app| {
            let main_window = app.get_window("main").unwrap();

            // Pass in the main window to our server listener for message emitting
            networking::start_listening_server(main_window.clone());

            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");

    println!("End");
}
