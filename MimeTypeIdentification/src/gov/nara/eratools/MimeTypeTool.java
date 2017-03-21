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

public class MimeTypeTool implements ERATool {
	private ERAProgress progressBar;
	private ERALogger logger;
	private ERAFactory factory;
	
	
	
	@Override
	public void execute(ERAFactory factory) {
		this.factory = factory;
		this.logger = factory.getLogger();
		
		List<File> selectedFiles = getChildrenFiles(factory.getSelectionDirectory());
		this.progressBar = factory.getProgressBar(selectedFiles.size());
		
		
		logger.write("Running MimeType Tool");
		
		
		processSelection(factory.getSelection(), "/");
		
	}

	private void processSelection(Iterator<ERAObject> objects, String path){
		
		while(objects.hasNext()){
			ERAObject object = objects.next();
			if(object.isFile()){
				try{
					String filePath = ((ERAStringMetadata) object.getMetadataField(ERAMetadataField.FILE_PATH)).getValue();
					runFileCommand(object, filePath );
				} catch(Exception ex){
					logger.write(ex.getLocalizedMessage());
				} finally{
					progressBar.performStep();
				}
			}
			
			if(object.isFolder()){
				try {
					String newPath = String.format("%s/%s", path, object.fileName());
					processSelection(object.listFiles(), newPath);
				} catch (ERAException e) {
					logger.write(e.getMessage());
				}
			}
		}
	}


	
	private int runFileCommand(ERAObject object, String path){
		
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
					Arrays.asList("file", "-b", "-L", "--mime-type", source.getAbsolutePath()));			
			Process proc = builder.start();
			proc.waitFor();
						
			if (proc.exitValue() == 0) {
				String result = new BufferedReader(new InputStreamReader(proc.getInputStream()))
			  			.lines()
			  			.collect(Collectors.joining("\n"));
				ERASelectMetadata mimeType =((ERASelectMetadata) object.getMetadataField(ERAMetadataField.MIME_TYPE));
				mimeType.set(result);
				object.updateMetadata(mimeType);		
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


