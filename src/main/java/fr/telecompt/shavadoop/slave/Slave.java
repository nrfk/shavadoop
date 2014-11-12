package fr.telecompt.shavadoop.slave;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import fr.telecompt.shavadoop.master.SSHManager;
import fr.telecompt.shavadoop.slave.thread.ShufflingMapThread;
import fr.telecompt.shavadoop.util.Constant;
import fr.telecompt.shavadoop.util.Pair;
import fr.telecompt.shavadoop.util.PropReader;
import fr.telecompt.shavadoop.util.Util;

/**
 * Slave object
 *
 */
public class Slave 
{
	public final static String SPLIT_MAPPING_FUNCTION = "split_mapping_function";
	public final static String SHUFFLING_MAP_FUNCTION = "shuffling_map_function";
	private PropReader prop = new PropReader();
	
	private String functionName;
	private String hostMaster;
	private String fileToTreat;
	private SSHManager sm;
	
	
	public Slave(){}
	
    public Slave(String _hostMaster, String _functionName, String _fileToTreat) {
    	hostMaster = _hostMaster;
    	functionName = _functionName;
    	fileToTreat = _fileToTreat;
    }
	
    public void launchWork() {
    	
    	sm = new SSHManager();
    	sm.initialize();
    	
    	switch (functionName){
    	
	    	case SPLIT_MAPPING_FUNCTION:
	    		//Launch map method
	    		splitMapping(hostMaster, fileToTreat);
	    		break;
	    		
	    	case SHUFFLING_MAP_FUNCTION:
	    		// launch shuffling maps process
	    		int threadMaxByWorker = Integer.parseInt(prop.getPropValues(PropReader.THREAD_MAX_BY_WORKER));
	    		ExecutorService es = Util.fixedThreadPoolWithQueueSize(threadMaxByWorker,100);
	    		try {
	    			InputStream ips = new FileInputStream(fileToTreat); 
	    			InputStreamReader ipsr = new InputStreamReader(ips);
	    			BufferedReader br = new BufferedReader(ipsr);
	    			String shufflingDictionaryLine;
	    			
	    			while((shufflingDictionaryLine = br.readLine()) != null){
	    				String[] elements = shufflingDictionaryLine.split(Constant.SEP_CONTAINS_FILE);
	    				String key = elements[0];
	    				String filesToShuffling = elements[1];
	    				es.execute(new ShufflingMapThread(sm, this, key, filesToShuffling));
	    			}
	    			
	    			br.close();
	    			ipsr.close();
	    			br.close();
	    		} catch (IOException e) {
	    			System.out.println("No shuffling dictionary file");
	    		}
	    		
	    		es.shutdown();
	    		
	    		try {
	    			es.awaitTermination(Integer.parseInt(prop.getPropValues(PropReader.THREAD_LIFETIME)), TimeUnit.MINUTES);
	    		} catch (InterruptedException e) {
	    			e.printStackTrace();
	    		}
	    		break;
	    	default:
	    		System.out.println("Function name unknown");
	    		break;
    	}
    }
    
    
    /**	
     * Map method
     * @param fileToMap
     */
    public void splitMapping(String hostMaster, String fileToMap) {
		 try {
             FileReader fic = new FileReader(fileToMap);
             BufferedReader read = new BufferedReader(fic);
             String line = null;
             
             List<Pair> unsortedMaps = null;
            		 
             while ((line = read.readLine()) != null) {
            	 unsortedMaps = wordCount(unsortedMaps, line);
             }
             
             fic.close();
             read.close();   
    		 
             // Write UM File
        	 String fileToShuffle = Constant.PATH_F_MAPPING 
        			 + Constant.SEP_NAME_FILE 
        			 + sm.getHostFull();
        	 
        	 Util.writeFileFromPair(fileToShuffle, unsortedMaps);
        	 
        	 // Send dictionary with UNIQUE key (word) and hostname to the master
        	 sendDictionaryElement(hostMaster, unsortedMaps, fileToShuffle);
        	 
         } catch (IOException e) {
             e.printStackTrace();
         }
    }
    
    /**
     * Count the occurence of each word in the sentence
     * @param line
     * @return
     */
    public List<Pair> wordCount(List<Pair> mapWc, String line) {
    	if (mapWc == null) {
    		mapWc = new ArrayList<Pair>();
    	}
    	//We split the line word by word
    	String words[] = line.split(Constant.SEP_WORD);
    	
    	for (int i = 0; i < words.length; i++) {
    		String word = words[i];
    		//Increment counter value for this word
    		mapWc.add(new Pair(word, String.valueOf(1)));
    	}
    	
    	return mapWc;
    }
    
    /**
     * Send key (word) - value (name of the file content), to the master
     * @param unsortedMaps
     * @param fileToShuffle
     * @throws IOException 
     * @throws UnknownHostException 
     */
    private void sendDictionaryElement(String hostMaster, List<Pair> unsortedMaps, String fileToShuffle) throws UnknownHostException, IOException {
    	//Get host master and port
		int portMaster = Integer.parseInt(prop.getPropValues(PropReader.PORT_MASTER));
		
        Socket socket = new Socket(hostMaster, portMaster);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
        
        Map<String, Pair> partDictionary = new HashMap<String, Pair>();
        for (Pair p : unsortedMaps) {
        	// Send dictionary element
        	partDictionary.put(p.getVal1(), new Pair(sm.getHostFull(), fileToShuffle));
        }
        
        // Send dictionary element
        out.writeObject(partDictionary);
        out.flush();
        out.close();
        socket.close();
    }
    

    /**
     * Group and sort maps results by key
     * @param key
     * @param shufflingDictionaryLine
     * @return
     */
    public String shufflingMaps(String key, String[] listFiles) {
    	// Final file to reduce
    	String fileToReduce = null;
    	
    	// Concat data of each files in one
		 try {
             List<Pair> sortedMaps = new ArrayList<Pair>();
             
             // For each files
			 for (int i = 0; i < listFiles.length; i++) {
				 
	             FileReader fic = new FileReader(listFiles[i]);
	             BufferedReader read = new BufferedReader(fic);
	             String line = null;
	
	             // For each lines of the file
	             while ((line = read.readLine()) != null) {
		            String words[] = line.split(Constant.SEP_CONTAINS_FILE);
		            // Search line refers to this key
		            if (words[0].equals(key)) {
			            // Add each line matched with the key to our hashmap
		            	sortedMaps.add(new Pair(words[0], words[1]));
		            }
	             } 
	        	 
	             fic.close();
	             read.close();   
			 }
			 
        	 fileToReduce = Constant.PATH_F_SHUFFLING 
        			 + Constant.SEP_NAME_FILE 
        			 + key
        			 + Constant.SEP_NAME_FILE 
        			 + sm.getHostFull();
        	 Util.writeFileFromPair(fileToReduce, sortedMaps);
         	
         } catch (IOException e) {
             e.printStackTrace();
         }
    	return fileToReduce;
    	
    }
    
    
    /**
     * Reduce method
     * @param fileToReduce
     */
    public void mappingSortedMaps(String key, String fileToReduce) {
		 try {
             FileReader fic = new FileReader(fileToReduce);
             BufferedReader read = new BufferedReader(fic);
             String line = null;

             Map<String, Integer> finalMaps = new HashMap<String, Integer>();
             
             while ((line = read.readLine()) != null) {
            	String words[] = line.split(Constant.SEP_CONTAINS_FILE);
         		//Increment counter value for this word
         		if (finalMaps.containsKey(words[0])) {
         			finalMaps.put(words[0], finalMaps.get(words[0]) + Integer.parseInt(words[1]));
         		} else {
         			finalMaps.put(words[0], Integer.parseInt(words[1]));
         		}
             }
            
        	 String fileToAssemble = Constant.PATH_F_REDUCING 
        			 + Constant.SEP_NAME_FILE
        			 + key
        			 + Constant.SEP_NAME_FILE 
        			 + sm.getHostFull();
        	 Util.writeFile(fileToAssemble, finalMaps);
        	 
             fic.close();
             read.close();   
    		 
         } catch (IOException e) {
             e.printStackTrace();
         }
    }
}
