public class ReadTest {
    public static void main(String[] args) throws Exception {

        Node node = new Node();
        node.setNodeName("N:test1");

        node.putLocal("D:test", "hello");

        // EXISTING
        String req1 = "AB R " + CRNUtils.encodeString("D:test");
        System.out.println(node.handleMessage(req1));

        // MISSING
        String req2 = "AB R " + CRNUtils.encodeString("D:missing");
        System.out.println(node.handleMessage(req2));
    }
}