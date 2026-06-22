import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptTool {
    public static void main(String[] args) {
        String password = args.length > 0 ? args[0] : "alels1234567";
        int strength = 12;
        System.out.println(new BCryptPasswordEncoder(strength).encode(password));
    }
}
