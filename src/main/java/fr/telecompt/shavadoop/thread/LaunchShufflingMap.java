package fr.telecompt.shavadoop.thread;

import java.io.InterruptedIOException;

import org.apache.commons.io.FilenameUtils;

import com.jcabi.ssh.SSH;
import com.jcabi.ssh.Shell;

import fr.telecompt.shavadoop.master.SSHManager;
import fr.telecompt.shavadoop.slave.Slave;
import fr.telecompt.shavadoop.util.Constant;

public class LaunchShufflingMap extends ShellThread {

	private String hostMapper;
	private String idWorker;
	
	public LaunchShufflingMap(SSHManager _sm, String _distantHost, String _shufflingDictionaryFile, String _hostMapper, String _idWorker) {
		super(_sm, _distantHost, _shufflingDictionaryFile);
		hostMapper = _hostMapper;
		idWorker = _idWorker;
	}
	
    @Override
    public void interrupt() {
        super.interrupt();
    }

    
	public void run() {
		
        try {

			String pathJar = Constant.PATH_JAR;
			String method = Slave.SHUFFLING_MAP_FUNCTION;
			
			// execute on the master's computer
			if(local) {
				// Run a java app in a separate system process
				String cmd = getCmdJar(pathJar, null, method, fileToTreat, idWorker);
				Process p = Runtime.getRuntime().exec(cmd);
				if (Constant.MODE_DEBUG) System.out.println("On local : " + cmd);
				p.waitFor();
			// execute on a distant computer
			} else {
				//Connect to the distant computer
				shell = new SSH(distantHost, shellPort, Constant.USERNAME, dsaKey);
				
				if (Constant.MODE_SCP_FILES) {
					// MASTER DSM file -> SLAVE
					String destFile = Constant.PATH_SLAVE + FilenameUtils.getBaseName(fileToTreat);
					FileTransfert ft = new FileTransfert(sm, distantHost, fileToTreat, destFile);
					ft.transfertFileScp();
					fileToTreat = destFile;
				}
				
				String cmd = getCmdJar(pathJar, hostMapper, method, fileToTreat, idWorker);
				
				//Launch map process
				String stdout = new Shell.Plain(shell).exec(cmd);
				if (Constant.MODE_DEBUG) System.out.println("On " + distantHost + " : " + cmd);
				if (Constant.MODE_DEBUG) System.out.println("Stdout slave : " + stdout); //TODO
			}
			
        } catch (InterruptedIOException e) { // if thread was interrupted
            Thread.currentThread().interrupt();
            if (Constant.MODE_DEBUG) System.out.println("TASK_TRACKER : worker failed was interrupted");
        } catch (Exception e) {
            if (!isInterrupted()) { // if other exceptions
            	System.out.println("Fail to launch shavadoop slave from " + distantHost);
            } else { 
            	if (Constant.MODE_DEBUG) System.out.println("TASK_TRACKER : worker failed was interrupted");
            }
        }
	}
}