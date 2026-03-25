public class NearestTest {
    public static void main(String[] args) throws Exception {

        Node node = new Node();
        node.setNodeName("N:test1");

        String fakeHash = HashID.bytesToHex(HashID.computeHashID("D:test"));

        String request = "AB N " + fakeHash;
        String response = node.handleMessage(request);

        System.out.println("Request : " + request);
        System.out.println("Response: " + response);
    }
}