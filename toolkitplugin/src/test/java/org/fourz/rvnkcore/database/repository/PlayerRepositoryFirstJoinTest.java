package org.fourz.rvnkcore.database.repository;

import org.bukkit.plugin.Plugin;
import org.fourz.rvnkcore.database.connection.ConnectionProvider;
import org.fourz.rvnkcore.database.query.QueryBuilder;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * #2281: correctFirstJoinIfEarlier swallowed SQLException and returned 0, the same value as
 * "already correct", so /rvnkcore firstjoin backfill reported 0 failed after a database outage.
 */
class PlayerRepositoryFirstJoinTest {

    private static PlayerRepository repository(ConnectionProvider provider) {
        Plugin plugin = mock(Plugin.class);
        when(plugin.getName()).thenReturn("RVNKCoreFirstJoinTest");
        return new PlayerRepository(provider, mock(QueryBuilder.class), plugin);
    }

    @Test
    void databaseErrorCompletesExceptionally() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        when(provider.getConnection()).thenThrow(new SQLException("connection lost"));

        ExecutionException ex = assertThrows(ExecutionException.class, () -> repository(provider)
                .correctFirstJoinIfEarlier(UUID.randomUUID(), new Timestamp(1_000L))
                .get(5, TimeUnit.SECONDS));
        assertInstanceOf(SQLException.class, ex.getCause());
    }

    @Test
    void alreadyCorrectStillReturnsZero() throws Exception {
        ConnectionProvider provider = mock(ConnectionProvider.class);
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(provider.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeUpdate()).thenReturn(0);

        int changed = repository(provider)
                .correctFirstJoinIfEarlier(UUID.randomUUID(), new Timestamp(1_000L))
                .get(5, TimeUnit.SECONDS);
        assertEquals(0, changed);
    }
}
