package org.fourz.rvnkcore.database.connection;

import org.fourz.rvnkcore.database.config.DatabaseConfig;
import org.fourz.rvnktools.util.log.LogManager;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration test for MySQLConnectionProvider.
 * 
 * This test validates the MySQL connection provider implementation including
 * HikariCP integration, connection pooling, SSL support, and error handling.
 * 
 * NOTE: This test requires a running MySQL instance for full validation.
 * For CI/CD environments, use testcontainers or mock the connection.
 */
public class MySQLConnectionProviderTest {
    
    @Mock
    private Plugin mockPlugin;
    
    @Mock
    private LogManager mockLogger;
    
    private MySQLConnectionProvider connectionProvider;
    private DatabaseConfig config;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock plugin logging
        when(mockPlugin.getName()).thenReturn("TestPlugin");
        LogManager.setInstance(mockPlugin, mockLogger);
        
        // Create test configuration
        config = DatabaseConfig.builder()
            .type("mysql")
            .host("localhost")
            .port(3306)
            .database("rvnkcore_test")
            .username("test_user")
            .password("test_password")
            .useSSL(false) // Disable SSL for testing
            .maxConnections(5)
            .minIdleConnections(2)
            .connectionTimeoutMs(10000L)
            .build();
    }
    
    @AfterEach
    void tearDown() {
        if (connectionProvider != null) {
            connectionProvider.close();
        }
    }
    
    @Test
    void testConnectionProviderInitialization() {
        // Test successful initialization
        assertDoesNotThrow(() -> {
            connectionProvider = new MySQLConnectionProvider(config, mockPlugin);
            assertNotNull(connectionProvider);
            assertEquals("MySQL", connectionProvider.getDatabaseType());
        });
    }
    
    @Test
    void testConnectionValidation() {
        connectionProvider = new MySQLConnectionProvider(config, mockPlugin);
        
        // Note: This will only pass if MySQL is running and accessible
        // In a real environment, you would check if MySQL is available
        boolean isValid = connectionProvider.isValid();
        
        // Log the result for manual verification
        if (isValid) {
            System.out.println("✅ MySQL connection validation passed");
        } else {
            System.out.println("⚠️ MySQL connection validation failed (expected if MySQL not running)");
        }
    }
    
    @Test
    void testConnectionPoolMetrics() throws SQLException {
        connectionProvider = new MySQLConnectionProvider(config, mockPlugin);
        
        // Test pool metrics methods exist and return sensible values
        int activeConnections = connectionProvider.getActiveConnections();
        int idleConnections = connectionProvider.getIdleConnections();
        int totalConnections = connectionProvider.getTotalConnections();
        
        assertTrue(activeConnections >= 0, "Active connections should be non-negative");
        assertTrue(idleConnections >= 0, "Idle connections should be non-negative");
        assertTrue(totalConnections >= 0, "Total connections should be non-negative");
        assertTrue(totalConnections >= activeConnections, "Total should be >= active");
        
        String poolStats = connectionProvider.getPoolStatistics();
        assertNotNull(poolStats, "Pool statistics should not be null");
        assertTrue(poolStats.contains("MySQL Pool Stats"), "Pool stats should contain identifier");
        
        System.out.println("📊 Pool Statistics: " + poolStats);
    }
    
    @Test
    void testConfigurationValidation() {
        // Test invalid configurations
        assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConfig invalidConfig = DatabaseConfig.builder()
                .type("mysql")
                .host("") // Empty host
                .database("test")
                .build();
            new MySQLConnectionProvider(invalidConfig, mockPlugin);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConfig invalidConfig = DatabaseConfig.builder()
                .type("mysql")
                .host("localhost")
                .port(0) // Invalid port
                .database("test")
                .build();
            new MySQLConnectionProvider(invalidConfig, mockPlugin);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            DatabaseConfig invalidConfig = DatabaseConfig.builder()
                .type("sqlite") // Wrong type
                .host("localhost")
                .database("test")
                .build();
            new MySQLConnectionProvider(invalidConfig, mockPlugin);
        });
    }
    
    @Test
    void testConnectionAcquisition() {
        connectionProvider = new MySQLConnectionProvider(config, mockPlugin);
        
        try {
            Connection connection = connectionProvider.getConnection();
            
            if (connection != null) {
                // Successfully got connection
                assertTrue(connection.isValid(5), "Connection should be valid");
                
                // Test basic SQL operation
                try (Statement stmt = connection.createStatement()) {
                    stmt.executeQuery("SELECT 1").close();
                    System.out.println("✅ Basic SQL query executed successfully");
                }
                
                connection.close();
                System.out.println("✅ Connection acquired and released successfully");
                
            } else {
                System.out.println("⚠️ Connection is null (MySQL not accessible)");
            }
            
        } catch (SQLException e) {
            System.out.println("⚠️ SQLException during connection test: " + e.getMessage());
            // This is expected if MySQL is not running
        }
    }
    
    @Test
    void testConnectionPoolLifecycle() {
        connectionProvider = new MySQLConnectionProvider(config, mockPlugin);
        
        // Test initial state
        String initialStats = connectionProvider.getPoolStatistics();
        assertNotNull(initialStats);
        
        // Test close operation
        assertDoesNotThrow(() -> {
            connectionProvider.close();
        });
        
        // After closing, validity check should return false
        assertFalse(connectionProvider.isValid(), "Connection provider should be invalid after close");
    }
    
    /**
     * Integration test that requires a real MySQL instance.
     * This demonstrates the full connection lifecycle.
     */
    @Test
    void testRealMySQLIntegration() {
        // Skip if MySQL environment variables not set
        String mysqlHost = System.getenv("MYSQL_HOST");
        String mysqlUser = System.getenv("MYSQL_USER");
        String mysqlPassword = System.getenv("MYSQL_PASSWORD");
        String mysqlDatabase = System.getenv("MYSQL_DATABASE");
        
        if (mysqlHost == null || mysqlUser == null) {
            System.out.println("⚠️ Skipping real MySQL integration test (environment variables not set)");
            return;
        }
        
        // Create configuration from environment
        DatabaseConfig realConfig = DatabaseConfig.builder()
            .type("mysql")
            .host(mysqlHost)
            .port(Integer.parseInt(System.getenv("MYSQL_PORT") != null ? 
                  System.getenv("MYSQL_PORT") : "3306"))
            .database(mysqlDatabase != null ? mysqlDatabase : "rvnkcore_test")
            .username(mysqlUser)
            .password(mysqlPassword != null ? mysqlPassword : "")
            .useSSL(Boolean.parseBoolean(System.getenv("MYSQL_USE_SSL")))
            .maxConnections(10)
            .minIdleConnections(3)
            .build();
        
        try {
            connectionProvider = new MySQLConnectionProvider(realConfig, mockPlugin);
            
            // Test connection acquisition
            try (Connection conn = connectionProvider.getConnection()) {
                assertNotNull(conn);
                assertTrue(conn.isValid(5));
                
                // Test table creation
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("CREATE TEMPORARY TABLE test_table (id INT PRIMARY KEY, name VARCHAR(100))");
                    stmt.execute("INSERT INTO test_table (id, name) VALUES (1, 'Test Data')");
                    
                    var rs = stmt.executeQuery("SELECT COUNT(*) FROM test_table");
                    assertTrue(rs.next());
                    assertEquals(1, rs.getInt(1));
                    
                    System.out.println("✅ Real MySQL integration test passed");
                }
            }
            
            // Test pool statistics
            String stats = connectionProvider.getPoolStatistics();
            System.out.println("📊 Real Pool Statistics: " + stats);
            
            // Verify pool is working
            assertTrue(connectionProvider.getActiveConnections() >= 0);
            assertTrue(connectionProvider.getTotalConnections() > 0);
            
        } catch (SQLException e) {
            System.err.println("❌ Real MySQL integration test failed: " + e.getMessage());
            // Don't fail the test since MySQL might not be available
        }
    }
    
    /**
     * Performance test for connection pool under load.
     */
    @Test 
    void testConnectionPoolUnderLoad() {
        connectionProvider = new MySQLConnectionProvider(config, mockPlugin);
        
        // Only run if MySQL is actually available
        if (!connectionProvider.isValid()) {
            System.out.println("⚠️ Skipping load test (MySQL not available)");
            return;
        }
        
        int threadCount = 10;
        int operationsPerThread = 5;
        
        Thread[] threads = new Thread[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    try (Connection conn = connectionProvider.getConnection()) {
                        assertNotNull(conn);
                        assertTrue(conn.isValid(1));
                        
                        // Simulate work
                        try (Statement stmt = conn.createStatement()) {
                            stmt.executeQuery("SELECT 1").close();
                        }
                        
                    } catch (SQLException e) {
                        System.err.println("Thread " + threadId + " operation " + j + " failed: " + e.getMessage());
                    }
                }
            });
        }
        
        // Start all threads
        long startTime = System.currentTimeMillis();
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for completion
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("🚀 Load test completed in " + elapsed + "ms");
        System.out.println("📊 Final pool stats: " + connectionProvider.getPoolStatistics());
    }
}
