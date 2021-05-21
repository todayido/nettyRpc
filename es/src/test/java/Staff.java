import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Staff {
    private String name;
    private int age;
    private String extra;
}
