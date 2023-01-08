// use std::path::Path;
// use std::time::Instant;
//
// mod file_manager;
//
// fn main() {
//     let test_dir = Path::new(r"D:\cuian\Games\Osu\Songs");
//     println!("Reading all songs from {:?}", test_dir);
//     let now = Instant::now();
//     let test_dir_songs = file_manager::read_local_files(test_dir);
//     let dur = now.elapsed().as_micros();
//     println!("Took {:?} ms", dur);
//
//     match test_dir_songs {
//         Ok(songs) => {
//             println!("Read {} songs", songs.len());
//             songs.into_iter().for_each(|song| println!("\t{:?}", song))
//         },
//         Err(err) => {
//             println!("An error occurred while trying to read the files: {:?}", err);
//         }
//     }
// }

#![cfg_attr(
all(not(debug_assertions), target_os = "windows"),
windows_subsystem = "windows"
)]

fn main() {
    tauri::Builder::default()
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
