import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MultiThreadTest2 {

	public static void main(String[] args) {
		new MultiThreadTest2().tester();
	}
	
	private void tester() {
		ExecutorService es = Executors.newFixedThreadPool(10);
		
		for (int i=0; i<50; i++) {
			es.execute(new IOTask());
		}
		
		es.shutdown();
		System.out.println("INVOKE END-------------");
	}
	
	class IOTask implements Runnable {
		public void run() {
			try {
				Thread.sleep(1000);
				System.out.println(Thread.currentThread().getName() + " IOTask.... " + System.currentTimeMillis());
			} catch (InterruptedException e) {
				e.printStackTrace();
				Thread.currentThread().interrupt();
			}
		};
	}
}
