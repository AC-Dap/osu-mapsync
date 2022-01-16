package org.acdap.osusynchro.network;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.acdap.osusynchro.network.NetworkProtocol.Message;
import org.acdap.osusynchro.network.NetworkProtocol.MessageType;
import org.acdap.osusynchro.util.Beatmap;
import org.acdap.osusynchro.util.BeatmapSource;
import org.acdap.osusynchro.util.LocalBeatmapSource;
import org.acdap.osusynchro.util.RemoteBeatmapSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;


/**
 * Abstracts away the network connection to a remote source.
 * Handles the possible operations:
 * <ul>
 *     <li>SEND REQUEST
 *     <ol>
 *         <li>Send when local list of beatmaps is refreshed to remote source</li>
 *         <li>Send when local beatmap is included/excluded to remote source.</li>
 *         <li>Get list of beatmaps from remote source.</li>
 *         <li>Send when remote beatmap is included/excluded to remote source.</li>
 *     </ol>
 *     <li>RESPOND REQUEST
 *     <ol>
 *         <li>Respond to request for local beatmaps from remote source.</li>
 *         <li>Respond to when local beatmap is included/excluded by remote source.</li>
 *         <li>Respond to when remote beatmap list is updated by remote source</li>
 *         <li>Respond to when remote beatmap is included/excluded by remote source.</li>
 *     </ol>
 * </ul>
 */
public class NetworkManager {

    // Whether we're currently connected to a remote source or not
    private enum ConnectionState{
        DISCONNECTED,
        CONNECTED
    }
    private ConnectionState state = ConnectionState.DISCONNECTED;
    // state, connection, listeningThread should all be locked by the same lock
    private final Object stateLock = new Object();

    // Used for both client and server connection
    private Socket connection;
    private final Object socketWriteLock = new Object();
    private final Object socketReadLock = new Object();

    // Used to listen for incoming requests
    private Thread listeningThread;

    // Used to listen for incoming connections
    private ServerSocket serverSocket;

    // Our beatmap sources
    private final LocalBeatmapSource localSource;
    private final RemoteBeatmapSource remoteSource;

    // Used for serialization/deserialization of requests
    private final Gson gson = new Gson();

    public NetworkManager(LocalBeatmapSource local, RemoteBeatmapSource remote){
        this.localSource = local;
        this.remoteSource = remote;

        // Local source is refreshed
        this.localSource.addBeatmapRefreshedListener(() -> sendLocalBeatmaps());

        // Local beatmap included/excluded
        this.localSource.addBeatmapIgnoreListener((i, ignore) -> sendBeatmapIgnored(localSource, i, ignore));

        // Remote beatmap included/excluded
        this.remoteSource.addBeatmapIgnoreListener((i, ignore) -> sendBeatmapIgnored(remoteSource, i, ignore));

        try{
            serverSocket = new ServerSocket(727);

            Thread serverThread = new Thread(() -> serverLoop());
            serverThread.start();
        } catch (IOException e) {
            System.out.println("Error starting server");
            e.printStackTrace();
        }
    }

    public void sendLocalBeatmaps(){
        String content;
        synchronized (localSource.getBeatmaps()){
            content = gson.toJson(localSource.getBeatmaps());
        }
        Message msg = new Message(MessageType.LIST, content);
        sendMessage(msg);
    }

    public void sendBeatmapIgnored(BeatmapSource source, int i, boolean ignore){
        String content = gson.toJson(new NetworkProtocol.MessageIgnore(source == localSource, i, ignore));
        Message msg = new Message(MessageType.IGNORE, content);
        sendMessage(msg);
    }

    public void sendRemoteBeatmapsRequest(){
        Message msg = new Message(MessageType.REQUEST, "");
        sendMessage(msg);
    }

    private void sendMessage(Message msg){
        synchronized (stateLock) {
            if (state == ConnectionState.DISCONNECTED) return;
            assert (connection != null);
            assert (connection.isConnected());
        }

        synchronized (socketWriteLock){
            try{
                // Don't close out after writing, as that also closes the socket's output stream
                PrintWriter out = new PrintWriter(connection.getOutputStream());
                out.println(NetworkProtocol.encodeMessage(msg));
                out.flush();

                System.out.println("Sent message: " + NetworkProtocol.encodeMessage(msg));
            } catch (SocketException ignored){} // Socket closed while sending message
            catch (IOException e) {
                System.out.println("Error sending message");
                e.printStackTrace();
            }
        }
    }

    private void parseMessage(String msg){
        System.out.println("Parsing message: " + msg);
        Message decoded = NetworkProtocol.decodeString(msg);
        switch (decoded.type()) {
            case LIST -> {
                Type listType = new TypeToken<ArrayList<Beatmap>>() {}.getType();
                ArrayList<Beatmap> beatmaps = gson.fromJson(decoded.content(), listType);
                remoteSource.remoteUpdateBeatmaps(beatmaps);
            }
            case IGNORE -> {
                NetworkProtocol.MessageIgnore ignore = gson.fromJson(decoded.content(), NetworkProtocol.MessageIgnore.class);
                // Invert as it's in relation to the sender
                (!ignore.isLocalSource() ? localSource : remoteSource).setBeatmapIgnore(ignore.i(), ignore.ignore());
            }
            case REQUEST -> sendLocalBeatmaps();
        }
    }

    private void listeningLoop(){
        System.out.println("Remote connection: " + connection.toString());
        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))){
            String line;
            StringBuilder msg = new StringBuilder();
            while(true){
                synchronized (socketReadLock) {
                    line = in.readLine();
                }
                // Remote connection closed
                if(line == null) {
                    synchronized (stateLock) {
                        connection.close();

                        state = ConnectionState.DISCONNECTED;
                    }
                    break;
                }

                // Read all lines between MSGSTART and MSGEND tokens
                if((msg.isEmpty() && line.equals(NetworkProtocol.MSGSTART)) || !msg.isEmpty()){
                    msg.append(line);

                    if(line.equals(NetworkProtocol.MSGEND)){
                        parseMessage(msg.toString());
                        msg.setLength(0);
                    }
                }
            }
        }catch(SocketException ignored){ // Local connection closed
        }catch (IOException e) {
            System.out.println("Error reading incoming message");
            e.printStackTrace();
        }
    }

    public void disconnect(){
        synchronized (stateLock){
            if(state == ConnectionState.DISCONNECTED) return;
            assert (connection != null);
            assert (!connection.isClosed());
            assert (listeningThread != null);
            assert (listeningThread.isAlive());

            try {
                connection.close();
                listeningThread.interrupt();
                listeningThread.join();
                state = ConnectionState.DISCONNECTED;
            } catch (IOException | InterruptedException e) {
                System.out.println("Error disconnecting connection");
                e.printStackTrace();
            }
        }
    }

    public void close(){
        disconnect();
        try {
            serverSocket.close();
        } catch (IOException e) {
            System.out.println("Error closing server");
            e.printStackTrace();
        }
    }

    public void startClientConnection(String remoteAddress) throws IOException {
        // Disconnect from whatever connection we currently have
        disconnect();

        synchronized (stateLock){
            // Start connection with the given remote address
            connection = new Socket();
            connection.connect(new InetSocketAddress(remoteAddress, 727), 5*1000);

            listeningThread = new Thread(() -> listeningLoop());
            listeningThread.start();

            state = ConnectionState.CONNECTED;
        }
    }

    private void serverLoop(){
        // Listen for connections on port 727
        System.out.println("Server opened on " + serverSocket.getInetAddress().getHostAddress());
        while(!serverSocket.isClosed()){
            try {
                Socket clientSocket = serverSocket.accept();
                // TODO: Have user verify connection first before accepting

                // Disconnect whatever connection we currently have
                disconnect();

                synchronized (stateLock){
                    connection = clientSocket;

                    listeningThread = new Thread(() -> listeningLoop());
                    listeningThread.start();

                    state = ConnectionState.CONNECTED;
                }
            } catch(SocketException ignored){ // serverSocket closed
            } catch(IOException e) {
                System.out.println("Error accepting connection");
                e.printStackTrace();
            }
        }
    }
}
