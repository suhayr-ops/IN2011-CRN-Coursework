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
import java.net.InetAddress;
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
    private final HashMap<String, String> pendingResponses = new HashMap<>();
    private java.util.Stack<String> relayStack = new java.util.Stack<>();
    private HashMap<String, String> addressBook = new HashMap<>();

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
    private byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];

        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) (
                    (Character.digit(hex.charAt(i), 16) << 4)
                            + Character.digit(hex.charAt(i+1), 16)
            );
        }

        return data;
    }
    private String findClosestNode(String key) throws Exception {
        byte[] keyHash = HashID.computeHashID(key);

        int bestDistance = HashID.distance(this.nodeHash, keyHash);
        String bestNode = this.nodeName;

        for (String node : addressBook.keySet()) {
            byte[] nodeHash = HashID.computeHashID(node);
            int dist = HashID.distance(nodeHash, keyHash);

            if (dist < bestDistance) {
                bestDistance = dist;
                bestNode = node;
            }
        }

        return bestNode;
    }
    private String sendRequestToNode(String message, String address) throws Exception {

        String txid = message.substring(0, 2);

        synchronized (pendingResponses) {
            pendingResponses.put(txid, null);
        }

        String[] parts = address.split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        byte[] data = message.getBytes();

        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                java.net.InetAddress.getByName(host),
                port
        );

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

        synchronized (pendingResponses) {
            pendingResponses.remove(txid);
        }

        return null;
    }
    private int encodedLength(String encoded) {
        int firstSpace = encoded.indexOf(' ');
        int spaceCount = Integer.parseInt(encoded.substring(0, firstSpace));

        int index = firstSpace + 1;
        int spacesSeen = 0;

        while (spacesSeen <= spaceCount && index < encoded.length()) {
            if (encoded.charAt(index) == ' ') {
                spacesSeen++;
            }
            index++;
        }

        return index;
    }

    private String sendRequest(String message) throws Exception {
        String finalMessage = message;

        // wrap with relay layers
        for (int i = relayStack.size() - 1; i >= 0; i--) {
            String relayNode = relayStack.get(i);
            String encodedNode = CRNUtils.encodeString(relayNode);
            finalMessage = finalMessage.substring(0, 2) + " V " + encodedNode + " " + finalMessage.substring(3);
        }

        String txid = finalMessage.substring(0, 2);

        synchronized (pendingResponses) {
            pendingResponses.put(txid, null);
        }

        byte[] data = finalMessage.getBytes();

        String address;

        if (!relayStack.isEmpty()) {

            String relayNode = relayStack.peek();

            // Ensure consistent format
            if (!relayNode.startsWith("N:")) {
                relayNode = "N:" + relayNode;
            }

            address = addressBook.get(relayNode);

            if (address == null) {
                System.out.println("Relay lookup failed for: " + relayNode);
                return null; // don't crash
            }

        } else {
            // fallback to local node (important for non-relay cases)
            address = "127.0.0.1:" + socket.getLocalPort();
        }

        String[] parts = address.split(":");
        InetAddress host = InetAddress.getByName(parts[0]);
        int port = Integer.parseInt(parts[1]);

        DatagramPacket packet = new DatagramPacket(data, data.length, host, port);

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
    void bootstrapNetwork() throws Exception {
        String bootstrapAddress = "10.216.34.30:20110"; // Azure node

        String txid = nextTxID();
        String hash = HashID.bytesToHex(HashID.computeHashID("bootstrap"));

        String request = txid + " N " + hash;

        String response = sendRequestToNode(request, bootstrapAddress);

        if (response != null && response.contains(" O ")) {
            String data = response.substring(response.indexOf(" O ") + 3);

            while (!data.isEmpty()) {

                // decode node
                String node = CRNUtils.decodeString(data);

                int nodeLen = encodedLength(data);
                data = data.substring(nodeLen);

                // decode address
                String addr = CRNUtils.decodeString(data);

                int addrLen = encodedLength(data);
                data = data.substring(addrLen);

                addressBook.put(node, addr);
            }
        }
    }
    private java.util.List<String> getClosestNodes(String key) throws Exception {
        byte[] keyHash = HashID.computeHashID(key);

        java.util.List<String> nodes = new java.util.ArrayList<>(addressBook.keySet());

        nodes.sort((a, b) -> {
            try {
                int da = HashID.distance(HashID.computeHashID(a), keyHash);
                int db = HashID.distance(HashID.computeHashID(b), keyHash);
                return Integer.compare(da, db);
            } catch (Exception e) {
                return 0;
            }
        });

        return nodes.subList(0, Math.min(3, nodes.size()));
    }
    public void debugAddressBook() {
        System.out.println("AddressBook size = " + addressBook.size());
        System.out.println(addressBook);
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
            // detect node introduction (bootstrap message)

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

                String targetNode = CRNUtils.decodeString(encodedNode);
                // Ensure consistent format
                if (!targetNode.startsWith("N:")) {
                    targetNode = "N:" + targetNode;
                }

                if (targetNode.equals(this.nodeName)) {
                    // stop relaying → process locally
                    return handleMessage(txid + " " + embeddedMessage.trim());
                }

                String address = addressBook.get(targetNode);
                if (address == null) return null;

                String forwardedMessage = txid + " " + embeddedMessage.trim();
                String response = sendRequestToNode(forwardedMessage, address);

                if (response == null) return null;

                // restore original TXID
                String newResponse = txid + " " + response.substring(3);

                return respond(txid, newResponse);
            }
            // G → Name
            if (type.equals("G")) {
                String encodedName = CRNUtils.encodeString(this.nodeName);
                return respond(txid, txid + " H " + encodedName);
            }

            // N → Nearest
            if (type.equals("N")) {
                String hashHex = parts[2];
                byte[] targetHash = hexStringToBytes(hashHex);

                java.util.List<String> nodes = new java.util.ArrayList<>(addressBook.keySet());

                nodes.sort((a, b) -> {
                    try {
                        int da = HashID.distance(targetHash, HashID.computeHashID(a));
                        int db = HashID.distance(targetHash, HashID.computeHashID(b));
                        return Integer.compare(da, db);
                    } catch (Exception e) {
                        return 0;
                    }
                });

                StringBuilder result = new StringBuilder();
                int count = Math.min(3, nodes.size());

                for (int i = 0; i < count; i++) {
                    String node = nodes.get(i);
                    String addr = addressBook.get(node);

                    result.append(CRNUtils.encodeString(node));
                    result.append(CRNUtils.encodeString(addr));
                }

                return txid + " O " + result.toString();
            }

            // E → Exists
            if (type.equals("E")) {
                String encodedKey = parts[2];
                String key = CRNUtils.decodeString(encodedKey);

                boolean A = store.containsKey(key);

                String closestNode = findClosestNode(key);
                boolean B = closestNode.equals(this.nodeName);

                // If not one of closest nodes → forward
                if (!B) {
                    String address = addressBook.get(closestNode);
                    if (address != null) {
                        return sendRequestToNode(message, address);
                    }
                }

                if (A) return txid + " F Y ";
                if (B) return txid + " F N ";
                return txid + " F ? ";
            }

            // R → Read
            if (type.equals("R")) {
                String encodedKey = parts[2];
                String key = CRNUtils.decodeString(encodedKey);

                boolean A = store.containsKey(key);

                String closestNode = findClosestNode(key);
                boolean B = closestNode.equals(this.nodeName);

                // Forward if not closest
                if (!B) {
                    String address = addressBook.get(closestNode);
                    if (address != null) {
                        return sendRequestToNode(message, address);
                    }
                }

                if (A) {
                    String value = store.get(key);
                    return txid + " S Y " + CRNUtils.encodeString(value);
                }

                if (B) return txid + " S N ";
                return txid + " S ? ";
            }

            // W → Write
            if (type.equals("W")) {
                String rest = parts[2];

                // --- decode key ---
                int keyLen = encodedLength(rest);
                String encodedKey = rest.substring(0, keyLen);
                String key = CRNUtils.decodeString(encodedKey);

                // --- decode value ---
                String remaining = rest.substring(keyLen);
                int valueLen = encodedLength(remaining);
                String encodedValue = remaining.substring(0, valueLen);
                String value = CRNUtils.decodeString(encodedValue);

                // --- ADDRESS ENTRY (N:...) ---
                if (key.startsWith("N:")) {
                    boolean existed = store.containsKey(key);

                    store.put(key, value);
                    addressBook.put(key, value);

                    return existed ? (txid + " X R ") : (txid + " X A ");
                }

                // --- DATA ENTRY (D:...) ---
                String closestNode = findClosestNode(key);

                // forward if not closest
                if (!closestNode.equals(this.nodeName)) {
                    String address = addressBook.get(closestNode);
                    if (address != null) {
                        return sendRequestToNode(message, address);
                    }
                }

                boolean existed = store.containsKey(key);
                store.put(key, value);

                return existed ? (txid + " X R ") : (txid + " X A ");
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

                String closestNode = findClosestNode(key);

                if (!closestNode.equals(this.nodeName)) {
                    String address = addressBook.get(closestNode);
                    return sendRequestToNode(message, address);
                }

                if (!store.containsKey(key)) {
                    // Key does not exist → ADD
                    store.put(key, newValue);
                    return respond(txid, txid + " D A ");
                }

                if (store.get(key).equals(currentValue)) {
                    // Match → REPLACE
                    store.put(key, newValue);
                    return respond(txid, txid + " D R ");
                }

                // No match → DO NOTHING
                return respond(txid, txid + " D N ");
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
        if (store.containsKey(key)) return true;

        String txid = nextTxID();

        String encodedKey = CRNUtils.encodeString(key);

        String request = txid + " E " + encodedKey;

        // route to closest node
        String closestNode = findClosestNode(key);
        String address = addressBook.get(closestNode);

        String response;

        if (address != null) {
            response = sendRequestToNode(request, address);
        } else {
            // fallback (shouldn't happen often)
            response = sendRequest(request);
        }

        return response != null && response.contains(" F Y ");
    }

    @Override
    public String read(String key) throws Exception {

        // check locally
        if (store.containsKey(key)) {
            return store.get(key);
        }

        String txid = nextTxID();
        String encodedKey = CRNUtils.encodeString(key);
        String request = txid + " R " + encodedKey;

        java.util.List<String> nodes = getClosestNodes(key);

        for (String node : nodes) {
            String address = addressBook.get(node);

            if (address == null) continue;

            String response = sendRequestToNode(request, address);

            if (response == null) continue;

            if (response.contains(" S Y ")) {
                String encodedValue = response.substring(response.indexOf(" S Y ") + 5);
                return CRNUtils.decodeString(encodedValue);
            }
        }

        return null;
    }

    @Override
    public boolean write(String key, String value) throws Exception {

        String txid = nextTxID();

        String encodedKey = CRNUtils.encodeString(key);
        String encodedValue = CRNUtils.encodeString(value);

        String request = txid + " W " + encodedKey + encodedValue;

        // find closest node
        String closestNode = findClosestNode(key);
        String address = addressBook.get(closestNode);

        String response;

        if (address != null) {
            response = sendRequestToNode(request, address);
        } else {
            // fallback to local
            response = sendRequest(request);
        }

        return response != null &&
                (response.contains(" X A ") || response.contains(" X R "));
    }

    @Override
    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        String txid = nextTxID();

        String encodedKey = CRNUtils.encodeString(key);
        String encodedCurrent = CRNUtils.encodeString(currentValue);
        String encodedNew = CRNUtils.encodeString(newValue);

        String request = txid + " C " + encodedKey + encodedCurrent + encodedNew;

        String closestNode = findClosestNode(key);
        String address = addressBook.get(closestNode);

        String response;

        if (address != null) {
            response = sendRequestToNode(request, address);
        } else {
            response = sendRequest(request);
        }

        return response != null &&
                (response.contains(" D R ") || response.contains(" D A "));
    }
}
