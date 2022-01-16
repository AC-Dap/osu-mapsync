package org.acdap.osusynchro.network;

import com.google.gson.Gson;

import java.util.Objects;

public class NetworkProtocol {

    public enum MessageType{
        LIST,
        IGNORE,
        REQUEST
    }

    public static final String MSGSTART = "MSG_START";
    public static final String MSGEND = "MSG_END";
    private static final Gson gson = new Gson();

    public static String encodeMessage(Message msg){
        return String.format("%s\n%s\n%s", MSGSTART, gson.toJson(msg), MSGEND);
    }

    public static Message decodeString(String str){
        String msgStr = str.substring(MSGSTART.length(), str.length() - MSGEND.length());
        return gson.fromJson(msgStr, Message.class);
    }

    public static class Message {
        private final MessageType type;
        private final String content;

        public Message(MessageType type, String content) {
            this.type = type;
            this.content = content;
        }

        public MessageType type(){
            return type;
        }

        public String content(){
            return content;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Message message = (Message) o;
            return type == message.type && Objects.equals(content, message.content);
        }
    }

    public static class MessageIgnore{

        private final boolean isLocalSource;
        private final int i;
        private final boolean ignore;

        public MessageIgnore (boolean isLocalSource, int i, boolean ignore){
            this.isLocalSource = isLocalSource;
            this.i = i;
            this.ignore = ignore;
        }

        public boolean isLocalSource() {
            return isLocalSource;
        }

        public int i() {
            return i;
        }

        public boolean ignore() {
            return ignore;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MessageIgnore that = (MessageIgnore) o;
            return isLocalSource == that.isLocalSource && i == that.i && ignore == that.ignore;
        }
    }
}
