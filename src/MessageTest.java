public class MessageTest {
    public static void main(String[] args) throws Exception {

        Node node = new Node();
        node.setNodeName("N:test1");

        String request = "AB G ";
        String response = node.handleMessage(request);

        System.out.println("Request : " + request);
        System.out.println("Response: " + response);
    }
}