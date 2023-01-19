use std::fs::File;
use std::io;
use std::io::{Cursor, Read, Seek, Write};
use data_encoding::HEXUPPER;
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};
use regex::Regex;
use lazy_static::lazy_static;
use tempfile::tempfile;
use thiserror::Error;
use tokio::{sync, task};
use walkdir::WalkDir;
use zip::write::FileOptions;

#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
pub struct SongFolder {
    pub id: u64,
    pub name: String,
    pub checksum: String,
    #[serde(skip)]
    pub path: Option<PathBuf>,
}

#[derive(Error, Debug)]
pub enum SongFolderError {
    #[error("The path {0} does not correspond to a valid song folder.")]
    InvalidPath(PathBuf),
    #[error("Unable to parse folder name: {0}")]
    InvalidFolderName(String),
    #[error("An IO error occurred: {0}")]
    IOError(#[from] io::Error)
}

impl SongFolder{
    fn new(path: PathBuf) -> Result<Self, SongFolderError> {
        if !SongFolder::is_song_folder(&path) {
            return Err(SongFolderError::InvalidPath(path));
        }

        lazy_static! {
            static ref FOLDER_FORMAT: Regex = Regex::new(r"^([0-9]*) (.+ - .+)$").unwrap();
        }
        let folder_name = path.file_name().unwrap().to_str().unwrap();
        let groups = FOLDER_FORMAT.captures(folder_name)
            .ok_or(SongFolderError::InvalidFolderName(folder_name.to_string()))?;

        Ok(SongFolder {
            id: groups[1].parse::<u64>().unwrap_or(0),
            name: groups[2].to_string(),
            checksum: SongFolder::calculate_checksum(&path)?,
            path: Some(path),
        })
    }

    /// Check if the given path is a valid song folder. Requires that it is a valid directory and
    /// that it follows the format "{Beatmap number} {Artist} - {Song Title}"
    fn is_song_folder(path: &Path) -> bool {
        lazy_static! {
            static ref FOLDER_FORMAT: Regex = Regex::new(r"^([0-9]*) (.+ - .+)$").unwrap();
        }
        let folder_name = path.file_name();
        path.is_dir() &&
            folder_name.is_some() &&
            FOLDER_FORMAT.is_match(folder_name.unwrap().to_str().unwrap())
    }

    /// Calculates a checksum of the given song folder by using just the .osu files
    /// to avoid reading too much from disk. Will block as it reads from the file system.
    fn calculate_checksum(path: &Path) -> Result<String, SongFolderError> {
        let mut hasher = Sha256::new();

        for entry in path.read_dir()? {
            let file = entry?;

            // Ignore file if it doesn't end in .osu
            if !file.file_name().to_str().unwrap_or("").ends_with(".osu") {
                continue;
            }

            // Read file and update the hasher
            let mut file = File::open(file.path())?;
            let mut buffer = Vec::new();
            file.read_to_end(&mut buffer)?;
            hasher.update(buffer);
        }

        let digest = hasher.finalize();
        Ok(HEXUPPER.encode(digest.as_ref()))
    }
}

/// Reads all the beatmap folders in the given directory.
/// Splits the job across 4 threads to speed up operation.
pub async fn read_local_files(songs_dir: &Path) -> Result<Vec<SongFolder>, SongFolderError> {
    // Get all the paths that we should read
    let mut song_paths = Vec::new();
    let mut entries = tokio::fs::read_dir(songs_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        if SongFolder::is_song_folder(&path) {
            song_paths.push(path);
        }
    }

    println!("Reading {} songs...", song_paths.len());

    // Split paths between 4 threads
    let (sender, mut receiver) = sync::mpsc::channel(100);
    for chunk in song_paths.chunks(4) {
        let chunk = chunk.to_owned();
        let sender = sender.clone();
        task::spawn_blocking(move || {
            for path in chunk {
                sender.blocking_send(SongFolder::new(path)).unwrap();
            }
        });
    }
    // Drop main thread's reference to allow channel to close properly
    drop(sender);

    // Read everything in the channel, until it is closed
    let mut songs = Vec::new();
    while let Some(song) = receiver.recv().await {
        songs.push(song?);
    }
    Ok(songs)
}

fn song_to_osz(song: &SongFolder) -> io::Result<Vec<u8>> {
    let zip_data = Cursor::new(Vec::<u8>::new());
    let mut zip = zip::ZipWriter::new(zip_data);
    let zip_options = FileOptions::default();

    // Root path of the folder
    let root = song.path.as_ref().unwrap();

    // Get iterator that goes over all entries in directory
    let mut files = WalkDir::new(&root)
        .into_iter().filter_map(|e| e.ok());

    // Skip the first entry since it's the root directory
    files.next();

    let mut buf = Vec::new();
    for entry in files {
        let path = entry.path();
        let name = path.strip_prefix(root).unwrap().to_string_lossy();

        if path.is_file() {
            println!("Adding file {:?} as {:?}", path, name);
            zip.start_file(name, zip_options)?;

            let mut f = File::open(path)?;
            f.read_to_end(&mut buf)?;
            zip.write_all(&buf)?;
            buf.clear();
        } else {
            println!("Adding dir {:?} as {:?}", path, name);
            zip.add_directory(name, zip_options)?;
        }
    }
    let zip_data = zip.finish()?;

    Ok(zip_data.into_inner())
}

pub fn zip_local_files(songs_to_zip: Vec<SongFolder>) -> io::Result<File> {
    let zip_file = tempfile()?;
    let mut zip = zip::ZipWriter::new(zip_file);
    let zip_options = FileOptions::default();

    // Zip each song into .osz format, and then add the zipped song as a file in the zip
    for song in songs_to_zip {
        let mut name = song.path.as_ref().unwrap().file_name().unwrap().to_os_string();
        name.push(".osz");
        let osz_data = song_to_osz(&song)?;

        zip.start_file(name.to_string_lossy(), zip_options)?;
        zip.write_all(&osz_data[..])?;
    }
    let mut zip_file = zip.finish()?;

    // Rewind position in file to beginning to match expected behavior
    zip_file.rewind()?;
    Ok(zip_file)
}
