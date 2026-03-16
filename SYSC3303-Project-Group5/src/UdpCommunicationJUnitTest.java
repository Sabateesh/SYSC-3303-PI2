import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.io.*;

public class UdpCommunicationJUnitTest {

    @Test
    void testUdpMessageSendReceive() throws Exception {
        // Create two sockets on different ports
        DatagramSocket senderSocket = new DatagramSocket();
        DatagramSocket receiverSocket = new DatagramSocket(6000); // test port

        // Create a test message
        Event testEvent = new Event("10:00:00", 1, Event.EventType.FIRE_DETECTED, Event.Severity.LOW);
        Message testMessage = new Message(Message.Type.EVENT, testEvent, "test note");

        // Send the message
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(testMessage);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), 6000);
        senderSocket.send(sendPacket);

        // Receive the message
        byte[] buffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receivePacket);
        ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        Message receivedMessage = (Message) in.readObject();

        // Assert the contents
        assertEquals(Message.Type.EVENT, receivedMessage.getType());
        assertNotNull(receivedMessage.getEvent());
        assertEquals("10:00:00", receivedMessage.getEvent().getTime());
        assertEquals(1, receivedMessage.getEvent().getZoneID());
        assertEquals(Event.EventType.FIRE_DETECTED, receivedMessage.getEvent().getEventType());
        assertEquals(Event.Severity.LOW, receivedMessage.getEvent().getSeverity());
        assertEquals("test note", receivedMessage.getNote());

        // Clean up
        senderSocket.close();
        receiverSocket.close();
    }

    @Test
    void testUdpRequestTaskMessage() throws Exception {
        DatagramSocket senderSocket = new DatagramSocket();
        DatagramSocket receiverSocket = new DatagramSocket(6001);

        Message requestMessage = new Message(Message.Type.REQUEST_TASK, null, "Drone-0");

        // Send
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(requestMessage);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), 6001);
        senderSocket.send(sendPacket);

        // Receive
        byte[] buffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receivePacket);
        ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        Message receivedMessage = (Message) in.readObject();

        // Assert
        assertEquals(Message.Type.REQUEST_TASK, receivedMessage.getType());
        assertNull(receivedMessage.getEvent());
        assertEquals("Drone-0", receivedMessage.getNote());

        senderSocket.close();
        receiverSocket.close();
    }

    @Test
    void testUdpDispatchMessage() throws Exception {
        DatagramSocket senderSocket = new DatagramSocket();
        DatagramSocket receiverSocket = new DatagramSocket(6002);

        Event dispatchEvent = new Event("10:05:00", 2, Event.EventType.FIRE_DETECTED, Event.Severity.HIGH);
        Message dispatchMessage = new Message(Message.Type.DISPATCH, dispatchEvent, "Drone-1");

        // Send
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(dispatchMessage);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), 6002);
        senderSocket.send(sendPacket);

        // Receive
        byte[] buffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receivePacket);
        ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        Message receivedMessage = (Message) in.readObject();

        // Assert
        assertEquals(Message.Type.DISPATCH, receivedMessage.getType());
        assertNotNull(receivedMessage.getEvent());
        assertEquals("10:05:00", receivedMessage.getEvent().getTime());
        assertEquals(2, receivedMessage.getEvent().getZoneID());
        assertEquals(Event.EventType.FIRE_DETECTED, receivedMessage.getEvent().getEventType());
        assertEquals(Event.Severity.HIGH, receivedMessage.getEvent().getSeverity());
        assertEquals("Drone-1", receivedMessage.getNote());

        senderSocket.close();
        receiverSocket.close();
    }

    @Test
    void testUdpDoneMessage() throws Exception {
        DatagramSocket senderSocket = new DatagramSocket();
        DatagramSocket receiverSocket = new DatagramSocket(6003);

        Event doneEvent = new Event("10:10:00", 3, Event.EventType.FIRE_DETECTED, Event.Severity.MODERATE);
        Message doneMessage = new Message(Message.Type.DONE, doneEvent, "Drone-2");

        // Send
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(doneMessage);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), 6003);
        senderSocket.send(sendPacket);

        // Receive
        byte[] buffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receivePacket);
        ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        Message receivedMessage = (Message) in.readObject();

        // Assert
        assertEquals(Message.Type.DONE, receivedMessage.getType());
        assertNotNull(receivedMessage.getEvent());
        assertEquals("10:10:00", receivedMessage.getEvent().getTime());
        assertEquals(3, receivedMessage.getEvent().getZoneID());
        assertEquals(Event.EventType.FIRE_DETECTED, receivedMessage.getEvent().getEventType());
        assertEquals(Event.Severity.MODERATE, receivedMessage.getEvent().getSeverity());
        assertEquals("Drone-2", receivedMessage.getNote());

        senderSocket.close();
        receiverSocket.close();
    }

    @Test
    void testUdpAckMessage() throws Exception {
        DatagramSocket senderSocket = new DatagramSocket();
        DatagramSocket receiverSocket = new DatagramSocket(6004);

        Message ackMessage = new Message(Message.Type.ACK, null, "Event received");

        // Send
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(ackMessage);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), 6004);
        senderSocket.send(sendPacket);

        // Receive
        byte[] buffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receivePacket);
        ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        Message receivedMessage = (Message) in.readObject();

        // Assert
        assertEquals(Message.Type.ACK, receivedMessage.getType());
        assertNull(receivedMessage.getEvent());
        assertEquals("Event received", receivedMessage.getNote());

        senderSocket.close();
        receiverSocket.close();
    }

    @Test
    void testUdpNoTaskMessage() throws Exception {
        DatagramSocket senderSocket = new DatagramSocket();
        DatagramSocket receiverSocket = new DatagramSocket(6005);

        Message noTaskMessage = new Message(Message.Type.NO_TASK, null, "Drone-3");

        // Send
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(noTaskMessage);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), 6005);
        senderSocket.send(sendPacket);

        // Receive
        byte[] buffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receivePacket);
        ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        Message receivedMessage = (Message) in.readObject();

        // Assert
        assertEquals(Message.Type.NO_TASK, receivedMessage.getType());
        assertNull(receivedMessage.getEvent());
        assertEquals("Drone-3", receivedMessage.getNote());

        senderSocket.close();
        receiverSocket.close();
    }

    @Test
    void testUdpDroneStatusMessage() throws Exception {
        DatagramSocket senderSocket = new DatagramSocket();
        DatagramSocket receiverSocket = new DatagramSocket(6006);

        Message statusMessage = new Message(Message.Type.DRONE_STATUS, null, "Drone-0,45.0,1,12.5");

        // Send
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(statusMessage);
        out.flush();
        byte[] data = bos.toByteArray();
        DatagramPacket sendPacket = new DatagramPacket(data, data.length, InetAddress.getByName("localhost"), 6006);
        senderSocket.send(sendPacket);

        // Receive
        byte[] buffer = new byte[4096];
        DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
        receiverSocket.receive(receivePacket);
        ByteArrayInputStream bis = new ByteArrayInputStream(receivePacket.getData(), 0, receivePacket.getLength());
        ObjectInputStream in = new ObjectInputStream(bis);
        Message receivedMessage = (Message) in.readObject();

        // Assert
        assertEquals(Message.Type.DRONE_STATUS, receivedMessage.getType());
        assertNull(receivedMessage.getEvent());
        assertEquals("Drone-0,45.0,1,12.5", receivedMessage.getNote());

        senderSocket.close();
        receiverSocket.close();
    }
}
