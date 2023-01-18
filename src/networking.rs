use std::sync::{Arc, Mutex};
use tauri::{Window, Wry};
use tauri::api::dialog::blocking::ask;
use thiserror::Error;
use tokio::io;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};
use crate::networking::packets::PacketManager;

pub mod packets;

#[repr(u8)]
enum ServerConnectMessage {
    ALLOWED = 255,
    DENIED = 0
}

#[derive(Error, Debug)]
pub enum NetworkingError {
    #[error("An error occurred when trying to connect to remote address {0}")]
    ConnectionError(String),
    #[error("An error occurred when trying to read from the socket stream: {0}")]
    ReadError(io::Error),
    #[error("An error occurred when trying to write to the socket stream: {0}")]
    WriteError(io::Error),
    #[error("An unexpected message was read from the socket: {0}")]
    UnexpectedMessage(String),
    #[error("An unexpected IO error occurred: {0}")]
    IOError(#[from] io::Error)
}

async fn handle_incoming_connection(listener: &TcpListener, app_window: &Window<Wry>, packet_manager: &Mutex<PacketManager>) -> Result<(), NetworkingError> {
    let (mut socket, addr) = listener.accept().await?;

    // Ask user to see if we should allow connection from addr
    let accept = ask(Some(app_window), "Accept connection",
                     format!("Accept incoming connection from {addr}?"));

    if accept {
        socket.write_u8(ServerConnectMessage::ALLOWED as u8).await
            .map_err(|err| NetworkingError::WriteError(err))?;

        // Pass connection to app.state.packet_server
        packet_manager.lock().unwrap().connect(socket);
    } else {
        socket.write_u8(ServerConnectMessage::DENIED as u8).await
            .map_err(|err| NetworkingError::WriteError(err))?;
    }

    // If the socket hasn't been passed to the packet server, it will be
    // dropped and automatically closed here
    Ok(())
}

pub fn start_listening_server(app_window: Window<Wry>, packet_manager: Arc<Mutex<PacketManager>>) {
    tokio::spawn(async move {
        // Start listening on port
        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        println!("Server started at {:?}", listener.local_addr());
        // TODO: Send this address to front end (maybe by storing in struct -> front-end queries?)

        // Start listening loop
        loop {
            let connection_result = handle_incoming_connection(&listener, &app_window, &packet_manager).await;

            // Handle any errors that occur in the listening loop so they don't stop
            // the server
            if let Err(err) = connection_result {
                println!("An error occurred in the listening server: {err:?}");
            }
        }
    });
}

pub async fn connect_to_server(addr: String, packet_manager: &Mutex<PacketManager>) -> Result<bool, NetworkingError> {
    let mut connection = TcpStream::connect(&addr).await
        .map_err(|_| NetworkingError::ConnectionError(addr))?;

    // Check if this connection is allowed
    let allowed = connection.read_u8().await
        .map_err(|err| NetworkingError::ReadError(err))?;
    if allowed == ServerConnectMessage::ALLOWED as u8 {
        // Pass connection to packet server
        packet_manager.lock().unwrap().connect(connection);
        Ok(true)
    } else if allowed == ServerConnectMessage::DENIED as u8 {
        Ok(false)
    } else {
        Err(NetworkingError::UnexpectedMessage(allowed.to_string()))
    }

    // If the socket hasn't been passed to the packet server, it will be
    // dropped and automatically closed here
}