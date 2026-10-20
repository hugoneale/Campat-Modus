package io.github.hugoneale.campat.runtime.legacy.forge;

/** Forge 47's {@code IndexedMessageCodec.MessageHandler}, returned by {@code registerMessage}; mods only hold on to it. */
public final class LegacyMessageHandler<M> {
    private final Class<M> type;
    private final int index;

    LegacyMessageHandler(Class<M> type, int index) {
        this.type = type;
        this.index = index;
    }

    public Class<M> getMessageType() {
        return type;
    }

    public int getIndex() {
        return index;
    }
}
