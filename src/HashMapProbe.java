import java.util.HashMap;
import java.util.Map;

/**
 * Small probe for stepping into JDK 8 class library sources (e.g. HashMap.put).
 */
public class HashMapProbe {
    public static void main(String[] args) {
        Map<String, String> map = new HashMap<>();
        map.put("key", "value");
        System.out.println(map.get("key"));
    }
}
