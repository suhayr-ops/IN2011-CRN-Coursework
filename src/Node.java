// IN2011 Computer Networks
// Coursework 2024/2025
//
// Submission by
//  Suhayr Mohamud
//  240017805
//  suhayr.mohamud@city.ac.uk


// DO NOT EDIT starts
// This gives the interface that your code must implement.
// These descriptions are intended to help you understand how the interface
// will be used. See the RFC for how the protocol works.

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
// DO NOT EDIT ends

public class Node implements NodeInterface {
    private static final int RESPONSE_TIMEOUT_MS = 5000;
    private static final int MAX_RESENDS = 3;
    private static final int HANDLE_POLL_MS = 100;
    private static final int MAX_NEAREST_QUERIES = 8;
    private static final int TXID_FIRST_CHAR = 33;
    private static final int TXID_CHAR_COUNT = 94;

    private String nodeName;
    private byte[] nodeHash;
    private DatagramSocket socket;
    private String selfAddress;

    private final HashMap<String, String> store = new HashMap<>();
    private final HashMap<String, String> addressBook = new HashMap<>();
    private final HashMap<String, String> pendingResponses = new HashMap<>();
    private final Stack<String> relayStack = new Stack<>();

    public void putLocal(String key, String value) {
        synchronized (store) {
            store.put(key, value);
        }
    }

    private int txCounter = 0;

    private synchronized String nextTxID() {
        txCounter = (txCounter + 1) % (TXID_CHAR_COUNT * TXID_CHAR_COUNT);
        char c1 = (char) (TXID_FIRST_CHAR + (txCounter / TXID_CHAR_COUNT));
        char c2 = (char) (TXID_FIRST_CHAR + (txCounter % TXID_CHAR_COUNT));
        return "" + c1 + c2;
    }

    private static class ParsedString {
        final String value;
        final int nextIndex;

        ParsedString(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }

    private byte[] hexStringToBytes(String hex) throws Exception {
        if (hex.length() != 64) {
            throw new IllegalArgumentException("hashID must contain 64 hex chars");
        }

        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < hex.length(); i += 2) {
            int hi = Character.digit(hex.charAt(i), 16);
            int lo = Character.digit(hex.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("invalid hex");
            }
            data[i / 2] = (byte) ((hi << 4) + lo);
        }
        return data;
    }

    private ParsedString parseEncodedString(String s, int start) throws Exception {
        if (start >= s.length()) {
            throw new IllegalArgumentException("missing encoded string");
        }

        int firstSpace = s.indexOf(' ', start);
        if (firstSpace < 0) {
            throw new IllegalArgumentException("missing separator");
        }

        int spaceCount = Integer.parseInt(s.substring(start, firstSpace));
        int index = firstSpace + 1;
        int spacesSeen = 0;

        while (index < s.length()) {
            if (s.charAt(index) == ' ') {
                if (spacesSeen == spaceCount) {
                    return new ParsedString(s.substring(firstSpace + 1, index), index + 1);
                }
                spacesSeen++;
            }
            index++;
        }

        throw new IllegalArgumentException("unterminated encoded string");
    }

    private boolean isValidNodeName(String name) {
        return name != null && name.startsWith("N:");
    }

    private boolean isValidDataName(String name) {
        return name != null && name.startsWith("D:");
    }

    private boolean isValidAddress(String address) {
        if (address == null) {
            return false;
        }

        int colon = address.lastIndexOf(':');
        if (colon <= 0 || colon == address.length() - 1) {
            return false;
        }

        String host = address.substring(0, colon);
        String portText = address.substring(colon + 1);
        try {
            int port = Integer.parseInt(portText);
            if (port < 0 || port > 65535) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }

        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }

        for (String part : parts) {
            try {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }

        return true;
    }

    private String getAddressForNode(String name) {
        if (name == null) {
            return null;
        }
        if (name.equals(nodeName)) {
            return selfAddress;
        }
        synchronized (addressBook) {
            return addressBook.get(name);
        }
    }

    private void learnAddress(String name, String address) {
        if (!isValidNodeName(name) || !isValidAddress(address) || nodeName == null || nodeHash == null) {
            return;
        }

        if (name.equals(nodeName)) {
            selfAddress = address;
            return;
        }

        try {
            int newDistance = HashID.distance(nodeHash, HashID.computeHashID(name));

            synchronized (addressBook) {
                if (addressBook.containsKey(name)) {
                    addressBook.put(name, address);
                    return;
                }

                int atDistance = 0;
                for (String existing : addressBook.keySet()) {
                    int distance = HashID.distance(nodeHash, HashID.computeHashID(existing));
                    if (distance == newDistance) {
                        atDistance++;
                    }
                }

                if (atDistance < 3) {
                    addressBook.put(name, address);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private List<String> getKnownNodeNames() {
        ArrayList<String> nodes = new ArrayList<>();
        if (nodeName != null) {
            nodes.add(nodeName);
        }
        synchronized (addressBook) {
            nodes.addAll(addressBook.keySet());
        }
        return nodes;
    }

    private List<String> sortNodesByDistance(byte[] targetHash) throws Exception {
        ArrayList<String> nodes = new ArrayList<>(getKnownNodeNames());
        nodes.sort((a, b) -> {
            try {
                int da = HashID.distance(HashID.computeHashID(a), targetHash);
                int db = HashID.distance(HashID.computeHashID(b), targetHash);
                if (da != db) {
                    return Integer.compare(da, db);
                }
                return a.compareTo(b);
            } catch (Exception e) {
                return 0;
            }
        });
        return nodes;
    }

    private List<String> getClosestKnownNodes(byte[] targetHash, int limit) throws Exception {
        List<String> sorted = sortNodesByDistance(targetHash);
        return sorted.subList(0, Math.min(limit, sorted.size()));
    }

    private int countStrictlyCloserNodes(String key) throws Exception {
        byte[] keyHash = HashID.computeHashID(key);
        int selfDistance = HashID.distance(nodeHash, keyHash);
        int count = 0;

        synchronized (addressBook) {
            for (String knownNode : addressBook.keySet()) {
                int distance = HashID.distance(HashID.computeHashID(knownNode), keyHash);
                if (distance < selfDistance) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean isOneOfThreeClosest(String key) throws Exception {
        return countStrictlyCloserNodes(key) < 3;
    }

    private void sendUdpMessage(String message, String address) throws Exception {
        if (socket == null) {
            throw new IllegalStateException("Port not opened");
        }

        String[] parts = address.split(":");
        InetAddress host = InetAddress.getByName(parts[0]);
        int port = Integer.parseInt(parts[1]);
        byte[] data = message.getBytes();
        DatagramPacket packet = new DatagramPacket(data, data.length, host, port);
        socket.send(packet);
    }

    private void handleResponseMessage(String message, InetAddress senderAddress, int senderPort) {
        try {
            String[] parts = message.split(" ", 3);
            if (parts.length < 2) {
                return;
            }

            String txid = parts[0];
            String type = parts[1];
            String sender = senderAddress == null ? null : senderAddress.getHostAddress() + ":" + senderPort;

            if ("H".equals(type) && parts.length == 3 && sender != null) {
                ParsedString node = parseEncodedString(parts[2], 0);
                learnAddress(node.value, sender);
            } else if ("O".equals(type) && parts.length == 3) {
                int index = 0;
                while (index < parts[2].length()) {
                    ParsedString node = parseEncodedString(parts[2], index);
                    ParsedString address = parseEncodedString(parts[2], node.nextIndex);
                    learnAddress(node.value, address.value);
                    index = address.nextIndex;
                }
            }

            synchronized (pendingResponses) {
                if (pendingResponses.containsKey(txid) && pendingResponses.get(txid) == null) {
                    pendingResponses.put(txid, message);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String waitForPendingResponse(String txid, int timeoutMs) throws Exception {
        long start = System.currentTimeMillis();

        while (System.currentTimeMillis() - start < timeoutMs) {
            handleIncomingMessages(10);

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

            Thread.sleep(5);
        }

        synchronized (pendingResponses) {
            pendingResponses.remove(txid);
        }
        return null;
    }

    private String wrapRelayMessage(String message, String relayTarget) {
        String target = relayTarget;
        if (!target.startsWith("N:")) {
            target = "N:" + target;
        }
        String txid = message.substring(0, 2);
        return txid + " V " + CRNUtils.encodeString(target) + message;
    }

    private void drainIncomingMessages() throws Exception {
        if (socket != null) {
            handleIncomingMessages(1);
        }
    }

    private String sendRequestToNode(String message, String targetNode, String targetAddress) throws Exception {
        drainIncomingMessages();

        if (targetNode != null && targetNode.equals(nodeName) && relayStack.isEmpty()) {
            return processMessage(message, null, -1);
        }

        String outboundMessage = message;
        String firstHopAddress = targetAddress;

        if (!relayStack.isEmpty()) {
            String nextTarget = targetNode;
            for (int i = relayStack.size() - 1; i >= 0; i--) {
                outboundMessage = wrapRelayMessage(outboundMessage, nextTarget);
                String relayNode = relayStack.get(i);
                if (!relayNode.startsWith("N:")) {
                    relayNode = "N:" + relayNode;
                }
                nextTarget = relayNode;
            }
            firstHopAddress = getAddressForNode(nextTarget);
        }

        if (firstHopAddress == null) {
            return null;
        }

        String txid = outboundMessage.substring(0, 2);
        synchronized (pendingResponses) {
            pendingResponses.put(txid, null);
        }

        for (int resend = 0; resend <= MAX_RESENDS; resend++) {
            sendUdpMessage(outboundMessage, firstHopAddress);
            String response = waitForPendingResponse(txid, RESPONSE_TIMEOUT_MS);
            if (response != null) {
                return response;
            }
            if (resend < MAX_RESENDS) {
                synchronized (pendingResponses) {
                    pendingResponses.put(txid, null);
                }
            }
        }

        synchronized (pendingResponses) {
            pendingResponses.remove(txid);
        }
        return null;
    }

    private void queryNearestNodes(byte[] keyHash) throws Exception {
        String hashHex = HashID.bytesToHex(keyHash);
        Set<String> queried = new HashSet<>();

        for (int i = 0; i < MAX_NEAREST_QUERIES; i++) {
            String nextNode = null;
            int bestDistance = Integer.MAX_VALUE;

            synchronized (addressBook) {
                for (Map.Entry<String, String> entry : addressBook.entrySet()) {
                    String node = entry.getKey();
                    if (queried.contains(node)) {
                        continue;
                    }

                    int distance = HashID.distance(HashID.computeHashID(node), keyHash);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        nextNode = node;
                    }
                }
            }

            if (nextNode == null) {
                return;
            }

            queried.add(nextNode);
            String txid = nextTxID();
            String response = sendRequestToNode(txid + " N " + hashHex, nextNode, getAddressForNode(nextNode));
            if (response != null) {
                handleResponseMessage(response, null, -1);
            }
        }
    }

    private List<String> getCandidateNodesForKey(String key, int limit) throws Exception {
        drainIncomingMessages();
        byte[] keyHash = HashID.computeHashID(key);
        queryNearestNodes(keyHash);
        return getClosestKnownNodes(keyHash, limit);
    }

    private String processRelayMessage(String outerTxid, String rest) throws Exception {
        ParsedString target = parseEncodedString(rest, 0);
        String embedded = rest.substring(target.nextIndex);
        if (embedded.length() < 3) {
            return null;
        }

        String targetNode = target.value;
        if (targetNode.equals(nodeName)) {
            return processMessage(embedded, null, -1);
        }

        String targetAddress = getAddressForNode(targetNode);
        if (targetAddress == null) {
            return null;
        }

        String forwarded = nextTxID() + embedded.substring(2);
        String response = sendRequestToNode(forwarded, targetNode, targetAddress);
        if (response == null || response.length() < 3) {
            return null;
        }

        return outerTxid + response.substring(2);
    }

    private String processMessage(String message, InetAddress senderAddress, int senderPort) throws Exception {
        if (message == null || message.length() < 3) {
            return null;
        }

        String[] parts = message.split(" ", 3);
        if (parts.length < 2) {
            return null;
        }

        String txid = parts[0];
        String type = parts[1];
        if (txid.length() != 2) {
            return null;
        }

        boolean isResponseType = "H".equals(type) || "O".equals(type) || "F".equals(type)
                || "S".equals(type) || "X".equals(type) || "D".equals(type);
        if (isResponseType) {
            handleResponseMessage(message, senderAddress, senderPort);
            return null;
        }

        if ("I".equals(type)) {
            return null;
        }

        if ("G".equals(type)) {
            return txid + " H " + CRNUtils.encodeString(nodeName);
        }

        if ("V".equals(type) && parts.length == 3) {
            return processRelayMessage(txid, parts[2]);
        }

        if ("N".equals(type) && parts.length == 3) {
            byte[] targetHash = hexStringToBytes(parts[2].trim());
            StringBuilder result = new StringBuilder();

            for (String node : getClosestKnownNodes(targetHash, 3)) {
                String address = getAddressForNode(node);
                if (address == null) {
                    continue;
                }
                result.append(CRNUtils.encodeString(node));
                result.append(CRNUtils.encodeString(address));
            }

            return txid + " O " + result;
        }

        if (parts.length != 3) {
            return null;
        }

        if ("E".equals(type)) {
            ParsedString encodedKey = parseEncodedString(parts[2], 0);
            String key = encodedKey.value;

            synchronized (store) {
                if (store.containsKey(key)) {
                    return txid + " F Y ";
                }
            }

            return isOneOfThreeClosest(key) ? txid + " F N " : txid + " F ? ";
        }

        if ("R".equals(type)) {
            ParsedString encodedKey = parseEncodedString(parts[2], 0);
            String key = encodedKey.value;

            synchronized (store) {
                if (store.containsKey(key)) {
                    return txid + " S Y " + CRNUtils.encodeString(store.get(key));
                }
            }

            return isOneOfThreeClosest(key) ? txid + " S N " : txid + " S ? ";
        }

        if ("W".equals(type)) {
            ParsedString encodedKey = parseEncodedString(parts[2], 0);
            ParsedString encodedValue = parseEncodedString(parts[2], encodedKey.nextIndex);
            String key = encodedKey.value;
            String value = encodedValue.value;

            if (isValidNodeName(key) && isValidAddress(value)) {
                boolean replace = getAddressForNode(key) != null;
                learnAddress(key, value);
                return txid + (replace ? " X R" : " X A");
            }

            if (!isValidDataName(key)) {
                return txid + " X X";
            }

            synchronized (store) {
                if (store.containsKey(key)) {
                    store.put(key, value);
                    return txid + " X R";
                }
            }

            if (isOneOfThreeClosest(key)) {
                synchronized (store) {
                    store.put(key, value);
                }
                return txid + " X A";
            }

            return txid + " X X";
        }

        if ("C".equals(type)) {
            ParsedString encodedKey = parseEncodedString(parts[2], 0);
            ParsedString encodedCurrent = parseEncodedString(parts[2], encodedKey.nextIndex);
            ParsedString encodedNew = parseEncodedString(parts[2], encodedCurrent.nextIndex);

            String key = encodedKey.value;
            String currentValue = encodedCurrent.value;
            String newValue = encodedNew.value;

            if (isValidNodeName(key) && isValidAddress(newValue)) {
                String existing = getAddressForNode(key);
                if (existing == null) {
                    learnAddress(key, newValue);
                    return txid + " D A ";
                }
                if (existing.equals(currentValue)) {
                    learnAddress(key, newValue);
                    return txid + " D R ";
                }
                return txid + " D N ";
            }

            synchronized (store) {
                if (store.containsKey(key)) {
                    if (store.get(key).equals(currentValue)) {
                        store.put(key, newValue);
                        return txid + " D R ";
                    }
                    return txid + " D N ";
                }
            }

            if (isOneOfThreeClosest(key)) {
                synchronized (store) {
                    store.put(key, newValue);
                }
                return txid + " D A ";
            }

            return txid + " D X ";
        }

        return null;
    }

    @Override
    public void setNodeName(String nodeName) throws Exception {
        if (!nodeName.startsWith("N:")) {
            nodeName = "N:" + nodeName;
        }

        this.nodeName = nodeName;
        this.nodeHash = HashID.computeHashID(nodeName);
    }

    @Override
    public void openPort(int portNumber) throws Exception {
        socket = new DatagramSocket(portNumber);
        try {
            selfAddress = InetAddress.getLocalHost().getHostAddress() + ":" + portNumber;
        } catch (Exception e) {
            selfAddress = "127.0.0.1:" + portNumber;
        }
    }

    @Override
    public void handleIncomingMessages(int delay) throws Exception {
        if (socket == null) {
            throw new IllegalStateException("Port not opened");
        }

        long endTime = delay > 0 ? System.currentTimeMillis() + delay : Long.MAX_VALUE;

        while (true) {
            int timeout;
            if (delay == 0) {
                timeout = 0;
            } else {
                long remaining = endTime - System.currentTimeMillis();
                if (remaining <= 0) {
                    return;
                }
                timeout = (int) Math.min(HANDLE_POLL_MS, remaining);
            }

            socket.setSoTimeout(timeout);
            byte[] buffer = new byte[65535];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            try {
                socket.receive(packet);
            } catch (java.net.SocketTimeoutException e) {
                if (delay == 0) {
                    continue;
                }
                if (System.currentTimeMillis() >= endTime) {
                    return;
                }
                continue;
            }

            String message = new String(packet.getData(), 0, packet.getLength());
            String response = processMessage(message, packet.getAddress(), packet.getPort());

            if (response != null) {
                byte[] responseBytes = response.getBytes();
                DatagramPacket reply = new DatagramPacket(responseBytes, responseBytes.length,
                        packet.getAddress(), packet.getPort());
                socket.send(reply);
            }
        }
    }

    public String handleMessage(String message) {
        try {
            return processMessage(message, null, -1);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isActive(String nodeName) throws Exception {
        if (!nodeName.startsWith("N:")) {
            nodeName = "N:" + nodeName;
        }

        if (nodeName.equals(this.nodeName)) {
            return true;
        }

        drainIncomingMessages();
        String address = getAddressForNode(nodeName);
        if (address == null) {
            return false;
        }

        String txid = nextTxID();
        String response = sendRequestToNode(txid + " G", nodeName, address);
        if (response == null || !response.startsWith(txid + " H ")) {
            return false;
        }

        ParsedString returnedName = parseEncodedString(response.substring(5), 0);
        return returnedName.value.equals(nodeName);
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
        String request = txid + " E " + CRNUtils.encodeString(key);
        boolean seenClosestNegative = false;

        for (String node : getCandidateNodesForKey(key, 3)) {
            String response = sendRequestToNode(request, node, getAddressForNode(node));
            if (response == null) {
                continue;
            }
            if (response.startsWith(txid + " F Y ")) {
                return true;
            }
            if (response.startsWith(txid + " F N ")) {
                seenClosestNegative = true;
            }
        }

        return false;
    }

    @Override
    public String read(String key) throws Exception {
        String txid = nextTxID();
        String request = txid + " R " + CRNUtils.encodeString(key);
        boolean seenClosestNegative = false;

        for (String node : getCandidateNodesForKey(key, 3)) {
            String response = sendRequestToNode(request, node, getAddressForNode(node));
            if (response == null) {
                continue;
            }
            if (response.startsWith(txid + " S Y ")) {
                ParsedString value = parseEncodedString(response.substring(7), 0);
                return value.value;
            }
            if (response.startsWith(txid + " S N ")) {
                seenClosestNegative = true;
            }
        }

        if (seenClosestNegative) {
            return null;
        }
        return null;
    }

    @Override
    public boolean write(String key, String value) throws Exception {
        String txid = nextTxID();
        String request = txid + " W " + CRNUtils.encodeString(key) + CRNUtils.encodeString(value);
        boolean success = false;

        for (String node : getCandidateNodesForKey(key, 3)) {
            String response = sendRequestToNode(request, node, getAddressForNode(node));
            if (response == null) {
                continue;
            }
            if (response.startsWith(txid + " X A") || response.startsWith(txid + " X R")) {
                success = true;
            }
        }

        return success;
    }

    @Override
    public boolean CAS(String key, String currentValue, String newValue) throws Exception {
        String txid = nextTxID();
        String request = txid + " C " + CRNUtils.encodeString(key)
                + CRNUtils.encodeString(currentValue) + CRNUtils.encodeString(newValue);

        for (String node : getCandidateNodesForKey(key, 3)) {
            String response = sendRequestToNode(request, node, getAddressForNode(node));
            if (response == null) {
                continue;
            }
            if (response.startsWith(txid + " D R ") || response.startsWith(txid + " D A ")) {
                return true;
            }
            if (response.startsWith(txid + " D N ")) {
                return false;
            }
        }

        return false;
    }
}
