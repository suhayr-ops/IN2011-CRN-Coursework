public class ExistsTest {
    public static void main(String[] args) throws Exception {

        Node node = new Node();
        node.setNodeName("N:test1");

        // Add data properly
        node.putLocal("D:test", "hello");

        String req1 = "AB E " + CRNUtils.encodeString("D:test");
        System.out.println(node.handleMessage(req1));

        String req2 = "AB E " + CRNUtils.encodeString("D:missing");
        System.out.println(node.handleMessage(req2));
    }
}