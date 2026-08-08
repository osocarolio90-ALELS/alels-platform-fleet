import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptTool {
    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            throw new IllegalArgumentException("Usage: BcryptTool <password>");
        }
        String password = args[0];
        int strength = 12;
        System.out.println(new BCryptPasswordEncoder(strength).encode(password));
    }
}
