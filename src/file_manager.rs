use std::fs::File;
use std::io;
use std::io::{BufReader, Read};
use data_encoding::HEXUPPER;
use sha2::{Digest, Sha256};
use std::path::{Path, PathBuf};
use regex::Regex;
use lazy_static::lazy_static;
use thiserror::Error;

#[derive(Debug)]
pub struct SongFolder {
    id: u64,
    name: String,
    path: PathBuf,
    checksum: String
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
            path,
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
    /// to avoid reading too much from disk.
    fn calculate_checksum(path: &Path) -> Result<String, SongFolderError> {
        let mut hasher = Sha256::new();
        let mut buffer = [0; 1024];

        for entry in path.read_dir()? {
            let file = entry?;

            // Ignore file if it doesn't end in .osu
            if !file.file_name().to_str().unwrap_or("").ends_with(".osu") {
                continue;
            }

            // Add file bytes to the hasher
            let file = File::open(file.path())?;
            let mut reader = BufReader::new(file);
            loop {
                let count = reader.read(&mut buffer)?;
                if count == 0 { break }
                hasher.update(&buffer[..count]);
            }
        }

        let digest = hasher.finalize();
        Ok(HEXUPPER.encode(digest.as_ref()))
    }
}

pub fn read_local_files(songs_folder: &Path) -> Result<Vec<SongFolder>, SongFolderError> {
    let mut songs = Vec::new();
    for entry in songs_folder.read_dir()? {
        let path = entry?.path();
        println!("{:?}", path);
        if SongFolder::is_song_folder(&path) {
            songs.push(SongFolder::new(path)?);
        }
    }
    Ok(songs)
}