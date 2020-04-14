import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class SnmpPoller implements Job {

	private final static int threadPoolSize = 10;

	@Override
	public void execute(JobExecutionContext arg0) throws JobExecutionException {
		System.out.println("Job Executed [" + new Date(System.currentTimeMillis()) + "]"
				+ "--------------------------------------------------------------------------------"); 

		List<Map<String,Object>> servers = getServersFromConfig();

		for (Map<String, Object> map : servers) {
			snmpPoller(map);
		}
	}

	private void snmpPoller(final Map<String,Object> map) {
		SnmpWorker sw = new SnmpWorker(map);
		ExecutorService es = Executors.newFixedThreadPool(threadPoolSize);

		@SuppressWarnings("unchecked")
		FutureTask<Boolean> future = new FutureTask<Boolean>(sw) {
			@Override
			protected void done() {
				try {
					boolean callSuccess = ((Boolean) get()).booleanValue();
					// System.out.println("future task callSuccess : "+ callSuccess + ", deviceid : " + map.get("deviceid"));
				} catch (InterruptedException e) {
					System.out.println("[InterruptedException] " + e.getMessage());
					Thread.currentThread().interrupt();
				} catch (ExecutionException e) {
					System.out.println("[ExecutionException] " + e.getMessage());
				}
			}
		};

		es.execute(future);
		es.shutdown();
	}

	private List<Map<String,Object>> getServersFromConfig() {
		List<Map<String,Object>> servers = new ArrayList<Map<String,Object>>();

		final String appHome = System.getProperty("user.dir");
		final String configFile = String.join(File.separator, appHome, "config", "servers.txt");
		File file = new File(configFile); 
		if(file.exists()) {
			try (BufferedReader inFile = new BufferedReader(new FileReader(file)); ) {
				String sLine = null; 
				while( (sLine = inFile.readLine()) != null ) {
					// System.out.println(sLine);
					if ( sLine.startsWith("#") ) continue;
					String[] arr = sLine.split("\\,");
					Map<String,Object> hm = new HashMap<>();
					hm.put("deviceid", arr[0]);
					hm.put("ip", arr[1]);
					hm.put("community", arr[2]);
					hm.put("version", arr[3]);
					hm.put("password", arr[4]);
					servers.add(hm);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return servers;
	}


}
