import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class FutureTaskTest {
    public static void main(String[] args) {
        long start = System.currentTimeMillis();

        FutureTask futureTask1 = new FutureTask(()->{
            Thread.sleep(1000);
            System.out.println(Thread.currentThread());
            return " hello ";
        });

        new Thread(futureTask1).start();

        FutureTask futureTask2 = new FutureTask(()->{
            Thread.sleep(3000);
            System.out.println(Thread.currentThread());
            return " word ";
        });

        new Thread(futureTask2).start();

        try {
            System.out.println((String) futureTask1.get() + futureTask2.get());
            long end = System.currentTimeMillis();
            System.out.println("cost："+(end-start));
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }


    }
}
