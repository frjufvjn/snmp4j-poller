import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.ScopedPDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.UserTarget;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.MessageProcessingModel;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.AuthMD5;
import org.snmp4j.security.PrivAES128;
import org.snmp4j.security.SecurityLevel;
import org.snmp4j.security.SecurityModels;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.security.UsmUser;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.VariableBinding;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.PDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeListener;
import org.snmp4j.util.TreeUtils;

public class SnmpWorker implements Callable {

	/**
	 * @description OID Information (GETBULK, SUBTREE 방식)
	 * 	Default 
	 * 		- .1.3.6.1.2.1.25.2.3.1 (Disk-Usage, Memory, Swap)
	 * 		- .1.3.6.1.2.1.25.3.3.1.2 (CPU LOAD)
	 * 	Addtional
	 * 		- 프로세스 모니터링
	 * 			- ".1.3.6.1.2.1.25.4.2.1.2" // (hrSWRunName)
	 * 			- ".1.3.6.1.2.1.25.5.1.1.2" // (hrSWRunPerfMem)
	 * 		- 인터페이스 (네트워크사용량)
	 * */
	private final static String[] oids = {
			".1.3.6.1.2.1.25.2.3.1", 	// include <-- hrStorageUsed & hrStorageSize & hrStorageDescr
			".1.3.6.1.2.1.25.4.2.1.2", 	// (hrSWRunName)
			".1.3.6.1.2.1.25.5.1.1.2", 	// (hrSWRunPerfMem)
			".1.3.6.1.2.1.25.3.3.1.2" 	// (hrProcessorLoad)
	};



	private final static String[] extractUsageOidKeys = {".1.3.6.1.2.1.25.2.3.1.3",".1.3.6.1.2.1.25.2.3.1.5", ".1.3.6.1.2.1.25.2.3.1.6"};
	private final static String extractUsageTypeOidKey = ".1.3.6.1.2.1.25.2.3.1.3";
	private final static String extractCpuLoadOidKey = ".1.3.6.1.2.1.25.3.3.1.2";

	private final static String[] diskBlackCaseType = {"PHYSICAL MEMORY","VIRTUAL MEMORY","MEMORY BUFFERS","CACHED MEMORY","SWAP SPACE","REAL MEMORY"};
	private final static String[] memoryType = {"PHYSICAL MEMORY", "MEMORY BUFFERS", "REAL MEMORY"};
	private final static String[] swapType = {"VIRTUAL MEMORY","SWAP SPACE"};

	private final static List<String> diskBlackCaseList = new ArrayList<>(Arrays.asList(diskBlackCaseType));
	private final static List<String> memList = new ArrayList<>(Arrays.asList(memoryType));
	private final static List<String> swapList = new ArrayList<>(Arrays.asList(swapType));

	private static int snmpRetries = 1;
	private static long snmpTimeout = 7000;
	private static int maxRepetitions = 100;
	private static long threadTimeoutMills = 8000;

	/**
	 * SNMPv3 auth protocol
	 * */
	private final OID authProtocol = AuthMD5.ID;

	/**
	 * SNMPv3 priv protocol
	 * */
	private final OID privProtocol = PrivAES128.ID;

	private Map<String,Object> hm;



	// Constructor
	public SnmpWorker(Map<String,Object> hm) {
		this.hm = hm;
	}

	@Override
	public Boolean call() throws Exception {
		return snmpWalk(this.hm);
	}

	private boolean snmpWalk(final Map<String,Object> hm) {

		try {

			final String deviceId = hm.get("deviceid").toString();
			final String ipaddress = hm.get("ip").toString();
			final String community = hm.get("community").toString();
			final String version = hm.get("version").toString();
			final String passwd = hm.get("password").toString();

			final Snmp snmp = new Snmp(new DefaultUdpTransportMapping());
			snmp.listen();

			final Target target = getTarget(ipaddress, community, version, passwd);

			CompletableFuture<Map<String,String>> completableFuture1 = CompletableFuture.supplyAsync(() -> sendAsyncRequest(oids[0], target, deviceId, snmp, ipaddress, version));
			CompletableFuture<Map<String,String>> completableFuture2 = CompletableFuture.supplyAsync(() -> sendAsyncRequest(oids[1], target, deviceId, snmp, ipaddress, version));
			CompletableFuture<Map<String,String>> completableFuture3 = CompletableFuture.supplyAsync(() -> sendAsyncRequest(oids[2], target, deviceId, snmp, ipaddress, version));
			CompletableFuture<Map<String,String>> completableFuture4 = CompletableFuture.supplyAsync(() -> sendAsyncRequest(oids[3], target, deviceId, snmp, ipaddress, version));

			List<CompletableFuture<Map<String,String>>> futures = Arrays.asList(completableFuture1,
					completableFuture2,
					completableFuture3,
					completableFuture4);

			CompletableFuture.allOf(completableFuture1, completableFuture2, completableFuture3, completableFuture4)
			.thenAccept(s -> {
				List<Map<String,String>> result = futures.stream()
						.map(pageContentFuture -> pageContentFuture.join())
						.collect(Collectors.toList());
				System.out.println("  # " + hm.get("deviceid") + " final result size : " + result.size());
				
				Map<String,Object> calc = new HashMap<String,Object>();
				Map<String,Object> calcDisk = new HashMap<String,Object>();
				calc.put("ip", ipaddress);
				calc.put("id", deviceId);


				/**
				 * @description Usage (DiskUsage, Memory, Swap)
				 * */
				@SuppressWarnings("unchecked")
				Map<String,String> resUsage = result.get(0);
				if (resUsage != null) {
					Map<String,Double> tmpValue = new HashMap<String,Double>();

					List<String> subOidEntry = Stream.of(resUsage.keySet().toArray())
							.filter(item -> item.toString().contains(extractUsageTypeOidKey) )
							.map(e -> getSubOid(e.toString()) )
							.collect(Collectors.toList());

					subOidEntry.stream().map(item -> Stream.of(extractUsageOidKeys)
							.map(m -> m.concat("." + item))
							.collect(Collectors.toList()))
					.forEach(l -> {
						String storageType = resUsage.get(l.get(0)); // .toUpperCase();
						Double tot = Double.parseDouble( resUsage.get(l.get(1)) );
						Double use = Double.parseDouble( resUsage.get(l.get(2)) );
						tmpValue.put(storageType, (use/tot)*100);
					});


					/**
					 * @description Get Disk Usage Value
					 * */
					Stream.of(tmpValue.keySet().toArray())
					.filter(item -> !diskBlackCaseList.contains(item.toString().toUpperCase()))
					.forEach(key -> {
						calcDisk.put("DISK@" + getDecodeStr(key.toString()), tmpValue.get(key));
					});


					/**
					 * @description Get Memory Usage Value
					 * */
					Double memVal = Stream.of(tmpValue.keySet().toArray())
							.filter(item -> memList.contains(item.toString().toUpperCase()))
							.mapToDouble(tmpValue::get)
							.average()
							.orElseThrow(NoSuchElementException::new)
							;

					calc.put("MEMORY", memVal);

					/**
					 * @description Get Swap Usage Value
					 * */
					Double swapVal = Stream.of(tmpValue.keySet().toArray())
							.filter(item -> swapList.contains(item.toString().toUpperCase()))
							.mapToDouble(tmpValue::get)
							.average()
							.orElseThrow(NoSuchElementException::new)
							;

					calc.put("SWAP", swapVal);
				}




				/**
				 * @description CPU Load
				 * */
				@SuppressWarnings("unchecked")
				Map<String,String> resCpuLoad = result.get(3);
				if ( resCpuLoad != null ) {
					long cpuCore = Stream.of(resCpuLoad.keySet().toArray())
							.filter(item -> item.toString().contains(extractCpuLoadOidKey) )
							.count();

					Optional<Double> sum = Stream.of(resCpuLoad.keySet().toArray())
							.filter(item -> item.toString().contains(extractCpuLoadOidKey) )
							.map(resCpuLoad::get)
							.map(Double::parseDouble)
							.reduce(Double::sum);

					calc.put("CPULOAD", sum.get()/cpuCore);
				}


				/**
				 * @description Process List
				 * */
				Map<String,String> resProcess = result.get(1);
				Map<String,String> resProcessMem = result.get(2);

				Map<String,Object> calcProc = new HashMap<String,Object>();
				calcProc.put("id", deviceId);
				calcProc.put("ip", ipaddress);

				if ( resProcess != null && resProcessMem != null ) {
					List<Map<String,Object>> resProcList = new ArrayList<Map<String,Object>>();
					resProcess.keySet().forEach(item -> {
						Map<String,Object> proc = new HashMap<String,Object>();
						proc.put(resProcess.get(item), resProcessMem.get(".1.3.6.1.2.1.25.5.1.1.2." + getSubOid(item)));
						resProcList.add(proc);
					});
					calcProc.put("data", resProcList);
				}
				
				System.out.println("    >> " + calc.toString());
				System.out.println("    >> " + calcProc.toString());
				System.out.println(deviceId + " is end.....................................................");
				
				
				calc.clear();
				calcDisk.clear();
				calcProc.clear();

				try {
					snmp.close();
				} catch (IOException e) {
					System.err.println("snmp close exception : " + e);
				}
			});

		} catch (Exception e) {
			return false;
		}

		return true;
	}



	/**
	 * @description Send Asynchronously SNMP GET-SUBTREE
	 * @param tableOid
	 * @param target
	 * @param idx
	 * @param snmp
	 * @param fut
	 * @param ipaddress
	 */
	private Map<String,String> sendAsyncRequest(String tableOid, Target target, String idx, Snmp snmp, String ipaddress, String version) {

		Map<String, String> result = new TreeMap<>();

		TreeUtils treeUtils = null;
		if ("v3".equals(version)) {
			treeUtils = new TreeUtils(snmp, new SysmonPduFactory());
		} else {
			treeUtils = new TreeUtils(snmp, new DefaultPDUFactory(PDU.GETBULK));
		}

		treeUtils.setMaxRepetitions(maxRepetitions); // default is 10

		long start = System.currentTimeMillis();
		final CountDownLatch latch = new CountDownLatch(1);

		treeUtils.getSubtree(target, new OID(tableOid), null, new TreeListener() {

			@Override
			public boolean next(TreeEvent event) {
				if (!resultProcess(result, event, ipaddress, tableOid)) {
					// jw fut.complete();
					return false;
				}
				return true;
			}

			@Override
			public boolean isFinished() {
				return false;
			}

			@Override
			public void finished(TreeEvent event) {
				if (!resultProcess(result, event, ipaddress, tableOid)) {
					// jw fut.complete();
				} else {
					latch.countDown();
					// jw fut.complete(result);
				}
			}
		});

		try {
			boolean wait = latch.await(threadTimeoutMills, TimeUnit.MILLISECONDS);
			if (wait) {
				System.out.println("end occursNo:"+idx+", ipaddress:"+ipaddress+", oid:"+tableOid+
						", CountDownLatch Wait : "+wait+", elapsed: "+(System.currentTimeMillis() - start)+"ms");
			} else {
				System.err.println("end occursNo:"+idx+", ipaddress:"+ipaddress+", CountDownLatch Wait : " + wait);
			}
		} catch (InterruptedException e) {
			System.err.println("snmp InterruptedException (try to interrupt!!) " +  e);
			Thread.currentThread().interrupt();
		}

		return result;
	}


	/**
	 * @description result method
	 * @param result
	 * @param event
	 * @return
	 */
	private boolean resultProcess(Map<String, String> result, TreeEvent event, String ipAddr, String tableOid) {
		if ( event == null ) {
			System.err.println("event is null, ip:{}, oid:{}" +  ipAddr + ", " +  tableOid);
			return false;
		} else {
			if ( event.isError() ) {
				System.err.println("err:{}, ip:{}, oid:{}" + event.getErrorMessage() + ", " + ipAddr + ", " + tableOid);
				return false;
			} else {
				VariableBinding[] varBindings = event.getVariableBindings();
				if (varBindings == null || varBindings.length == 0) {
					System.err.println("varBinding is null, ip:{}, oid:{}" + ipAddr + ", " + tableOid);
					return false;
				} else {
					for (VariableBinding varBinding : varBindings) {
						if (varBinding == null) {
							continue;
						}

						// System.out.println("ip : " + ipAddr + " oid: " + varBinding.getOid().toString() + " value: " + varBinding.getVariable().toString());
						result.put("." + varBinding.getOid().toString(), varBinding.getVariable().toString());
					}
					return true;
				}
			}
		}
	}



	private Target getTarget(final String ipaddress, final String community, final String version,
			final String passwd) {
		Target target;
		if ( "v3".equals(version) ) {
			USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
			usm.addUser(new OctetString(community), new UsmUser(new OctetString(community), this.authProtocol,
					new OctetString(passwd), this.privProtocol, new OctetString(passwd)));
			SecurityModels.getInstance().addSecurityModel(usm);

			target = createCommunity(ipaddress, community, passwd);
		} else {
			target = createCommunity(ipaddress, community);
		}
		return target;
	}


	/**
	 * @description Create SNMP Community Target
	 * @param ip
	 * @param community
	 * @return {CommunityTarget}
	 */
	private CommunityTarget createCommunity(String ip, String community) {
		CommunityTarget target = new CommunityTarget();
		target.setCommunity(new OctetString(community));
		target.setAddress(GenericAddress.parse("udp:"+ip+"/161")); // supply your own IP and port
		target.setRetries(snmpRetries);
		target.setTimeout(snmpTimeout);
		target.setVersion(SnmpConstants.version2c);
		return target;
	}

	/**
	 * @description Create SNMP User Target For SNMPv3
	 * @param ip
	 * @param community
	 * @param passwd
	 * @return {UserTarget}
	 */
	private UserTarget createCommunity(String ip, String community, String passwd) {
		UserTarget target = new UserTarget();
		target.setAddress(GenericAddress.parse("udp:"+ip+"/161")); // supply your own IP and port
		target.setRetries(snmpRetries);
		target.setTimeout(snmpTimeout);
		target.setVersion(SnmpConstants.version3);
		target.setSecurityLevel(SecurityLevel.AUTH_PRIV);
		target.setSecurityName(new OctetString(community));

		return target;
	}

	/**
	 * @description Create PDU For SNMPv3
	 * @author user
	 *	
	 */
	private final class SysmonPduFactory implements PDUFactory {
		@Override
		public PDU createPDU(Target target) {
			return getPDU();
		}

		@Override
		public PDU createPDU(MessageProcessingModel messageProcessingModel) {
			return getPDU();
		}
	}

	/**
	 * @description create the PDU For SNMPv3
	 * @return {PDU}
	 */
	private PDU getPDU() {
		PDU pdu = new ScopedPDU();
		// pdu.add(new VariableBinding(SnmpConstants.sysDescr ));
		pdu.setType(PDU.GETBULK);
		pdu.setMaxRepetitions(maxRepetitions); //or any number you wish
		return pdu;
	}

	/**
	 * @description get sub oid
	 * @param str
	 * @return {String}
	 */
	private String getSubOid(String str) {
		String[] strArr = str.split("\\.");
		return strArr[strArr.length-1];
	}

	private String getDecodeStr(String org) {
		try {
			if ( org.split(":").length >= 5 ) {
				return URLDecoder.decode("%"+org.replace(":", "%"),"euc-kr");
			} else {
				return org;
			}
		} catch (UnsupportedEncodingException e) {
			return org;
		}
	}
}

