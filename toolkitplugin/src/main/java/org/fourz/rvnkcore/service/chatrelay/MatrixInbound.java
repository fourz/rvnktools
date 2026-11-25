package org.fourz.rvnkcore.service.chatrelay;

import org.fourz.rvnkcore.api.model.ChatMessageDTO;

/**
 * Rules for a chat line from the Matrix bridge (#2376, #2377). Pure, so it is unit-tested.
 *
 * <p>A Matrix line is plain text from outside the network. It may only enter the opt-in MATRIX room;
 * styled {@code components} (which can carry click events) are dropped; section-sign colour codes
 * and control characters are removed; and the sender and message are capped. Ampersand codes are
 * left as text here: RVNKEvents renders MATRIX-room lines without colour translation.</p>
 */
public final class MatrixInbound {

    public static final String ROOM = "MATRIX";
    public static final int MAX_SENDER = 48;
    public static final int MAX_MESSAGE = 256;

    private MatrixInbound() { }

    /** Why a Matrix line was refused, or null when it is accepted. */
    public static String refusal(ChatMessageDTO dto) {
        if (dto == null) return "empty body";
        if (dto.getRoom() == null || !ROOM.equalsIgnoreCase(dto.getRoom().trim())) {
            return "a Matrix line may only use room MATRIX";
        }
        String message = clean(dto.getMessage(), MAX_MESSAGE);
        if (message.isEmpty()) return "message is empty";
        return null;
    }

    /** Applies the rules in place: room, channel, no components, cleaned and capped text. */
    public static void sanitize(ChatMessageDTO dto) {
        dto.setRoom(ROOM);
        dto.setComponents(null);
        dto.setLabel(null);
        dto.setWorld(null);
        dto.setChannel("chat");
        String sender = clean(dto.getSenderName(), MAX_SENDER);
        dto.setSenderName(sender.isEmpty() ? "Matrix" : sender);
        dto.setMessage(clean(dto.getMessage(), MAX_MESSAGE));
    }

    /**
     * Removes section signs (with the code that follows), control, format and bidi characters,
     * collapses whitespace and caps the length.
     */
    static String clean(String raw, int max) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(Math.min(raw.length(), max + 8));
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§') {
                i++; // drop the colour code letter too
                continue;
            }
            int type = Character.getType(c);
            if (Character.isISOControl(c) || type == Character.FORMAT
                    || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
                out.append(' ');
                continue;
            }
            out.append(c);
        }
        String s = out.toString().replaceAll("\\s+", " ").trim();
        if (s.length() > max) s = s.substring(0, max).trim();
        return s;
    }
}
