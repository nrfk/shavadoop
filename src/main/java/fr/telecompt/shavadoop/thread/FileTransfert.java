package fr.telecompt.shavadoop.thread;

import fr.telecompt.shavadoop.master.SSHManager;
import fr.telecompt.shavadoop.util.Constant;


public class FileTransfert extends ShellThread {
	
	private String destFile;
	
	public FileTransfert(SSHManager _sm, String _HostOwner, String _fileToTreat, String _destFile) {
		super(_sm, _HostOwner, _fileToTreat);
		destFile = _destFile;
	}
	
	public void run() {
		transfertFileScp();
	}
	
	public void transfertFileScp() {
		String cmd = "scp " + fileToTreat + " " + username + "@" + sm.getHostFull() + ":" + destFile;
		try {
			// Run a java app in a separate system process
			Process p = Runtime.getRuntime().exec(cmd);
			if (Constant.MODE_DEBUG) System.out.println("On local : " + cmd);
			p.waitFor();
		} catch (Exception e) {
			System.out.println("Error on local : " + cmd);
			e.printStackTrace();
		}
	}
	
}