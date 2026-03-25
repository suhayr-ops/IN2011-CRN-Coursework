// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  Suhayr Mohamud
//  YOUR_STUDENT_ID_NUMBER_GOES_HERE
//  suhayr.mohamud@city.ac.uk


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.HashMap;

interface NodeInterface {

    /* These methods configure your node.
     * They must both be called once after the node has been created but
     * before it is used. */
    
    // Set the name of the node.
    public void setNodeName(String nodeName) throws Exception;

    // Open a UDP port for sending and receiving messages.
    public void openPort(int portNumber) throws Exception;


    /*
     * These methods query and change how the network is used.
     */

    // Handle all incoming messages.
    // If you wait for more than delay miliseconds and
    // there are no new incoming messages return.
    // If delay is zero then wait for an unlimited amount of time.
    public void handleIncomingMessages(int delay) throws Exception;
    
    // Determines if a node can be contacted and is responding correctly.
    // Handles any messages that have arrived.
    public boolean isActive(String nodeName) throws Exception;

    // You need to keep a stack of nodes that are used to relay messages.
    // The base of the stack is the first node to be used as a relay.
    // The first node must relay to the second node and so on.
    
    // Adds a node name to a stack of nodes used to relay all future messages.
    public void pushRelay(String nodeName) throws Exception;

    // Pops the top entry from the stack of nodes used for relaying.
    // No effect if the stack is empty
    public void popRelay() throws Exception;
    

    /*
     * These methods provide access to the basic functionality of
     * CRN-25 network.
     */

    // Checks if there is an entry in the network with the given key.
    // Handles any messages that have arrived.
    public boolean exists(String key) throws Exception;
    
    // Reads the entry stored in the network for key.
    // If there is a value, return it.
    // If there isn't a value, return null.
    // Handles any messages that have arrived.
    public String read(String key) throws Exception;

    // Sets key to be value.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean write(String key, String value) throws Exception;

    // If key is set to currentValue change it to newValue.
    // Returns true if it worked, false if it didn't.
    // Handles any messages that have arrived.
    public boolean CAS(String key, String currentValue, String newValue) throws Exception;

}
// DO NOT EDIT ends

// Complete this!
public class Node implements NodeInterface {
    private String nodeName;
    private byte[] nodeHash;
    private DatagramSocket socket;

    private HashMap<String, String> store = new HashMap<>();
    private HashMap<String, String> recentResponses = new HashMap<>();
    private final HashMap<String, String> pendingResponses = new HashMap<>();
    private java.util.Stack<String> relayStack = new java.util.Stack<>();


    public void putLocal(String key, String value) {
        store.put(key, value);
    }

    private int txCounter = 0;

    private synchronized String nextTxID() {
        txCounter++;

        char c1 = (char) ('A' + (txCounter / 26) % 26);
        char c2 = (char) ('A' + txCounter % 26);

        return "" + c1 + c2;
    }


    private String sendRequest(String message) throws Exception {
        String finalMessage = message;

        // wrap with relay layers
        for (int i = relayStack.size() - 1; i >= 0; i--) {
            String relayNode = relayStack.get(i);
            String encodedNode = CRNUtils.encodeString(relayNode);
            finalMessage = finalMessage.substring(0, 2) + " V " + encodedNode + finalMessage.substring(3);
        }

        String txid = finalMessage.substring(0, 2);

        synchronized (pendingResponses) {
            pendingResponses.put(txid, null);
        }

        byte[] data = finalMessage.getBytes();

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                java.net.InetAddress.getLocalHost(), // local for now
                socket.getLocalPort()
        );

        int attempts = 0;

        while (attempts < 3) {
            socket.send(packet);

            long start = System.currentTimeMillis();

            while (System.currentTimeMillis() - start < 5000) {
                String response;

                synchronized (pendingResponses) {
                    response = pendingResponses.get(txid);
                }

                if (response != null) {
                    synchronized (pendingResponses) {
                        pendingResponses.remove(txid);
                    }
                    return response;
                }

                Thread.sleep(10);
            }

            attempts++;
        }

        synchronized (pendingResponses) {
            pendingResponses.remove(txid);
        }

        return null;
    }

    @Override
    public void setNodeName(String nodeName) throws Exception {
        this.nodeName = nodeName;
        this.nodeHash = HashID.computeHashID(nodeName);
    }

    @Override
    public void openPort(int portNumber) throws Exception {
        this.socket = new DatagramSocket(portNumber);

        Thread listener = new Thread(() -> {
            try {
                handleIncomingMessages(0);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        listener.setDaemon(true);
        listener.start();
    }

    private String respond(String txid, String response) {
        return response;
    }

    @Override
    public void handleIncomingMessages(int delay) throws Exception {
        byte[] buffer = new byte[65535];

        if (delay > 0) {
            socket.setSoTimeout(delay);
        } else {
            socket.setSoTimeout(0);
        }

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(packet);
            } catch (java.net.SocketTimeoutException e) {
                return;
            }

            String message = new String(packet.getData(), 0, packet.getLength());

            if (message.length() < 3) {
                continue;
            }

            String[] parts = message.split(" ", 3);
            if (parts.length < 2) {
                continue;
            }

            String txid = parts[0];
            String type = parts[1];

            boolean isResponseType =
                    type.equals("H") || type.equals("O") || type.equals("F") ||
                            type.equals("S") || type.equals("X") || type.equals("D");

            synchronized (pendingResponses) {
                if (isResponseType && pendingResponses.containsKey(txid)) {
                    pendingResponses.put(txid, message);
                    continue;
                }
            }

            String response = handleMessage(message);

            if (response != null) {
                byte[] responseBytes = response.getBytes();

                DatagramPacket reply = new DatagramPacket(
                        responseBytes,
                        responseBytes.length,
                        packet.getAddress(),
                        packet.getPort()
                );

                socket.send(reply);
            }
        }
    }
    public String handleMessage(String message) {
        try {

            String[] parts = message.split(" ", 3);

            String txid = parts[0];
            String type = parts[1];

            System.out.println("Node " + nodeName + " received: " + message);

            if (type.equals("V")) {
                String rest = parts[2];

                int firstSpace = rest.indexOf(' ');
                int spaceCount = Integer.parseInt(rest.substring(0, firstSpace));

                int index = firstSpace + 1;
                int spacesSeen = 0;

                while (spacesSeen <= spaceCount && index < rest.length()) {
                    if (rest.charAt(index) == ' ') {
                        spacesSeen++;
                    }
                    index++;
                }

                String encodedNode = rest.substring(0, index);
                String embeddedMessage = rest.substring(index);

                // simulate relay locally
                String response = handleMessage(txid + " " + embeddedMessage);

                if (response == null) return null;

                // preserve TXID properly
                String newResponse = txid + response.substring(2);

                return respond(txid, newResponse);
            }
            // G → Name
            if (type.equals("G")) {
                String encodedName = CRNUtils.encodeString(this.nodeName);
                return respond(txid, txid + " H " + encodedName);
            }

            // N → Nearest
            if (type.equals("N")) {
                String encodedName = CRNUtils.encodeString(this.nodeName);
                return respond(txid, txid + " O " + encodedName);
            }

            // E → Exists
            if (type.equals("E")) {
                String encodedKey = parts[2];
                String key = CRNUtils.decodeString(encodedKey);

                if (store.containsKey(key)) {
                    return respond(txid, txid + " F Y ");
                } else {
                    return respond(txid, txid + " F N ");
                }
            }

            // R → Read
            if (type.equals("R")) {
                String encodedKey = parts[2];
                String key = CRNUtils.decodeString(encodedKey);

                if (store.containsKey(key)) {
                    String value = store.get(key);
                    String encodedValue = CRNUtils.encodeString(value);
                    return respond(txid, txid + " S Y " + encodedValue);
                } else {
                    return respond(txid, txid + " S N ");
                }
            }

            // W → Write
            if (type.equals("W")) {
                String rest = parts[2];

                int firstSpace = rest.indexOf(' ');
                int spaceCount = Integer.parseInt(rest.substring(0, firstSpace));

                int index = firstSpace + 1;
                int spacesSeen = 0;

                while (spacesSeen <= spaceCount && index < rest.length()) {
                    if (rest.charAt(index) == ' ') {
                        spacesSeen++;
                    }
                    index++;
                }

                String encodedKey = rest.substring(0, index);
                String encodedValue = rest.substring(index);

                String key = CRNUtils.decodeString(encodedKey);
                String value = CRNUtils.decodeString(encodedValue);

                if (store.containsKey(key)) {
                    store.put(key, value);
                    return respond(txid, txid + " X R ");
                } else {
                    store.put(key, value);
                    return respond(txid, txid + " X A ");
                }
            }
            if (type.equals("C")) {
                String rest = parts[2];

                // --- extract key ---
                int firstSpace = rest.indexOf(' ');
                int spaceCount1 = Integer.parseInt(rest.substring(0, firstSpace));

                int index = firstSpace + 1;
                int spacesSeen = 0;

                while (spacesSeen <= spaceCount1 && index < rest.length()) {
                    if (rest.charAt(index) == ' ') {
                        spacesSeen++;
                    }
                    index++;
                }

                String encodedKey = rest.substring(0, index);
                String remaining1 = rest.substring(index);

                // --- extract currentValue ---
                int firstSpace2 = remaining1.indexOf(' ');
                int spaceCount2 = Integer.parseInt(remaining1.substring(0, firstSpace2));

                int index2 = firstSpace2 + 1;
                int spacesSeen2 = 0;

                while (spacesSeen2 <= spaceCount2 && index2 < remaining1.length()) {
                    if (remaining1.charAt(index2) == ' ') {
                        spacesSeen2++;
                    }
                    index2++;
                }

                String encodedCurrent = remaining1.substring(0, index2);
                String encodedNew = remaining1.substring(index2);

                String key = CRNUtils.decodeString(encodedKey);
                String currentValue = CRNUtils.decodeString(encodedCurrent);
                String newValue = CRNUtils.decodeString(encodedNew);

                if (store.containsKey(key) && store.get(key).equals(currentValue)) {
                    store.put(key, newValue);
                    return respond(txid, txid + " D Y ");
                } else {
                    return respond(txid, txid + " D N ");
                }
            }


            return null;

        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isActive(String nodeName) throws Exception {

        String txid = nextTxID();

        String request = txid + " G";

        String response = sendRequest(request);

        if (response == null) return false;

        // Check it's a name response
        if (!response.contains(" H ")) return false;

        // Extract returned name
        String encodedName = response.substring(response.indexOf(" H ") + 3);
        String returnedName = CRNUtils.decodeString(encodedName);

        return returnedName.equals(nodeName);
    }

    public void pushRelay(String nodeName) {
        relayStack.push(nodeName);
    }

    public void popRelay() {
        if (!relayStack.isEmpty()) {
            relayStack.pop();
        }
    }

    @Override
    public boolean exists(String key) throws Exception {

        String txid = nextTxID();

        String encodedKey = CRNUtils.encodeString(key);

        String request = txid + " E " + encodedKey;

        String response = sendRequest(request);

        return response != null && response.contains(" F Y ");
    }

    @Override
    public String read(String key) throws Exception {

        String txid = nextTxID();

        String encodedKey = CRNUtils.encodeString(key);

        String request = txid + " R " + encodedKey;

        String response = sendRequest(request);

        if (response == null) return null;

        if (response.contains(" S Y ")) {
            String encodedValue = response.substring(response.indexOf(" S Y ") + 5);
            return CRNUtils.decodeString(encodedValue);
        }

        return null;
    }

    @Override
    public boolean write(String key, String value) throws Exception {

        String txid = nextTxID();

        String encodedKey = CRNUtils.encodeString(key);
        String encodedValue = CRNUtils.encodeString(value);

        String request = txid + " W " + encodedKey + encodedValue;

        String response = sendRequest(request);

        if (response == null) return false;

        // Check response type
        return response.contains(" X A ") || response.contains(" X R ");
    }

    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        String txid = nextTxID();

        String encodedKey = CRNUtils.encodeString(key);
        String encodedCurrent = CRNUtils.encodeString(currentValue);
        String encodedNew = CRNUtils.encodeString(newValue);

        String request = txid + " C " + encodedKey + encodedCurrent + encodedNew;

        String response = sendRequest(request);

        return response != null && response.contains(" D Y ");
    }
}
