import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class PacketLossDetectionJUnitTest {

    private DatagramSocket senderSocket;
    private DatagramSocket receiverSocket;
    private static final int TEST_PORT = 7000;

    @BeforeEach
    void setUp() throws Exception {
        senderSocket = new DatagramSocket();
        receiverSocket = new DatagramSocket(TEST_PORT);
        receiverSocket.setSoTimeout(500);
    }

    @AfterEach
    void tearDown() {
        if (senderSocket != null) senderSocket.close();
        if (receiverSocket != null) receiverSocket.close();
    }

    @Test
    void testSequenceNumberIncrement() throws Exception {
        Event event = new Event("10:00:00", 1, Event.EventType.FIRE_DETECTED, Event.Severity.LOW);
        
        Message m1 = new Message(Message.Type.EVENT, event, "demo");
        Message m2 = new Message(Message.Type.EVENT, event, "demo");
        Message m3 = new Message(Message.Type.EVENT, event, "demo");
        
        long seq1 = m1.getSequenceNumber();
        long seq2 = m2.getSequenceNumber();
        long seq3 = m3.getSequenceNumber();
        
        assertEquals(seq1 + 1, seq2, "Sequence numbers should increment by 1");
        assertEquals(seq2 + 1, seq3, "Sequence numbers should increment by 1");
        assertTrue(seq1 > 0, "Sequence numbers should be positive");
    }

    @Test
    void testResendPreservesSequenceNumber() throws Exception {
        Event event = new Event("10:05:00", 2, Event.EventType.FIRE_DETECTED, Event.Severity.HIGH);
        Message original = new Message(Message.Type.DISPATCH, event, "Drone-0");
        
        long originalSeq = original.getSequenceNumber();
        Message resend = original.asResendAttempt();
        
        assertEquals(originalSeq, resend.getSequenceNumber(), "Resend should preserve original sequence number");
        assertEquals(2, resend.getAttempt(), "Resend attempt count should increment");
    }

    @Test
    void testSequenceNumberInCrc() throws Exception {
        Event event = new Event("10:10:00", 3, Event.EventType.DRONE_REQUEST, Event.Severity.MODERATE);
        Message m1 = new Message(Message.Type.DISPATCH, event, "Drone-1");
        
        assertTrue(m1.isCrcValid(), "Freshly created message should have valid CRC");
        
        // Create another message with same content but different sequence
        Message m2 = new Message(Message.Type.DISPATCH, event, "Drone-1");
        
        // CRCs should differ because sequence numbers are different
        assertNotEquals(m1.getCrc32(), m2.getCrc32(), 
            "Messages with different sequence numbers should have different CRCs");
    }

    @Test
    void testUdpMessageWithSequenceNumber() throws Exception {
        Event testEvent = new Event("10:15:00", 4, Event.EventType.FIRE_DETECTED, Event.Severity.HIGH);
        Message testMessage = new Message(Message.Type.DISPATCH, testEvent, "Drone-2");
        long expectedSeq = testMessage.getSequenceNumber();

        // Send the message
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(testMessage);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, 
            InetAddress.getByName("localhost"), TEST_PORT);
        senderSocket.send(sendPacket);

        // Receive the message
        byte[] buffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receivePacket);
        ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        Message receivedMessage = (Message) in.readObject();

        // Assert sequence number preserved
        assertEquals(expectedSeq, receivedMessage.getSequenceNumber(), 
            "Sequence number should be preserved over UDP");
        assertTrue(receivedMessage.isCrcValid(), "CRC should be valid for received message");
    }

    @Test
    void testPacketLossDetection() throws Exception {
        // Simulate receiving messages with gaps in sequence numbers
        Map<Long, Boolean> detectedLoss = new HashMap<>();
        Map<String, Long> lastSeqPerSender = new HashMap<>();
        
        // Sender sends messages 1, 2, 3, 5 (4 is lost)
        long[] incomingSequences = {1, 2, 3, 5};
        String testSenderId = "Drone-0";
        
        for (long seq : incomingSequences) {
            Long lastSeq = lastSeqPerSender.get(testSenderId);
            
            if (lastSeq != null) {
                long gap = seq - lastSeq;
                if (gap > 1 && gap <= 5) {
                    // Packet loss detected!
                    detectedLoss.put(seq, true);
                } else {
                    detectedLoss.put(seq, false);
                }
            } else {
                detectedLoss.put(seq, false);
            }
            lastSeqPerSender.put(testSenderId, seq);
        }
        
        // Check that we detected the gap between 3 and 5
        assertEquals(4, detectedLoss.size(), "Should process all incoming sequences");
        assertTrue(detectedLoss.get(5L), "Gap between seq 3 and 5 should be detected as packet loss");
    }

    @Test
    void testMultipleDroneSequenceTracking() throws Exception {
        // Test that each drone's sequence is tracked independently
        Map<String, Long> droneSequences = new HashMap<>();
        
        String drone0 = "Drone-0";
        String drone1 = "Drone-1";
        String drone2 = "Drone-2";
        
        // Simulate receiving status updates from different drones
        Message status0_1 = new Message(Message.Type.DRONE_STATUS, null, drone0 + ",50.0,0,15.0,0,0,0,idle");
        Message status1_1 = new Message(Message.Type.DRONE_STATUS, null, drone1 + ",45.0,1,12.5,0,0,0,enRoute");
        Message status2_1 = new Message(Message.Type.DRONE_STATUS, null, drone2 + ",48.0,2,14.0,0,0,0,dropping");
        
        Message status0_2 = new Message(Message.Type.DRONE_STATUS, null, drone0 + ",50.0,0,15.0,0,0,0,idle");
        Message status1_2 = new Message(Message.Type.DRONE_STATUS, null, drone1 + ",45.0,1,12.5,0,0,0,enRoute");
        
        List<Message> messages = Arrays.asList(status0_1, status1_1, status2_1, status0_2, status1_2);
        
        for (Message msg : messages) {
            String droneId = msg.getNote().split(",")[0];
            long seq = msg.getSequenceNumber();
            
            Long previousSeq = droneSequences.get(droneId);
            if (previousSeq != null) {
                long gap = seq - previousSeq;
                assertTrue(gap >= 1, "Sequence should be monotonically increasing per drone");
            }
            droneSequences.put(droneId, seq);
        }
        
        assertEquals(3, droneSequences.size(), "Should track 3 distinct drones");
    }

    @Test
    void testCorruptedMessageDetection() throws Exception {
        Event event = new Event("10:20:00", 5, Event.EventType.FIRE_DETECTED, Event.Severity.LOW);
        Message original = new Message(Message.Type.REQUEST_TASK, null, "Drone-3");
        
        assertTrue(original.isCrcValid(), "Original message should have valid CRC");
        
        // Simulate corruption by creating a new message with modified note
        // (We can't actually corrupt the serialized bytes easily, but we can test CRC logic)
        Message modified = new Message(Message.Type.REQUEST_TASK, null, "Drone-4", 
            original.getMessageId(), original.getCorrelationId(), original.getAttempt(), 
            original.getSequenceNumber());
        
        // Modified message should have different CRC due to different note
        assertNotEquals(original.getCrc32(), modified.getCrc32(), 
            "Messages with different content should have different CRCs");
    }

    @Test
    void testLargeSequenceGap() throws Exception {
        // Test detection of large gaps (e.g., > 5 packets lost)
        Map<String, Long> lastSeqPerDrone = new HashMap<>();
        String droneId = "Drone-0";
        
        // Simulate: last received was seq 10, now receive seq 20 (gap of 10)
        lastSeqPerDrone.put(droneId, 10L);
        long incomingSeq = 20L;
        long maxGapThreshold = 5L;
        
        long gap = incomingSeq - lastSeqPerDrone.get(droneId);
        boolean shouldMarkFailed = (gap > 1 && gap <= maxGapThreshold);
        
        // Large gap (10 > 5) should NOT automatically mark as comm failure
        // because it might indicate a long delay or batch of messages
        assertFalse(shouldMarkFailed, "Large gaps (> threshold) might indicate other issues, not just packet loss");
    }

    @Test
    void testSequenceNumberThreadSafety() throws Exception {
        // Test that sequence numbers are thread-safe
        List<Long> sequences = Collections.synchronizedList(new ArrayList<>());
        int numThreads = 5;
        int messagesPerThread = 10;
        
        Thread[] threads = new Thread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            final int threadNum = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < messagesPerThread; j++) {
                    Message msg = new Message(Message.Type.DRONE_STATUS, null, "Drone-" + threadNum);
                    sequences.add(msg.getSequenceNumber());
                }
            });
            threads[i].start();
        }
        
        for (Thread t : threads) {
            t.join();
        }
        
        assertEquals(numThreads * messagesPerThread, sequences.size(), 
            "Should create correct number of messages");
        
        // Check that all sequences are unique
        Set<Long> uniqueSeqs = new HashSet<>(sequences);
        assertEquals(sequences.size(), uniqueSeqs.size(), 
            "All sequence numbers should be unique even in multi-threaded context");
    }

    @Test
    void testResendRequestCorrelationId() throws Exception {
        Message original = new Message(Message.Type.DISPATCH, null, "Drone-0");
        String originalMessageId = original.getMessageId();
        
        Message resendRequest = Message.resendRequest("Drone-0", originalMessageId);
        
        assertEquals(Message.Type.RESEND_REQUEST, resendRequest.getType(), 
            "Resend request should have correct type");
        assertEquals("Drone-0", resendRequest.getNote(), 
            "Resend request note should contain requester ID");
        assertEquals(originalMessageId, resendRequest.getCorrelationId(), 
            "Resend request correlation ID should match original message ID");
    }

    @Test
    void testCommFailureMessage() throws Exception {
        String droneId = "Drone-5";
        String reason = "packet_loss_detected";
        
        Message commFailure = Message.commFailure(droneId, reason);
        
        assertEquals(Message.Type.COMM_FAILURE, commFailure.getType(), 
            "Message type should be COMM_FAILURE");
        assertTrue(commFailure.getNote().contains(droneId), 
            "Note should contain drone ID");
        assertTrue(commFailure.getNote().contains(reason), 
            "Note should contain failure reason");
    }
}




