package tests;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.acdap.osusynchro.network.NetworkProtocol;
import org.acdap.osusynchro.network.NetworkProtocol.Message;
import org.acdap.osusynchro.network.NetworkProtocol.MessageIgnore;
import org.acdap.osusynchro.network.NetworkProtocol.MessageType;
import org.acdap.osusynchro.util.Beatmap;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;


class NetworkProtocolTest {

    @Test
    void encodedMessage() {
        Gson gson = new Gson();

        // LIST msg
        ArrayList<Beatmap> list = new ArrayList<>(Arrays.asList(
                new Beatmap(123, "abc"),
                new Beatmap(446, "aser{f3;"),
                new Beatmap(29837534, "ef!oseijfs..SE")
        ));
        Message msg = new Message(MessageType.LIST, gson.toJson(list));
        String str = NetworkProtocol.encodeMessage(msg);
        System.out.println(str);

        // IGNORE msg
        MessageIgnore ignore = new MessageIgnore(true, 17, false);
        msg = new Message(MessageType.IGNORE, gson.toJson(ignore));
        str = NetworkProtocol.encodeMessage(msg);
        System.out.println(str);

        // REQUEST msg
        msg = new Message(MessageType.REQUEST, "");
        str = NetworkProtocol.encodeMessage(msg);
        System.out.println(str);
    }

    @Test
    void decodeString() {
        Gson gson = new Gson();

        // LIST msg
        String str = """
                MSG_START
                {"type":"LIST","content":"[{\\"id\\":123,\\"name\\":\\"abc\\",\\"ignore\\":false},{\\"id\\":446,\\"name\\":\\"aser{f3;\\",\\"ignore\\":false},{\\"id\\":29837534,\\"name\\":\\"ef!oseijfs..SE\\",\\"ignore\\":false}]"}
                MSG_END""";
        Message msg = NetworkProtocol.decodeString(str);
        assertEquals(msg.type(), MessageType.LIST);

        Type listType = new TypeToken<ArrayList<Beatmap>>() {}.getType();
        ArrayList<Beatmap> beatmaps = gson.fromJson(msg.content(), listType);
        ArrayList<Beatmap> list = new ArrayList<>(Arrays.asList(
                new Beatmap(123, "abc"),
                new Beatmap(446, "aser{f3;"),
                new Beatmap(29837534, "ef!oseijfs..SE")
        ));
        TestUtils.assertArrayListEquals(list, beatmaps);

        // IGNORE msg
        str = """
                MSG_START
                {"type":"IGNORE","content":"{\\"isLocalSource\\":true,\\"i\\":17,\\"ignore\\":false}"}
                MSG_END""";
        msg = NetworkProtocol.decodeString(str);
        assertEquals(msg.type(), MessageType.IGNORE);

        MessageIgnore ignore = new MessageIgnore(true, 17, false);
        assertEquals(gson.fromJson(msg.content(), MessageIgnore.class), ignore);

        // REQUEST msg
        str = """
                MSG_START
                {"type":"REQUEST","content":""}
                MSG_END""";
        msg = NetworkProtocol.decodeString(str);
        assertEquals(msg.type(), MessageType.REQUEST);
    }
}