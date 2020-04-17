import org.snmp4j.mp.MPv3;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.security.USM;
import org.snmp4j.smi.OctetString;

public class USMFactory {
	private USM usm= null;
	private static USMFactory usmF = null ;

	public static USMFactory getInstance(){
		if(usmF == null ) usmF = new USMFactory();
		return usmF;
	}

	public USMFactory(){
		usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
	}

	public USM getUSM(){
		return usm;
	}
}
