use std::fmt::Debug;
use std::fs::File;
use std::io;
use std::string::String;
use std::time::Duration;
use expect_test::{Expect, expect, expect_file, ExpectFile};
use serde::Serialize;
use tokio::io::{AsyncBufReadExt, AsyncReadExt, AsyncWriteExt, BufReader};
use tokio::net::{TcpListener, TcpStream};
use tokio::time::sleep;
use crate::file_manager::SongFolderError;
use crate::networking::packets::{DisconnectPacket, DownloadRequestPacket, DownloadResponsePacket, MapListPacket, MapListRequestPacket, Packet};
use super::*;

// Mock out the Tauri front-end
#[derive(Debug, Clone)]
pub struct MockWindow {
    messages: Arc<Mutex<Vec<String>>>
}
impl MockWindow {
    pub fn new() -> Self {
        Self { messages: Arc::new(Mutex::new(Vec::new())) }
    }

    pub fn emit<S: Serialize + Clone>(&self, event: &str, payload: S) -> Result<(), ()> {
        self.messages.lock().unwrap().push(
            format!("{event}: {}", serde_json::to_string(&payload).unwrap())
        );
        Ok(())
    }

    pub fn get_messages(&self) -> Vec<String> {
        self.messages.lock().unwrap().clone()
    }
}

#[derive(Clone, serde::Serialize)]
struct DialogPayload {
    title: String,
    message: String
}
pub fn ask(window: Option<&MockWindow>, title: impl AsRef<str>, message: impl AsRef<str>) -> bool {
    if let Some(window) = window {
        window.emit("ask-dialog", DialogPayload {
            title: title.as_ref().to_string(),
            message: message.as_ref().to_string()
        }).unwrap();
    }
    true
}


/// Helper function to check test result against expected value.
fn check<T: Debug>(actual: T, expect: Expect) {
    expect.assert_debug_eq(&actual);
}

fn check_file<T: Debug>(actual: T, expect: ExpectFile) {
    expect.assert_debug_eq(&actual);
}

async fn get_test_files() -> Result<Vec<SongFolder>, SongFolderError> {
    let song_folder_path = Path::new("src/test/testsongs");
    let mut songs = file_manager::read_local_files(&song_folder_path).await?;
    songs.sort_by(|a, b| a.id.cmp(&b.id));
    Ok(songs)
}

#[tokio::test]
async fn test_read_local_files() {
    let songs = get_test_files().await;
    assert!(songs.is_ok(), "Error when trying to read songs: {:?}", songs);

    check_file(songs.unwrap(), expect_file!["./test/testsongs/serialize.txt"])
}

#[tokio::test] #[ignore]
async fn test_zip_local_files() {
    let mut songs = get_test_files().await.unwrap();
    songs.truncate(3);

    check(
        format!("Zipping files: {:?}", songs.iter().map(|song| song.name.clone()).collect::<Vec<String>>()),
        expect![[r#"
            "Zipping files: [\"DragonForce - Through The Fire And Flames\", \"ZUN - Lunatic Red Eyes _ Invisible Full Moon\", \"Caramell - Caramelldansen (Speedycake Remix)\"]"
        "#]]
    );

    let created_zip = file_manager::zip_local_files(songs);
    assert!(created_zip.is_ok(), "Error when trying to zip files: {:?}", created_zip);
    let mut created_zip = created_zip.unwrap();

    check(
        created_zip.metadata().unwrap().len(),
        expect![[r#"
            14848656
        "#]]
    );

    let mut test_zip_file = File::create("src/test/testsongs/test_zip.zip").unwrap();
    io::copy(&mut created_zip, &mut test_zip_file).unwrap();
}

async fn setup_test_packet_server() -> (TcpStream, PacketManager, Arc<Mutex<Vec<SongFolder>>>, Arc<Mutex<Vec<SongFolder>>>, MockWindow) {
    // Create packet manager
    let mut packet_server = PacketManager::new();
    let local_songs = Arc::new(Mutex::new(Vec::new()));
    let remote_songs = Arc::new(Mutex::new(Vec::new()));
    let window = MockWindow::new();
    packet_server.connect_to_test(local_songs.clone(), remote_songs.clone(), window.clone());

    // Spin up two local sockets
    let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();

    let local_socket = TcpStream::connect(addr).await.unwrap();
    let remote_socket = listener.accept().await.unwrap().0;

    packet_server.connect(local_socket);
    (remote_socket, packet_server, local_songs, remote_songs, window)
}

async fn write_packet(packet: impl Packet, remote_socket: &mut TcpStream) {
    println!("Writing {} packet...", packet.get_header());
    remote_socket.write_all(
        format!("{}\n{}\n", packet.get_header(), packet.get_data()).as_bytes()
    ).await.unwrap();
}

async fn close_connection(remote_socket: &mut TcpStream) {
    println!("Closing connection");
    write_packet(DisconnectPacket::new(), remote_socket).await;
}

#[tokio::test]
async fn test_map_list_request_packet() {
    let (mut remote_socket,
        _packet_server,
        local_songs,
        _remote_songs,
        window) = setup_test_packet_server().await;

    println!("Server setup correctly");

    let songs = get_test_files().await.unwrap();
    *local_songs.lock().unwrap() = songs;

    let packet = MapListRequestPacket::new();
    write_packet(packet, &mut remote_socket).await;

    let mut buf_reader = BufReader::new(&mut remote_socket);

    println!("Waiting for response header...");

    let mut response_header= String::new();
    buf_reader.read_line(&mut response_header).await.unwrap();
    check(
        response_header,
        expect![[r#"
            "MapListPacket\n"
        "#]]
    );

    println!("Waiting for response data...");

    let mut response_data = String::new();
    buf_reader.read_line(&mut response_data).await.unwrap();
    check(
        response_data,
        expect![[r#"
            "[{\"id\":1752,\"name\":\"DragonForce - Through The Fire And Flames\",\"checksum\":\"F9C1ED218A7E13BD3C55EE65BEE323A5B89F0015E4F0BE9A187602BBD23192DA\"},{\"id\":3030,\"name\":\"Lucky Star - Motteke! Sailor Fuku (REDALiCE Remix)\",\"checksum\":\"EB28D7411563346E4803E8095A245626DDDB28BAEDAD0C2B519DF114C2A4AA5B\"},{\"id\":3756,\"name\":\"Peter Lambert - osu! tutorial\",\"checksum\":\"96E110E2307A99D46330607EF5A8ACB51C674773B24EDEE8138273B73CD8F463\"},{\"id\":5445,\"name\":\"Hanataba - Night of Knights\",\"checksum\":\"45BEA6AC53D0397FABF4C906ED770963DE8BB4B1086C5AAA9E6116B3DC26D7DD\"},{\"id\":7380,\"name\":\"Caramell - Caramelldansen (Speedycake Remix)\",\"checksum\":\"A7E11AF5A2D094C505E66E8AE9ABEF363F533DE08CBED4B84C3C18DF6251B31D\"},{\"id\":8033,\"name\":\"ZUN - Reach for the Moon, Immortal Smoke\",\"checksum\":\"8E2A78161FD3DBAD7604D4914C54E03D1C025E235C708CB851EE19C0F82639A5\"},{\"id\":8284,\"name\":\"Hatsune Miku - Hatsune Miku no Shoushitsu\",\"checksum\":\"FA1AD88FF5AA1FEF27279C5A57695F24A49568040FA211529A5A77CC78B650C1\"},{\"id\":8299,\"name\":\"Wiklund - Whip the Blip\",\"checksum\":\"18B077618409F9092BF8699CB5AB60F1EDB19B3AA30988222155D11E1658D7AA\"},{\"id\":8830,\"name\":\"ZUN - Lunatic Red Eyes _ Invisible Full Moon\",\"checksum\":\"5A91938EAA21BA3109FA313CC9F9A80991DCC484369B3236693E6A9F8DAEA3C8\"},{\"id\":9040,\"name\":\"Wiklund - Billy Boogie\",\"checksum\":\"B92C9596AC28C1DE7A8DEF05C0FB1A5533D2EAE9FB77110051A9A81B0F315425\"},{\"id\":9197,\"name\":\"Wiklund - Joy of Living\",\"checksum\":\"5BB25EFDAFB5A9D14CDD667E698583830FCDFF495157859527E70DA86291BAD6\"}]\n"
        "#]]
    );

    close_connection(&mut remote_socket).await;

    check(
        window.get_messages(),
        expect![[r#"
            []
        "#]]
    )
}

#[tokio::test]
async fn test_map_list_packet() {
    let (mut remote_socket,
        _packet_server,
        _local_songs,
        remote_songs,
        window) = setup_test_packet_server().await;

    println!("Server setup correctly");

    let songs = get_test_files().await.unwrap();

    let packet = MapListPacket::new(songs);
    write_packet(packet, &mut remote_socket).await;

    sleep(Duration::from_millis(500)).await;
    check_file(
        remote_songs.lock().unwrap(),
        expect_file!["./test/testsongs/serialize.txt"]
    );

    close_connection(&mut remote_socket).await;

    check(
        window.get_messages(),
        expect![[r#"
            [
                "remote-songs-updated: null",
            ]
        "#]]
    )
}

#[tokio::test]
async fn test_download_request_packet() {
    let (mut remote_socket,
        _packet_server,
        local_songs,
        _remote_songs,
        window) = setup_test_packet_server().await;

    println!("Server setup correctly");

    let mut songs = get_test_files().await.unwrap();
    *local_songs.lock().unwrap() = songs.clone();

    songs.truncate(3);
    let packet = DownloadRequestPacket::new(songs);
    write_packet(packet, &mut remote_socket).await;

    let mut buf_reader = BufReader::new(&mut remote_socket);

    println!("Waiting for response header...");

    let mut response_header= String::new();
    buf_reader.read_line(&mut response_header).await.unwrap();
    check(
        response_header,
        expect![[r#"
            "DownloadResponsePacket\n"
        "#]]
    );

    println!("Waiting for response data...");

    let mut response_data = String::new();
    buf_reader.read_line(&mut response_data).await.unwrap();
    check(
        response_data,
        expect![[r#"
            "17795233\n"
        "#]]
    );

    close_connection(&mut remote_socket).await;

    check(
        window.get_messages(),
        expect![[r#"
            []
        "#]]
    )
}

#[tokio::test]
async fn test_download_response_packet() {
    let (mut remote_socket,
        _packet_server,
        _local_songs,
        _remote_songs,
        window) = setup_test_packet_server().await;

    println!("Server setup correctly");

    let mut test_zip = tokio::fs::File::open("src/test/testsongs/test_zip.zip").await.unwrap();

    let packet = DownloadResponsePacket::new(test_zip.try_clone().await.unwrap()).await;
    write_packet(packet, &mut remote_socket).await;

    let mut file = Vec::new();
    test_zip.read_to_end(&mut file).await.unwrap();
    remote_socket.write_all(&file).await.unwrap();
    remote_socket.flush().await.unwrap();

    sleep(Duration::from_secs(5)).await;
    close_connection(&mut remote_socket).await;

    check(
        window.get_messages(),
        expect![[r#"
            [
                "ask-dialog: {\"title\":\"Download Zip\",\"message\":\"You are about to download a 14 MB zip file. Continue?\"}",
                "download-started: null",
                "download-progress: 1",
                "download-progress: 2",
                "download-progress: 3",
                "download-progress: 4",
                "download-progress: 5",
                "download-progress: 6",
                "download-progress: 7",
                "download-progress: 8",
                "download-progress: 9",
                "download-progress: 10",
                "download-progress: 11",
                "download-progress: 12",
                "download-progress: 13",
                "download-progress: 14",
                "download-progress: 15",
                "download-progress: 16",
                "download-progress: 17",
                "download-progress: 18",
                "download-progress: 19",
                "download-progress: 20",
                "download-progress: 21",
                "download-progress: 22",
                "download-progress: 23",
                "download-progress: 24",
                "download-progress: 25",
                "download-progress: 26",
                "download-progress: 27",
                "download-progress: 28",
                "download-progress: 29",
                "download-progress: 30",
                "download-progress: 31",
                "download-progress: 32",
                "download-progress: 33",
                "download-progress: 34",
                "download-progress: 35",
                "download-progress: 36",
                "download-progress: 37",
                "download-progress: 38",
                "download-progress: 39",
                "download-progress: 40",
                "download-progress: 41",
                "download-progress: 42",
                "download-progress: 43",
                "download-progress: 44",
                "download-progress: 45",
                "download-progress: 46",
                "download-progress: 47",
                "download-progress: 48",
                "download-progress: 49",
                "download-progress: 50",
                "download-progress: 51",
                "download-progress: 52",
                "download-progress: 53",
                "download-progress: 54",
                "download-progress: 55",
                "download-progress: 56",
                "download-progress: 57",
                "download-progress: 58",
                "download-progress: 59",
                "download-progress: 60",
                "download-progress: 61",
                "download-progress: 62",
                "download-progress: 63",
                "download-progress: 64",
                "download-progress: 65",
                "download-progress: 66",
                "download-progress: 67",
                "download-progress: 68",
                "download-progress: 69",
                "download-progress: 70",
                "download-progress: 71",
                "download-progress: 72",
                "download-progress: 73",
                "download-progress: 74",
                "download-progress: 75",
                "download-progress: 76",
                "download-progress: 77",
                "download-progress: 78",
                "download-progress: 79",
                "download-progress: 80",
                "download-progress: 81",
                "download-progress: 82",
                "download-progress: 83",
                "download-progress: 84",
                "download-progress: 85",
                "download-progress: 86",
                "download-progress: 87",
                "download-progress: 88",
                "download-progress: 89",
                "download-progress: 90",
                "download-progress: 91",
                "download-progress: 92",
                "download-progress: 93",
                "download-progress: 94",
                "download-progress: 95",
                "download-progress: 96",
                "download-progress: 97",
                "download-progress: 98",
                "download-progress: 99",
                "download-progress: 100",
                "download-finished: null",
            ]
        "#]]
    )
}