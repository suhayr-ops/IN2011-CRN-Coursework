// IN2011 Computer Networks
// 1/2. HashID utilities (SHA-256, hex conversion, distance)

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class HashID {

	// Compute SHA-256 hash of a string
	public static byte[] computeHashID(String s) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update(s.getBytes(StandardCharsets.UTF_8));
		return md.digest();
	}

	// Convert byte array to 64-character hex string
	public static String bytesToHex(byte[] hash) {
		StringBuilder hex = new StringBuilder();
		for (byte b : hash) {
			String s = Integer.toHexString(0xff & b);
			if (s.length() == 1) hex.append('0');
			hex.append(s);
		}
		return hex.toString();
	}

	// Compute distance between two hashes
	// Distance = 256 - number of matching leading bits
	public static int distance(byte[] h1, byte[] h2) {
		int matchingBits = 0;

		for (int i = 0; i < h1.length; i++) {
			int b1 = h1[i] & 0xFF;
			int b2 = h2[i] & 0xFF;

			if (b1 == b2) {
				matchingBits += 8;
			} else {
				int xor = b1 ^ b2;

				for (int j = 7; j >= 0; j--) {
					if (((xor >> j) & 1) == 0) {
						matchingBits++;
					} else {
						return 256 - matchingBits;
					}
				}
			}
		}

		return 0; // identical hashes
	}
}