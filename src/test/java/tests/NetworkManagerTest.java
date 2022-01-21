package tests;

import com.google.gson.Gson;
import net.bytebuddy.asm.Advice;
import org.acdap.osusynchro.network.NetworkManager;
import org.acdap.osusynchro.network.NetworkProtocol;
import org.acdap.osusynchro.util.Beatmap;
import org.acdap.osusynchro.util.LocalBeatmapSource;
import org.acdap.osusynchro.util.RemoteBeatmapSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NetworkManagerTest {

    LocalBeatmapSource local;
    RemoteBeatmapSource remote;
    NetworkManager network;

    @BeforeEach
    void setUp() {
        local = mock(LocalBeatmapSource.class);
        ArrayList<Beatmap> mockLocal = new ArrayList<Beatmap>(Arrays.asList(
                new Beatmap(1, "test"),
                new Beatmap(1325412, "{}!*/\\\""),
                new Beatmap(315151515, "test2")
        ));
        when(local.getBeatmaps()).thenReturn(mockLocal);

        remote = mock(RemoteBeatmapSource.class);
        network = new NetworkManager(null, local, remote);
    }

    @AfterEach
    void tearDown() {
        network.close();
    }

    @Test
    void startClientConnection() {
        // NOTE: Uses port 728 to not collide with NetworkManager's server,
        // manually change in startClientConnection when testing
        try (ServerSocket server = new ServerSocket(728)){
            String serverAddress = server.getInetAddress().getHostAddress();
            Thread clientThread = new Thread(() -> {
                try {
                    network.startClientConnection(serverAddress);
                } catch (IOException e) {
                    System.out.println("Error connecting to " + serverAddress);
                    e.printStackTrace();
                }
            });
            clientThread.start();

            Socket client = server.accept();
            System.out.println("Connected client: " + client);

            client.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void startServerLoop(){
        // Check that we can connect as a client
        try (Socket connection = new Socket("0.0.0.0", 727)){
            System.out.println(connection);
            assertTrue(connection.isConnected());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void sendMessage(){
        try (Socket connection = new Socket("0.0.0.0", 727);
             BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))){
            System.out.println(connection);
            assertTrue(connection.isConnected());

            // sendLocalBeatmaps
            network.sendLocalBeatmaps();
            assertEquals(in.readLine(), NetworkProtocol.MSGSTART);
            // {"type":"LIST","content":"[{\"id\":1,\"name\":\"test\",\"ignore\":false},{\"id\":1325412,\"name\":\"{}!*/\\\\\\\"\",\"ignore\":false},{\"id\":315151515,\"name\":\"test2\",\"ignore\":false}]"}
            System.out.println(in.readLine());
            assertEquals(in.readLine(), NetworkProtocol.MSGEND);

            // sendBeatmapIgnored
            network.sendBeatmapIgnored(local, 17, true);
            network.sendBeatmapIgnored(remote, 38, true);

            assertEquals(in.readLine(), NetworkProtocol.MSGSTART);
            // {"type":"IGNORE","content":"{\"isLocalSource\":true,\"i\":17,\"ignore\":true}"}
            System.out.println(in.readLine());
            assertEquals(in.readLine(), NetworkProtocol.MSGEND);

            assertEquals(in.readLine(), NetworkProtocol.MSGSTART);
            //{"type":"IGNORE","content":"{\"isLocalSource\":false,\"i\":38,\"ignore\":true}"}
            System.out.println(in.readLine());
            assertEquals(in.readLine(), NetworkProtocol.MSGEND);

            // sendRemoteBeatmapsRequest
            network.sendRemoteBeatmapsRequest();
            assertEquals(in.readLine(), NetworkProtocol.MSGSTART);
            // {"type":"REQUEST","content":""}
            System.out.println(in.readLine());
            assertEquals(in.readLine(), NetworkProtocol.MSGEND);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    void receiveMessage(){
        try (Socket connection = new Socket("0.0.0.0", 727);
             PrintWriter out = new PrintWriter(connection.getOutputStream())){
            System.out.println(connection);
            assertTrue(connection.isConnected());

            // LIST msg
            out.println("""
                    MSG_START
                    {"type":"LIST","content":"[{\\"id\\":1,\\"name\\":\\"test\\",\\"ignore\\":false},{\\"id\\":1325412,\\"name\\":\\"{}!*/\\\\\\\\\\\\\\"\\",\\"ignore\\":false},{\\"id\\":315151515,\\"name\\":\\"test2\\",\\"ignore\\":false}]"}
                    MSG_END""");
            out.flush();
            Thread.sleep(1000);
            verify(remote).remoteUpdateBeatmaps(any());

            // IGNORE msg
            out.println("""
                    MSG_START
                    {"type":"IGNORE","content":"{\\"isLocalSource\\":true,\\"i\\":17,\\"ignore\\":true}"}
                    MSG_END""");
            out.flush();
            Thread.sleep(1000);
            verify(remote).setBeatmapIgnore(17, true);

            out.println("""
                    MSG_START
                    {"type":"IGNORE","content":"{\\"isLocalSource\\":false,\\"i\\":38,\\"ignore\\":false}"}
                    MSG_END""");
            out.flush();
            Thread.sleep(1000);
            verify(local).setBeatmapIgnore(38, false);

            // REQUEST msg
            out.println("""
                    MSG_START
                    {"type":"REQUEST","content":""}
                    MSG_END""");
            out.flush();
            Thread.sleep(1000);
            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))){
                assertEquals(in.readLine(), NetworkProtocol.MSGSTART);
                // {"type":"LIST","content":"[{\"id\":1,\"name\":\"test\",\"ignore\":false},{\"id\":1325412,\"name\":\"{}!*/\\\\\\\"\",\"ignore\":false},{\"id\":315151515,\"name\":\"test2\",\"ignore\":false}]"}
                System.out.println(in.readLine());
                assertEquals(in.readLine(), NetworkProtocol.MSGEND);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void disconnect() {
        try (Socket connection = new Socket("0.0.0.0", 727)){
            System.out.println(connection);
            assertTrue(connection.isConnected());

            Thread.sleep(1000);

            // Disconnect
            network.disconnect();
            assertEquals(connection.getInputStream().read(), -1);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}