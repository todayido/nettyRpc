import java.io.File;
import java.util.concurrent.TimeUnit;

public class AgentTest {
    public static void main(String[] args) {
        System.out.println("main start");

        try {
            File file = new File("");
            file.lastModified();

            TimeUnit.MINUTES.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("main end");
    }
}
