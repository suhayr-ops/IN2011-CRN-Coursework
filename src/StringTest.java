public class StringTest {
    public static void main(String[] args) {

        System.out.println("=== STRING TEST ===");

        String original = "Hello World";

        String encoded = CRNUtils.encodeString(original);
        String decoded = CRNUtils.decodeString(encoded);

        System.out.println("Original: " + original);
        System.out.println("Encoded : " + encoded);
        System.out.println("Decoded : " + decoded);

        // Extra tests (important)
        System.out.println("\n=== EDGE CASES ===");

        String empty = "";
        System.out.println("Empty encoded: " + CRNUtils.encodeString(empty));
        System.out.println("Empty decoded: " + CRNUtils.decodeString(CRNUtils.encodeString(empty)));

        String spaces = " ";
        System.out.println("Space encoded: " + CRNUtils.encodeString(spaces));
        System.out.println("Space decoded: [" + CRNUtils.decodeString(CRNUtils.encodeString(spaces)) + "]");
    }
}