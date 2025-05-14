package com.igot.cb.transactional.cassandrautils;

import com.datastax.oss.driver.api.core.CqlSession;
import com.igot.cb.exceptions.CustomException;
import com.igot.cb.util.PropertiesCache;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import org.mockito.MockedStatic;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.metadata.EndPoint;
import com.datastax.oss.driver.api.core.metadata.Metadata;
import com.datastax.oss.driver.api.core.metadata.Node;
import com.igot.cb.util.Constants;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.eq;
import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.DefaultConsistencyLevel;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.powermock.reflect.Whitebox;
import org.slf4j.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.Assert.fail;
import java.util.concurrent.ConcurrentHashMap;

@RunWith(MockitoJUnitRunner.class)
public class CassandraConnectionManagerImplTest {

    @Spy
    @InjectMocks
    private CassandraConnectionManagerImpl cassandraConnectionManager;

    @Mock
    private CqlSession mockSession;

    @Mock
    private CqlSession mockNewSession;

    @Mock
    private PropertiesCache propertiesCache;

    private Map<String, CqlSession> testSessionMap;



    @Before
    public void setUp() throws Exception {
        doReturn(mockNewSession).when(cassandraConnectionManager).createCassandraConnectionWithKeySpaces(anyString());
        testSessionMap = new ConcurrentHashMap<>();
        doAnswer(invocation -> {
            String keyspace = invocation.getArgument(0);
            CqlSession session = testSessionMap.get(keyspace);
            if (session != null && !session.isClosed()) {
                return session;
            } else {
                CqlSession newSession = cassandraConnectionManager.createCassandraConnectionWithKeySpaces(keyspace);
                testSessionMap.put(keyspace, newSession);
                return newSession;
            }
        }).when(cassandraConnectionManager).getSession(anyString());
    }

    @Test
    public void testGetSession_ReturnsExistingSession() {
        String keyspaceName = "testKeyspace";
        when(mockSession.isClosed()).thenReturn(false);
        testSessionMap.put(keyspaceName, mockSession);
        CqlSession result = cassandraConnectionManager.getSession(keyspaceName);
        assertSame("Should return the existing session", mockSession, result);
        verify(mockSession).isClosed();
        verify(cassandraConnectionManager, never()).createCassandraConnectionWithKeySpaces(keyspaceName);
    }

    @Test
    public void testGetSession_SessionIsClosed() {
        String keyspaceName = "testKeyspace";
        when(mockSession.isClosed()).thenReturn(true);
        testSessionMap.put(keyspaceName, mockSession);
        CqlSession result = cassandraConnectionManager.getSession(keyspaceName);
        assertSame("Should return a new session", mockNewSession, result);
        verify(mockSession).isClosed();
        verify(cassandraConnectionManager).createCassandraConnectionWithKeySpaces(keyspaceName);
        assertEquals("Should update the session map", mockNewSession, testSessionMap.get(keyspaceName));
    }

    @Test
    public void testGetSession_NoExistingSession() {
        String keyspaceName = "newKeyspace";
        CqlSession result = cassandraConnectionManager.getSession(keyspaceName);
        assertSame("Should return a new session", mockNewSession, result);
        verify(cassandraConnectionManager).createCassandraConnectionWithKeySpaces(keyspaceName);
        assertEquals("Should add the new session to the map", mockNewSession, testSessionMap.get(keyspaceName));
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_WithKeyspace() {
        reset(cassandraConnectionManager);
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
        when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("2");
        when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
        when(mockCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
        CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class);
        MockedStatic<CqlSession> mockedCqlSession = mockStatic(CqlSession.class);
        mockedCqlSession.when(CqlSession::builder).thenReturn(mockBuilder);
        when(mockBuilder.addContactPoints(any())).thenReturn(mockBuilder);
        when(mockBuilder.withLocalDatacenter(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withKeyspace(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withConfigLoader(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockSession);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getClusterName()).thenReturn(Optional.of("Test Cluster"));
        Map<UUID, Node> nodeMap = new HashMap<>();
        Node mockNode = mock(Node.class);
        when(mockNode.getDatacenter()).thenReturn("datacenter1");
        EndPoint mockEndPoint = mock(EndPoint.class);
        when(mockEndPoint.toString()).thenReturn("localhost:9042");
        when(mockNode.getEndPoint()).thenReturn(mockEndPoint);
        when(mockNode.getRack()).thenReturn("rack1");
        UUID nodeId = UUID.randomUUID();
        nodeMap.put(nodeId, mockNode);
        when(mockMetadata.getNodes()).thenReturn(nodeMap);
        CqlSession result = cassandraConnectionManager.createCassandraConnectionWithKeySpaces("test_keyspace");
        assertNotNull(result);
        verify(mockBuilder).withKeyspace("test_keyspace");
        mockedPropertiesCache.close();
        mockedCqlSession.close();
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_WithoutKeyspace() {
        reset(cassandraConnectionManager);
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("localhost");
        when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("2");
        when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
        when(mockCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
        CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class);
        MockedStatic<CqlSession> mockedCqlSession = mockStatic(CqlSession.class);
        mockedCqlSession.when(CqlSession::builder).thenReturn(mockBuilder);
        when(mockBuilder.addContactPoints(any())).thenReturn(mockBuilder);
        when(mockBuilder.withLocalDatacenter(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withConfigLoader(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockSession);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getClusterName()).thenReturn(Optional.of("Test Cluster"));
        Map<UUID, Node> nodeMap = new HashMap<>();
        Node mockNode = mock(Node.class);
        when(mockNode.getDatacenter()).thenReturn("datacenter1");
        EndPoint mockEndPoint = mock(EndPoint.class);
        when(mockEndPoint.toString()).thenReturn("localhost:9042");
        when(mockNode.getEndPoint()).thenReturn(mockEndPoint);
        when(mockNode.getRack()).thenReturn("rack1");
        UUID nodeId = UUID.randomUUID();
        nodeMap.put(nodeId, mockNode);
        when(mockMetadata.getNodes()).thenReturn(nodeMap);
        CqlSession result = cassandraConnectionManager.createCassandraConnectionWithKeySpaces(null);
        assertNotNull(result);
        verify(mockBuilder, never()).withKeyspace(anyString());
        mockedPropertiesCache.close();
        mockedCqlSession.close();
    }

    @Test
    public void testCreateCassandraConnectionWithKeySpaces_MultipleHosts() {
        reset(cassandraConnectionManager);
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("host1,host2,host3");
        when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_LOCAL)).thenReturn("2");
        when(mockCache.getProperty(Constants.CORE_CONNECTIONS_PER_HOST_FOR_REMOTE)).thenReturn("1");
        when(mockCache.getProperty(Constants.HEARTBEAT_INTERVAL)).thenReturn("30");
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL)).thenReturn("LOCAL_QUORUM");
        CqlSessionBuilder mockBuilder = mock(CqlSessionBuilder.class);
        MockedStatic<CqlSession> mockedCqlSession = mockStatic(CqlSession.class);
        mockedCqlSession.when(CqlSession::builder).thenReturn(mockBuilder);
        when(mockBuilder.addContactPoints(any())).thenReturn(mockBuilder);
        when(mockBuilder.withLocalDatacenter(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withKeyspace(anyString())).thenReturn(mockBuilder);
        when(mockBuilder.withConfigLoader(any())).thenReturn(mockBuilder);
        when(mockBuilder.build()).thenReturn(mockSession);
        Metadata mockMetadata = mock(Metadata.class);
        when(mockSession.getMetadata()).thenReturn(mockMetadata);
        when(mockMetadata.getClusterName()).thenReturn(Optional.of("Test Cluster"));
        Map<UUID, Node> nodeMap = new HashMap<>();
        Node mockNode = mock(Node.class);
        when(mockNode.getDatacenter()).thenReturn("datacenter1");
        EndPoint mockEndPoint = mock(EndPoint.class);
        when(mockEndPoint.toString()).thenReturn("host1:9042");
        when(mockNode.getEndPoint()).thenReturn(mockEndPoint);
        when(mockNode.getRack()).thenReturn("rack1");
        UUID nodeId = UUID.randomUUID();
        nodeMap.put(nodeId, mockNode);
        when(mockMetadata.getNodes()).thenReturn(nodeMap);
        ArgumentCaptor<Collection<InetSocketAddress>> contactPointsCaptor =
                ArgumentCaptor.forClass(Collection.class);
        CqlSession result = cassandraConnectionManager.createCassandraConnectionWithKeySpaces("test_keyspace");
        assertNotNull(result);
        verify(mockBuilder).addContactPoints(contactPointsCaptor.capture());
        Collection<InetSocketAddress> capturedContactPoints = contactPointsCaptor.getValue();
        assertEquals(3, capturedContactPoints.size());
        mockedPropertiesCache.close();
        mockedCqlSession.close();
    }

    @Test(expected = CustomException.class)
    public void testCreateCassandraConnectionWithKeySpaces_MissingHostConfig() {
        reset(cassandraConnectionManager);
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.getProperty(Constants.CASSANDRA_CONFIG_HOST)).thenReturn("");
        try {
            cassandraConnectionManager.createCassandraConnectionWithKeySpaces("test_keyspace");
        } finally {
            mockedPropertiesCache.close();
        }
    }

    @Test
    public void testCreateCassandraConnection_Success() {
        reset(cassandraConnectionManager);
        doReturn(mockSession).when(cassandraConnectionManager).createCassandraConnectionWithKeySpaces(null);
        cassandraConnectionManager.createCassandraConnection();
        verify(cassandraConnectionManager).createCassandraConnectionWithKeySpaces(null);
        CqlSession sessionField = Whitebox.getInternalState(CassandraConnectionManagerImpl.class, "session");
        assertSame(mockSession, sessionField);
    }

    @Test(expected = CustomException.class)
    public void testCreateCassandraConnection_ThrowsException() {
        reset(cassandraConnectionManager);
        doThrow(new RuntimeException("Connection error")).when(cassandraConnectionManager)
                .createCassandraConnectionWithKeySpaces(null);
        cassandraConnectionManager.createCassandraConnection();
    }

    @Test
    public void testCreateCassandraConnection_LogsError() {
        reset(cassandraConnectionManager);
        Logger mockLogger = mock(Logger.class);
        Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "logger", mockLogger);
        RuntimeException testException = new RuntimeException("Connection error");
        doThrow(testException).when(cassandraConnectionManager)
                .createCassandraConnectionWithKeySpaces(null);
        try {
            cassandraConnectionManager.createCassandraConnection();
        } catch (CustomException ex) {
            verify(mockLogger).error(eq("Error while creating Cassandra connection"), eq(testException));
        }
        Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "logger", LoggerFactory.getLogger(CassandraConnectionManagerImpl.class));
    }

    @Test
    public void testGetConsistencyLevel_ValidLevel() {
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                .thenReturn("LOCAL_QUORUM");
        ConsistencyLevel result = CassandraConnectionManagerImpl.getConsistencyLevel();
        assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, result);
        mockedPropertiesCache.close();
    }

    @Test
    public void testGetConsistencyLevel_LowercaseLevel() {
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                .thenReturn("local_quorum");
        ConsistencyLevel result = CassandraConnectionManagerImpl.getConsistencyLevel();
        assertEquals(DefaultConsistencyLevel.LOCAL_QUORUM, result);
        mockedPropertiesCache.close();
    }

    @Test
    public void testGetConsistencyLevel_InvalidLevel() {
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                .thenReturn("INVALID_LEVEL");
        ConsistencyLevel result = CassandraConnectionManagerImpl.getConsistencyLevel();
        assertNull(result);
        mockedPropertiesCache.close();
    }

    @Test
    public void testGetConsistencyLevel_EmptyLevel() {
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                .thenReturn("");
        ConsistencyLevel result = CassandraConnectionManagerImpl.getConsistencyLevel();
        assertNull(result);
        mockedPropertiesCache.close();
    }

    @Test
    public void testGetConsistencyLevel_NullLevel() {
        PropertiesCache mockCache = mock(PropertiesCache.class);
        MockedStatic<PropertiesCache> mockedPropertiesCache = mockStatic(PropertiesCache.class);
        mockedPropertiesCache.when(PropertiesCache::getInstance).thenReturn(mockCache);
        when(mockCache.readProperty(Constants.SUNBIRD_CASSANDRA_CONSISTENCY_LEVEL))
                .thenReturn(null);
        ConsistencyLevel result = CassandraConnectionManagerImpl.getConsistencyLevel();
        assertNull(result);
        mockedPropertiesCache.close();
    }

    @Test
    public void testRegisterShutdownHook() {
        Logger mockLogger = mock(Logger.class);
        Runtime mockRuntime = mock(Runtime.class);
        Logger originalLogger = Whitebox.getInternalState(CassandraConnectionManagerImpl.class, "logger");
        try {
            Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "logger", mockLogger);
            MockedStatic<Runtime> mockedRuntime = mockStatic(Runtime.class);
            mockedRuntime.when(Runtime::getRuntime).thenReturn(mockRuntime);
            CassandraConnectionManagerImpl.registerShutdownHook();
            verify(mockRuntime).addShutdownHook(any(Thread.class));
            verify(mockLogger).info("Cassandra ShutDownHook registered.");
            mockedRuntime.close();
        } finally {
            Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "logger", originalLogger);
        }
    }

    @Test
    public void testResourceCleanUp_Run() {
        Logger mockLogger = mock(Logger.class);
        CqlSession mockSession1 = mock(CqlSession.class);
        CqlSession mockSession2 = mock(CqlSession.class);
        CqlSession mockStaticSession = mock(CqlSession.class);
        Logger originalLogger = Whitebox.getInternalState(CassandraConnectionManagerImpl.class, "logger");
        Map<String, CqlSession> originalMap = Whitebox.getInternalState(CassandraConnectionManagerImpl.class, "cassandraSessionMap");
        CqlSession originalSession = Whitebox.getInternalState(CassandraConnectionManagerImpl.class, "session");
        Map<String, CqlSession> testMap = new ConcurrentHashMap<>();
        testMap.put("keyspace1", mockSession1);
        testMap.put("keyspace2", mockSession2);
        try {
            Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "logger", mockLogger);
            Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "cassandraSessionMap", testMap);
            Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "session", mockStaticSession);
            Class<?> resourceCleanUpClass = Class.forName("com.igot.cb.transactional.cassandrautils.CassandraConnectionManagerImpl$ResourceCleanUp");
            Thread cleanUpThread = (Thread) resourceCleanUpClass.getDeclaredConstructor().newInstance();
            cleanUpThread.run();
            verify(mockLogger).info("Started resource cleanup for Cassandra.");
            verify(mockSession1).close();
            verify(mockSession2).close();
            verify(mockStaticSession).close();
            verify(mockLogger).info("Completed resource cleanup for Cassandra.");
        } catch (Exception e) {
            fail("Test failed: " + e.getMessage());
        } finally {
            Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "logger", originalLogger);
            Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "cassandraSessionMap", originalMap);
            Whitebox.setInternalState(CassandraConnectionManagerImpl.class, "session", originalSession);
        }
    }
}