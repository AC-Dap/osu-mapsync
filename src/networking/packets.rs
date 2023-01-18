use std::any::Any;
use std::io::Write;
use std::fmt::Formatter;
use std::sync::{Arc, Mutex};
use tauri::{Window, Wry};
use tauri::api::dialog::blocking::{ask, FileDialogBuilder};
use tokio::fs::File;
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader, BufWriter};
use tokio::net::tcp::{OwnedReadHalf, OwnedWriteHalf};
use tokio::net::TcpStream;
use tokio::sync::{mpsc};
use crate::file_manager::{SongFolder, zip_local_files};

pub trait Packet: Send + Sync {
    /// A distinct header to identify the packet type
    fn get_header(&self) -> &'static str;
    /// A string representation of the important data associated with the packet.
    /// Should **NOT** include newlines, as they are used to mark the end of the data segment.
    fn get_data(&self) -> String;
    /// A way to get a packet struct with easy-to-manipulate data based on the string
    /// representation received over the socket connection.
    fn deserialize(raw_data: String) -> Self where Self:Sized;
    /// Allows recasting from dyn Packet to a specific packet
    fn as_any(&mut self) -> &mut dyn Any;
}
impl std::fmt::Debug for dyn Packet {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        write!(f, "Packet {{ header: {}, data: {} }}", self.get_header(), self.get_data())
    }
}

pub struct MapListRequestPacket;
impl MapListRequestPacket {
    const HEADER: &'static str = "MapListRequestPacket";

    fn new() -> Self {
        Self {}
    }
}
impl Packet for MapListRequestPacket {
    fn get_header(&self) -> &'static str {
        Self::HEADER
    }

    fn get_data(&self) -> String {
        String::new()
    }

    fn deserialize(_: String) -> Self {
        Self {}
    }

    fn as_any(&mut self) -> &mut dyn Any {
        self
    }
}

pub struct MapListPacket {
    pub map_list: Vec<SongFolder>
}
impl MapListPacket {
    const HEADER: &'static str = "MapListPacket";

    fn new(map_list: Vec<SongFolder>) -> Self {
        Self { map_list }
    }
}
impl Packet for MapListPacket {
    fn get_header(&self) -> &'static str {
        Self::HEADER
    }

    fn get_data(&self) -> String {
        serde_json::to_string(&self.map_list).unwrap()
    }

    fn deserialize(raw_data: String) -> Self {
        Self {
            map_list: serde_json::from_str(&raw_data).unwrap()
        }
    }

    fn as_any(&mut self) -> &mut dyn Any {
        self
    }
}

pub struct DownloadRequestPacket {
    pub requested_maps: Vec<SongFolder>
}
impl DownloadRequestPacket {
    const HEADER: &'static str = "DownloadRequestPacket";

    fn new(requested_maps: Vec<SongFolder>) -> Self {
        Self { requested_maps }
    }
}
impl Packet for DownloadRequestPacket {
    fn get_header(&self) -> &'static str {
        Self::HEADER
    }

    fn get_data(&self) -> String {
        serde_json::to_string(&self.requested_maps).unwrap()
    }

    fn deserialize(raw_data: String) -> Self {
        Self {
            requested_maps: serde_json::from_str(&raw_data).unwrap()
        }
    }

    fn as_any(&mut self) -> &mut dyn Any {
        self
    }
}

pub struct DownloadResponsePacket {
    pub zipped_maps: Option<File>,
    zip_size: u64
}
impl DownloadResponsePacket {
    const HEADER: &'static str = "DownloadResponsePacket";

    async fn new(zipped_maps: File) -> Self {
        Self {
            zip_size: zipped_maps.metadata().await.unwrap().len(),
            zipped_maps: Some(zipped_maps)
        }
    }
}
impl Packet for DownloadResponsePacket {
    fn get_header(&self) -> &'static str {
        Self::HEADER
    }

    fn get_data(&self) -> String {
        self.zip_size.to_string()
    }

    fn deserialize(raw_data: String) -> Self {
        Self { zip_size: raw_data.parse::<u64>().unwrap(), zipped_maps: None }
    }

    fn as_any(&mut self) -> &mut dyn Any {
        self
    }
}

pub struct DisconnectPacket;
impl DisconnectPacket {
    const HEADER: &'static str = "DisconnectPacket";

    fn new() -> Self {
        Self {}
    }
}
impl Packet for DisconnectPacket{
    fn get_header(&self) -> &'static str {
        Self::HEADER
    }

    fn get_data(&self) -> String {
        String::new()
    }

    fn deserialize(_: String) -> Self {
        DisconnectPacket {}
    }

    fn as_any(&mut self) -> &mut dyn Any {
        self
    }
}

#[derive(Debug)]
struct AppState {
    local_songs: Arc<Mutex<Vec<SongFolder>>>,
    remote_songs: Arc<Mutex<Vec<SongFolder>>>,
    app_window: Window<Wry>
}

#[derive(Debug)]
pub struct PacketManager {
    app_state: Option<AppState>,
    packet_queue: Option<mpsc::Sender<Box<dyn Packet>>>
}

impl PacketManager {
    pub fn new() -> Self {
        Self { app_state: None, packet_queue: None}
    }

    pub fn connect_to_app(&mut self, local_songs: Arc<Mutex<Vec<SongFolder>>>, remote_songs: Arc<Mutex<Vec<SongFolder>>>, app_window: Window<Wry>) {
        self.app_state = Some(AppState{ local_songs, remote_songs, app_window });
    }

    pub fn connect(&mut self, connection: TcpStream) {
        if self.app_state.is_none() {
            panic!("[Packet Manager] Connecting to socket before app is connected!");
        }

        // If there is a current connection, this will disconnect the writing stream, and the
        // connected server will then their own disconnect packet to close the reading stream
        // This ensures that if we're in the middle of reading something, it will complete
        self.send_packet(Box::new(DisconnectPacket::new()));

        // Create new packet queue
        let (sender, receiver) = mpsc::channel(10);
        self.packet_queue = Some(sender.clone());

        let (read_stream, write_stream) = connection.into_split();
        self.start_reading_thread(read_stream, sender);
        self.start_writing_thread(write_stream, receiver);
    }

    fn start_reading_thread(&self, stream: OwnedReadHalf, packet_queue: mpsc::Sender<Box<dyn Packet>>) {
        let local_songs = self.app_state.as_ref().unwrap().local_songs.clone();
        let remote_songs = self.app_state.as_ref().unwrap().remote_songs.clone();
        let window = self.app_state.as_ref().unwrap().app_window.clone();

        tokio::spawn(async move {
            let mut buf_reader = BufReader::new(stream);

            loop {
                let mut packet_header = String::new();
                buf_reader.read_line(&mut packet_header).await.unwrap();

                let mut raw_data = String::new();
                buf_reader.read_line(&mut raw_data).await.unwrap();

                match packet_header.as_str() {
                    MapListRequestPacket::HEADER => {
                        println!("Map List Requested");
                        // Send back packet of currently loaded local songs
                        let local_songs = local_songs.lock().unwrap().clone();
                        let _ = packet_queue.send(Box::new(MapListPacket::new(local_songs)));
                    },
                    MapListPacket::HEADER => {
                        println!("Map List Received");
                        // Update list of remote songs to what we just received
                        let new_remote_songs = MapListPacket::deserialize(raw_data);
                        *remote_songs.lock().unwrap() = new_remote_songs.map_list;

                        // Let front-end know that list has been updated
                        window.emit("remote-songs-updated", {}).unwrap();
                    },
                    DownloadRequestPacket::HEADER => {
                        println!("Download Requested");
                        // Zip up the files requested and send them back in a response packet
                        let maps_requested = DownloadRequestPacket::deserialize(raw_data);

                        // Get the corresponding local_song structs
                        let songs_to_zip = {
                            let local_songs = local_songs.lock().unwrap();
                            maps_requested.requested_maps.iter()
                                .map(|song| {
                                    local_songs
                                        .iter()
                                        .find(|local_song| song.id == local_song.id && song.name == local_song.name)
                                        .unwrap()
                                        .clone()
                                })
                                .collect()
                        };

                        let zipped_maps = zip_local_files(songs_to_zip).unwrap();
                        let zipped_maps = File::from_std(zipped_maps);
                        let _ = packet_queue.send(Box::new(DownloadResponsePacket::new(zipped_maps).await));
                    },
                    DownloadResponsePacket::HEADER => {
                        println!("Download Received");
                        // Ask user where to store the files, then read zip file and unzip to folder
                        let file_size = DownloadResponsePacket::deserialize(raw_data).zip_size;

                        let should_download = ask(Some(&window), "Download Zip",
                            format!("You are about to download a {} MB zip file. Continue?", file_size / 1_000_000));
                        if should_download {
                            let file_path = FileDialogBuilder::new()
                                .add_filter("Zip file", &["zip"])
                                .pick_file();

                            if let Some(file_path) = file_path {
                                let mut file = File::create(file_path).await.unwrap();
                                let mut file_data = buf_reader.take(file_size);

                                // Don't try and read the entire file into memory just in case it's large
                                // TODO: Emit messages to the front-end regarding progress of download
                                let mut buf = [0; 1024];
                                while file_data.limit() > 0 {
                                    let n = file_data.read(&mut buf[..]).await.unwrap();
                                    file.write_all(&buf[..n]).await.unwrap();
                                }

                                // Once we've done, consume the Take wrapper
                                buf_reader = file_data.into_inner();
                            }
                        }
                    },
                    DisconnectPacket::HEADER => {
                        println!("Disconnecting stream");
                        // Send disconnect packet to writing thread to get it to disconnect as well
                        // Getting an error is OK since that means the writing thread has already disconnected
                        let _ = packet_queue.send(Box::new(DisconnectPacket::new()));
                        break;
                    },
                    _ => {
                        println!("Unexpected header received: {}", packet_header)
                    }
                }
            }

            println!("Read stream disconnected");
        });
    }

    fn start_writing_thread(&self, stream: OwnedWriteHalf, mut packet_queue: mpsc::Receiver<Box<dyn Packet>>) {
        tokio::spawn(async move {
            let mut buf_writer = BufWriter::new(stream);

            loop {
                let mut packet = packet_queue.recv().await.unwrap();

                let mut buf = Vec::new();
                write!(&mut buf, "{}\n{}\n", packet.get_header(), packet.get_data()).unwrap();
                buf_writer.write_all(&buf[..]).await.unwrap();

                let header = packet.get_header();
                match header {
                    DownloadResponsePacket::HEADER => {
                        println!("Download Received");
                        // Write the zip file to the stream
                        let packet = packet.as_any()
                            .downcast_mut::<DownloadResponsePacket>().unwrap();

                        // Don't try and read the entire file into memory just in case it's large
                        let zip_file = packet.zipped_maps.as_mut().unwrap();
                        let mut buf = [0; 1024];
                        loop {
                            let n = zip_file.read(&mut buf[..]).await.unwrap();
                            if n == 0 {
                                break;
                            }
                            buf_writer.write_all(&buf[..n]).await.unwrap();
                        }
                    },
                    DisconnectPacket::HEADER => {
                        println!("Disconnect Requested");
                        break;
                    },
                    _ => {
                        // We don't need to do anything special for other kinds of packets
                    }
                }

                // Flush writer to make sure the entire packet is written
                buf_writer.flush().await.unwrap();
            }

            println!("Write stream disconnected");
        });
    }

    /// Spawn a tokio task to eventually send our packet in the queue
    /// Here we spawn a task to avoid making send_packet async, which would
    /// make things annoying since every use of PacketManager will be behind a mutex
    pub fn send_packet(&self, packet: Box<dyn Packet>) {
        if let Some(packet_queue) = &self.packet_queue {
            let packet_queue = packet_queue.clone();
            tokio::spawn(async move {
                packet_queue.send(packet).await.unwrap();
            });
        }
    }
}