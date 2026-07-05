package distproject.message;

import java.io.Serializable;

public class Message implements Serializable {
    private final MessageType type;
    private final String text;
    private final Object payload;

    public Message(MessageType type, String text, Object payload) {
        this.type = type;
        this.text = text;
        this.payload = payload;
    }

    public MessageType getType() {
        return type;
    }

    public String getText() {
        return text;
    }

    public Object getPayload() {
        return payload;
    }
}
