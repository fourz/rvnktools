package org.fourz.rvnkcore.service.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.Predicate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The packet reader against stand-ins shaped like the vanilla records: same simple names, a text
 * field whose value has {@code getString()}. The real component type check is by class name, so the
 * tests inject a predicate for the stand-in type.
 */
@DisplayName("QA packet text: kind by packet name, text by getString() (#2359)")
class QaPacketTextTest {

    /** Stands in for net.minecraft.network.chat.Component. */
    public static final class FakeComponent {
        private final String text;

        public FakeComponent(String text) {
            this.text = text;
        }

        public String getString() {
            return text;
        }
    }

    public record ClientboundSystemChatPacket(FakeComponent content, boolean overlay) {
    }

    public record ClientboundSetTitleTextPacket(FakeComponent text) {
    }

    public record ClientboundPlayerChatPacket(FakeComponent unsignedContent) {
    }

    /** A non-record packet, read through its fields. */
    public static final class ClientboundSetSubtitleTextPacket {
        private static final FakeComponent IGNORED = new FakeComponent("static");
        private final FakeComponent text;

        ClientboundSetSubtitleTextPacket(FakeComponent text) {
            this.text = text;
        }
    }

    private static final Predicate<Class<?>> FAKE = t -> t == FakeComponent.class;

    @Test
    @DisplayName("system chat reads as chat, or actionbar when overlay is true")
    void systemChatKinds() {
        assertEquals("chat", QaPacketText.kindOf(new ClientboundSystemChatPacket(new FakeComponent("hi"), false)));
        assertEquals("actionbar", QaPacketText.kindOf(new ClientboundSystemChatPacket(new FakeComponent("hi"), true)));
        assertEquals("title", QaPacketText.kindOf(new ClientboundSetTitleTextPacket(new FakeComponent("T"))));
    }

    @Test
    @DisplayName("player chat and unknown packets are not recorded")
    void notRecorded() {
        assertNull(QaPacketText.kindOf(new ClientboundPlayerChatPacket(new FakeComponent("x"))));
        assertNull(QaPacketText.kindOf("a string"));
        assertNull(QaPacketText.kindOf(null));
    }

    @Test
    @DisplayName("text comes from the component field, for records and plain classes")
    void text() {
        assertEquals("Warden Tolla: veterans only.", QaPacketText.textOf(
                new ClientboundSystemChatPacket(new FakeComponent("Warden Tolla: veterans only."), false), FAKE));
        assertEquals("Chapter One", QaPacketText.textOf(new ClientboundSetTitleTextPacket(new FakeComponent("Chapter One")), FAKE));
        assertEquals("the old road", QaPacketText.textOf(
                new ClientboundSetSubtitleTextPacket(new FakeComponent("the old road")), FAKE));
    }

    @Test
    @DisplayName("a null component, or no component field, gives null instead of throwing")
    void missing() {
        assertNull(QaPacketText.textOf(new ClientboundSetTitleTextPacket(null), FAKE));
        assertNull(QaPacketText.textOf(new ClientboundSetTitleTextPacket(new FakeComponent("x")), t -> false));
        assertNull(QaPacketText.textOf(null, FAKE));
    }

    @Test
    @DisplayName("the real component check matches by the vanilla class name only")
    void realComponentCheck() {
        assertFalse(QaPacketText.isComponentType(FakeComponent.class));
        assertFalse(QaPacketText.isComponentType(String.class));
        assertFalse(QaPacketText.isComponentType(null));
        assertTrue(QaPacketText.KINDS.containsKey("ClientboundSystemChatPacket"));
    }
}
