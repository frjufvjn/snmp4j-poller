import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class AppMainTimer {

	public static void main(String[] args) {
		new AppMainTimer().executeTimer();
	}

	private void executeTimer() {
		ScheduledJob job = new ScheduledJob();
		Timer jobScheduler = new Timer();
		jobScheduler.scheduleAtFixedRate(job, 1000, 30*1000);
	}
	class ScheduledJob extends TimerTask {

		public void run() {
			// System.out.println(new Date());
			new SnmpPoller().executeCall();
		}
	}
}
