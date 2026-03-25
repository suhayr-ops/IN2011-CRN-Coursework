public class WriteTest {
    public static void main(String[] args) throws Exception {

        Node node = new Node();
        node.setNodeName("N:test1");

        String key = CRNUtils.encodeString("D:test");
        String value = CRNUtils.encodeString("hello");

        // First write
        String req1 = "AB W " + key + value;
        System.out.println(node.handleMessage(req1));

        // Second write (replace)
        String req2 = "AB W " + key + CRNUtils.encodeString("newvalue");
        System.out.println(node.handleMessage(req2));

        // Read back
        String req3 = "AB R " + key;
        System.out.println(node.handleMessage(req3));
    }
}
