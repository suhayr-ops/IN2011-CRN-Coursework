// 3. String encoding/decoding

public class CRNUtils {

    public static String encodeString(String s) {
        int spaceCount = 0;

        for (char c : s.toCharArray()) {
            if (c == ' ') spaceCount++;
        }

        return spaceCount + " " + s + " ";
    }

    public static String decodeString(String encoded) {
        int firstSpace = encoded.indexOf(' ');
        int spaceCount = Integer.parseInt(encoded.substring(0, firstSpace));

        int index = firstSpace + 1;
        int spacesSeen = 0;

        while (index < encoded.length()) {
            if (encoded.charAt(index) == ' ') {
                if (spacesSeen == spaceCount) {
                    break;
                }
                spacesSeen++;
            }
            index++;
        }

        return encoded.substring(firstSpace + 1, index);
    }
}