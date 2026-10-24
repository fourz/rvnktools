package org.fourz.rvnkcore.service.chatrelay;

import org.fourz.rvnkcore.api.model.ChatMessageDTO;
import org.fourz.rvnkcore.api.model.MatrixChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Chatbot replies into the MATRIX room through the tier (#2383). */
@DisplayName("Matrix bot post")
class MatrixBotPostTest {

    private static final Optional<MatrixChannel> GENERAL = Optional.of(MatrixChannel.DEFAULT_GENERAL);
    private static final Optional<MatrixChannel> NEWS =
            Optional.of(new MatrixChannel("news", "Announcements", true, "all"));

    @Test
    @DisplayName("A listed, writable channel with text is accepted")
    void accepted() {
        assertNull(MatrixBotPost.refusal("hello", "general", GENERAL));
    }

    @Test
    @DisplayName("Empty text, an unlisted or malformed channel, and a read-only channel are refused")
    void refusals() {
        assertEquals("message is empty", MatrixBotPost.refusal(" §a ", "general", GENERAL));
        assertEquals("message is empty", MatrixBotPost.refusal(null, "general", GENERAL));
        assertEquals("unknown mchannel", MatrixBotPost.refusal("hi", "nosuch", Optional.empty()));
        assertEquals("unknown mchannel", MatrixBotPost.refusal("hi", null, Optional.empty()));
        assertEquals("mchannel is read-only", MatrixBotPost.refusal("hi", "news", NEWS));
    }

    @Test
    @DisplayName("No mchannel resolves to general")
    void missingChannelIsGeneral() {
        assertEquals(MatrixChannel.GENERAL, MatrixChannel.resolveName(null));
        assertEquals(MatrixChannel.GENERAL, MatrixChannel.resolveName(" "));
    }

    @Test
    @DisplayName("The tier builds the line: MATRIX room, bot kind, no UUID, persona as sender and label")
    void build() {
        ChatMessageDTO dto = MatrixBotPost.build("m1", "dev", "dev", "Lenore", "hi there", "ravengrub", 5L);
        assertEquals("MATRIX", dto.getRoom());
        assertEquals("bot", dto.getChannel());
        assertNull(dto.getSenderUuid());
        assertEquals("Lenore", dto.getSenderName());
        assertEquals("Lenore", dto.getLabel());
        assertEquals("ravengrub", dto.getMchannel());
        assertEquals("dev", dto.getOriginServerId());
        assertEquals("dev", dto.getServerLabel());
        assertNull(dto.getComponents());
    }

    @Test
    @DisplayName("Persona and text are cleaned and capped like a Matrix inbound line")
    void cleaned() {
        String longText = "x".repeat(MatrixInbound.MAX_MESSAGE + 50);
        ChatMessageDTO dto = MatrixBotPost.build("m2", "dev", "dev", "§cGlint\n" + "y".repeat(80),
                "§khi\nthere " + longText, "general", 5L);
        assertFalse(dto.getSenderName().contains("§"));
        assertTrue(dto.getSenderName().length() <= MatrixBotPost.MAX_LABEL);
        assertFalse(dto.getMessage().contains("§") || dto.getMessage().contains("\n"));
        assertTrue(dto.getMessage().length() <= MatrixInbound.MAX_MESSAGE);
        assertEquals("Bot", MatrixBotPost.build("m3", "dev", "dev", "  ", "hi", "general", 5L).getSenderName());
        assertEquals("Shadowmelt", MatrixBotPost.build("m4", "dev", "dev", "Shadowmelt", "hi", "general", 5L).getLabel());
        assertEquals("Ravengrub Archiv", MatrixBotPost.build("m5", "dev", "dev", "Ravengrub Archivist", "hi", "general", 5L).getLabel());
    }
}
