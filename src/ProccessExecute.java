import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ProccessExecute {
	
	private static final String path = "C:/go-work/log-error-detector/snmpWalkExample.exe";
	
	public static void main(String[] args) {
		new ProccessExecute().runCommand();
	}
	
	private void runCommand() {
		List<String> arguments = new ArrayList<String>();
		
		arguments.add("C:/go-work/log-error-detector/snmpWalkExample.exe");
        arguments.add("-i");
        
        System.out.println("ffmpeg arguments: {}"+ arguments);
        
        ProcessBuilder processBuilder = new ProcessBuilder(arguments);
        processBuilder.redirectErrorStream(true);
        Process process = null;
        IOException errorDuringExecution = null;
        BufferedReader reader = null;
        try {
            process = processBuilder.start();
            process.waitFor();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            if (process.exitValue() != 0) {
                while ((line = reader.readLine()) != null) {
                	System.out.println("[process.exitValue != 0 ] " + line);
                }
            }
            else {
                while ((line = reader.readLine()) != null) {
                	System.out.println("[process.exitValue == 0 ] " + line);
                }
            }


        } catch (IOException e) {
            errorDuringExecution = e;
            System.err.println("Error while running ffmpeg. IOException \n"+ e.getMessage());
        } catch (InterruptedException e) {
        	System.err.println("Error while running ffmpeg. InterruptedException \n"+ e.getMessage());
        } finally {
            arguments.clear();
            if (process != null) {
            	System.out.println("process destroy.....");
                process.destroy();
                process = null;
            }
            if(reader != null) {
            	try {
            		reader.close();
            	} catch (IOException e) {
            		System.err.println("Error while reader close. IOException \n"+ e.getMessage());
            	}
            }
            if (errorDuringExecution != null) {
                System.err.println(errorDuringExecution.getMessage());
            }
        }
	}
}
