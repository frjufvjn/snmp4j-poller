import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class MultiThreadTest {

	private static final int threadPoolSize = 10;
	private static final int occursCount = 30;

	public static void main(String[] args) {
		new MultiThreadTest().multiPoller();

	}

	private void multiPoller() {
		ExecutorService es = Executors.newFixedThreadPool(threadPoolSize);

		List<Callable<String>> callableTasks = new ArrayList<>();
		for (int i=0; i<occursCount; i++) {
			final int idx = i;
			callableTasks.add(() -> {
				return someBlokingJob("called : " + idx);
			});
		}

		try {
			List<Future<String>> futures = es.invokeAll(callableTasks);
			es.shutdown();

			futures.stream().forEach(item -> {
				try {
					System.out.println("final result : " + item.get());
				} catch (InterruptedException e) {
					e.printStackTrace();
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					e.printStackTrace();
				}
			});

			if (!es.awaitTermination(4000, TimeUnit.MILLISECONDS)) {
				System.out.println("### NO AWAIT !!");
				es.shutdownNow();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
			es.shutdownNow();
		}
	}

	private String someBlokingJob(String arg) {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println(arg);
		return "called " + arg;
	}

}
