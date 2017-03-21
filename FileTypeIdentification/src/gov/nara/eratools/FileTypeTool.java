package gov.nara.eratools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import gov.nara.oif.api.ERAFactory;
import gov.nara.oif.api.ERALogger;
import gov.nara.oif.api.ERATool;
import gov.nara.oif.api.ERAObject;
import gov.nara.oif.api.ERAProgress;

import gov.nara.oif.api.exception.ERAException;
import gov.nara.oif.api.metadata.ERAMetadataField;
import gov.nara.oif.api.metadata.ERASelectMetadata;
import gov.nara.oif.api.metadata.ERAStringMetadata;

//import org.apache.commons.lang3.SystemUtils;

public class FileTypeTool implements ERATool {
	private ERAProgress progressBar;
	private ERALogger logger;
	private ERAFactory factory;
	//private OutputLogger outputLogger;
	
	private static final String TOOL_TIMESTAMP = "Timestamp";
	private static final String OBJECT_FILENAME = "Filename";
	private static final String FILE_FORMAT = "FileIdentificationFormatName";
	private static final String AGENT_NAME = "SoftwareAgentName";
	private static final String AGENT_VERSION = "SoftwareAgentVersion";
	private static final String CONFIDENCE = "FileIdentificationFormatConfidence";
	
	private static final String AGENT_NAME_JSON = "\"" + AGENT_NAME + "\": \"file\"";
	private static final String CONFIDENCE_JSON = "\"" + CONFIDENCE + "\": \"POSITIVE\"";
	private static final String AGENT_VERSION_JSON_PREFIX = "\"" + AGENT_VERSION + "\":";
	
	
	@Override
	public void execute(ERAFactory factory) {
		this.factory = factory;
		this.logger = factory.getLogger();
		
		List<File> selectedFiles = getChildrenFiles(factory.getSelectionDirectory());
		this.progressBar = factory.getProgressBar(selectedFiles.size());
		
		
		logger.write("Running FileType Tool");
		String fileCommandVersion = findFileCommandVersion();
		List<Map<String,String>> outputData = new ArrayList<Map<String,String>>();
		processSelection(factory.getSelection(), "/", outputData);
		writeOutReport(outputData, fileCommandVersion);
	}

	private void processSelection(Iterator<ERAObject> objects, String path, List<Map<String,String>> outputData){
		
		while(objects.hasNext()){
			ERAObject object = objects.next();
			if(object.isFile()){
				try{
					String filePath = ((ERAStringMetadata) object.getMetadataField(ERAMetadataField.FILE_PATH)).getValue();
					Map<String, String> resultData = new HashMap<String,String>();
					int exitValue = runFileCommand(object, filePath, resultData );
					if (exitValue == 0) {
						outputData.add(resultData);
					}
				} catch(Exception ex){
					logger.write(ex.getLocalizedMessage());
				} finally{
					progressBar.performStep();
				}
			}
			
			if(object.isFolder()){
				try {
					String newPath = String.format("%s/%s", path, object.fileName());
					processSelection(object.listFiles(), newPath, outputData);
				} catch (ERAException e) {
					logger.write(e.getMessage());
				}
			}
		}
	}

	private String findFileCommandVersion() {
		String result = "Unknown";
		try {
			ProcessBuilder builder = new ProcessBuilder(Arrays.asList("file", "-v"));			
			Process proc = builder.start();
			proc.waitFor();
						
			if (proc.exitValue() == 0) {
				result = new BufferedReader(new InputStreamReader(proc.getInputStream()))
			  					.lines()
			  					.findFirst()
			  					.orElse("Unknown");
			}
			else {
				String error = new BufferedReader(new InputStreamReader(proc.getErrorStream()))
  								.lines()
  								.collect(Collectors.joining(" "));
				logger.write("Error when finding file command version, details: " + error);
			}			
		}catch(Exception e){
			logger.write(e.getMessage());
		}
		return result;
	}
	
	private int runFileCommand(ERAObject object, String path, Map<String, String> resultData){
		
		int returnCode = -1;
		File pre = factory.getSelectionDirectory();
		File source = new File(String.format("%s/%s/%s", pre.getAbsolutePath() , path, object.fileName()));
		logger.write(String.format("Processing file: %s", object.fileName()));
		
		try {
			// Run the file command with options -b and -L.
			// -b option keeps the output brief
			// And -L dereferences any symlinks.
			//
			ProcessBuilder builder = new ProcessBuilder(
					Arrays.asList("file", "-b", "-L", source.getAbsolutePath()));			
			Process proc = builder.start();
			proc.waitFor();
						
			if (proc.exitValue() == 0) {
				String result = new BufferedReader(new InputStreamReader(proc.getInputStream()))
			  			.lines()
			  			.collect(Collectors.joining("\n"));
				
				resultData.put(TOOL_TIMESTAMP,  LocalDateTime.now(Clock.systemUTC()).toString());
				resultData.put(OBJECT_FILENAME, object.fileName());
				resultData.put(FILE_FORMAT, result);
				returnCode = 0;
			}
			else {
				String error = new BufferedReader(new InputStreamReader(proc.getErrorStream()))
  								.lines()
  								.collect(Collectors.joining("\n"));
				logger.write("Error running file command for file" + object.fileName() +
							  " Details: " + error);
			}
			//logger.write("Exit code: " + proc.exitValue() + " Output: " + result);			
		}catch(Exception e){
			logger.write(e.getMessage());
		}
		//logger.write("---------------------------------------");
		return returnCode;
	}

	private void writeOutReport(List<Map<String,String>> outputData, String fileCommandVersion) {
		
		String fileCommandVersionJson = AGENT_VERSION_JSON_PREFIX + "\"" +
										fileCommandVersion + "\"";
		
		String jsonOutput = outputData.stream()
									   .map(v -> makeFileOutputIntoJson(v, fileCommandVersionJson))
									   .collect(Collectors.joining(","));
		jsonOutput = "[" + jsonOutput + "]";
		
		String fileName = "FileIdentificationReport_" + LocalDateTime.now(Clock.systemUTC()).toString() +".json";
		//Path path = Paths.get(factory.getSelectionDirectory().getAbsolutePath(), fileName);
		try {
			//ERAObject newFile = factory.newFile(factory.getSelectionDirectory().toString(), fileName);	
			ERAObject newFile = factory.newFile("/fileidoutput", fileName);	
			ERASelectMetadata mimeType =((ERASelectMetadata) newFile.getMetadataField(ERAMetadataField.MIME_TYPE));
			mimeType.set("text/plain");
			newFile.updateMetadata(mimeType);
			newFile.write(new ByteArrayInputStream(jsonOutput.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			logger.write(String.format("File %s could not be created in %s", factory.getSelectionDirectory().toString(),
					fileName));
		}
	}
	
	private String makeFileOutputIntoJson(Map<String, String> fileResult, String fileCommandVersion) {
		String jsonString = fileResult.entrySet()
				   				.stream()
				   				.map(e -> "\"" + e.getKey() + "\":\"" + e.getValue() + "\"")
				   				.collect(Collectors.joining(",\n"));
		StringJoiner joiner = new StringJoiner(",\n", "\n{\n", "\n}\n");
		joiner.add(jsonString);
		joiner.add(AGENT_NAME_JSON);
		joiner.add(fileCommandVersion);
		joiner.add(CONFIDENCE_JSON);	
		return joiner.toString();
	}
	
	private List<File> getChildrenFiles(File file){		
		List<File> children = new ArrayList<>();

		if(file.isDirectory() && file.exists()){
			for(File child: file.listFiles()){
				children.addAll(getChildrenFiles(child));				
			}
		}

		if(file.isFile() && file.exists()){
			children.add(file);
		}

		return children;

	}


	
}


