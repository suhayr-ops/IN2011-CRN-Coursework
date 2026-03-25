public class HashTest {
    public static void main(String[] args) throws Exception {

        System.out.println("=== HASH TEST ===");

        byte[] h1 = HashID.computeHashID("N:test1");
        byte[] h2 = HashID.computeHashID("N:test2");

        String hex1 = HashID.bytesToHex(h1);
        String hex2 = HashID.bytesToHex(h2);

        int dist = HashID.distance(h1, h2);

        System.out.println("Hash 1: " + hex1);
        System.out.println("Hash 2: " + hex2);
        System.out.println("Distance: " + dist);

        // Extra sanity checks
        System.out.println("\n=== SANITY CHECKS ===");

        byte[] h3 = HashID.computeHashID("N:test1");
        System.out.println("Same input same hash: " +
                hex1.equals(HashID.bytesToHex(h3)));

        System.out.println("Distance to itself: " +
                HashID.distance(h1, h1));
    }
}