/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.chemistry.opencmis.tck.tests.crud;

import static org.apache.chemistry.opencmis.tck.CmisTestResultStatus.FAILURE;
import static org.apache.chemistry.opencmis.tck.CmisTestResultStatus.SKIPPED;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.chemistry.opencmis.client.api.ChangeEvent;
import org.apache.chemistry.opencmis.client.api.ChangeEvents;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.commons.enums.CapabilityChanges;
import org.apache.chemistry.opencmis.commons.enums.ChangeType;
import org.apache.chemistry.opencmis.tck.impl.AbstractSessionTest;

public class GetContentChangesTest extends AbstractSessionTest {

    @Override
    public void init(Map<String, String> parameters) {
        super.init(parameters);
        setName("Get Content Changes Test");
        setDescription("Creates/Updates/Renames/Moves/Deletes a document and a folder to track the Changelog.");
    }

    @Override
    public void run(Session session) {
    	if(session.getRepositoryInfo().getCapabilities().getChangesCapability() == CapabilityChanges.NONE){
    		addResult(createResult(SKIPPED, "Repository does not provide getChangeLog support. Test skipped!"));
    		return;
    	}
    	
    	runPlausibilityTest(session);

    	// create/update/delete a test folder
    	runCUDFolderTest(session);
    	// create/update/delete a test file
    	runCUDFileTest(session);
    	// move test file
    	runMoveFileTest(session);
    	// move test folder
    	runMoveFolderTest(session);
    }
    
    private void runPlausibilityTest(Session session) {
    	String changeLogToken = session.getRepositoryInfo().getLatestChangeLogToken();
    	ChangeEvents changes = session.getContentChanges(changeLogToken, true, 1000);
    	if(changeLogToken.equals(changes.getLatestChangeLogToken()) && changes.getTotalNumItems() > 0) {
    		addResult(createResult(FAILURE, "ChangeLog Token hasn't changed, but events are returned."));
    		return;
    	}
    	int maxresults = 10;
    	// Check the whole history
    	changes = session.getContentChanges(null, true, maxresults);
    	if(changes.getLatestChangeLogToken() == null) {
    		addResult(createResult(FAILURE, "Latest ChangeLog Token hasn't returned, but it should be."));
    		return;
    	}
    	if(changes.getChangeEvents().size() < changes.getTotalNumItems() && !changes.getHasMoreItems()) {
    		addResult(createResult(FAILURE, "The Changes Object seems to be inconsistent, there are less events in the result then on the server side, but the Change Object returnes no on HasMoreItems."));
    		return;
    	}
    	if(changes.getChangeEvents().size() < changes.getTotalNumItems() && changes.getChangeEvents().size() < maxresults) {
    		addResult(createResult(FAILURE, "The client requested "+maxresults+" events, but only recieves "+changes.getChangeEvents().size()+ " of "+changes.getTotalNumItems()+" possible events."));
    		return;
    	}
    	for (ChangeEvent event: changes.getChangeEvents()) {
    		if(event.getChangeType() == ChangeType.UPDATED && event.getProperties().size()==0){
    			addResult(createResult(FAILURE, "The client requested to get changed properties, but didn't recieved them."));
    			return;
    		}
		}
    	addResult(createInfoResult("Succeded simple plausibity check."));
    }

    
    private void runCUDFolderTest(Session session) {
    	CapabilityChanges capChanges = session.getRepositoryInfo().getCapabilities().getChangesCapability();
    	boolean isPropertyChangesSupported = false;
    	if(capChanges == CapabilityChanges.ALL || capChanges == CapabilityChanges.PROPERTIES)
    		isPropertyChangesSupported = true;
    	String changetoken = session.getRepositoryInfo().getLatestChangeLogToken();
    	Folder folder = createTestFolder(session);
    	try{
    		ChangeEvents changes = session.getContentChanges(changetoken, isPropertyChangesSupported, 100);
    		if(changetoken.equals(changes.getLatestChangeLogToken())) {
    			addResult(createResult(FAILURE, "There should be a create event for the new folder and a new changelog token."));
    			return;
    		}
    		if(changes.getTotalNumItems() == 0 || changes.getChangeEvents().size() == 0) {
    			addResult(createResult(FAILURE, "There should be a create event for the new folder, but there isn't."));
    			return;
    		}
    		boolean createEventFound = false;
    		for(ChangeEvent change : changes.getChangeEvents()) {
    			if(change.getChangeType() == ChangeType.CREATED && change.getObjectId().equals(folder.getId())){
    				createEventFound = true;
    				break;
    			}
    		}
    		if(!createEventFound) {
    			addResult(createResult(FAILURE, "No event found for the created folder."));
    			return;
    		}
    		changetoken = changes.getLatestChangeLogToken();
    		String oldfolderid = folder.getId();
    		String newname = folder.getName() + "_new";
    		Map<String, Object> properties = new HashMap<String, Object>();
    		properties.put("cmis:name", newname);
    		folder.updateProperties(properties);
    		changes = session.getContentChanges(changetoken, isPropertyChangesSupported, 100);
    		boolean updateEventFound = false;
    		boolean deleteEventFound = false;
    		createEventFound = false;
    		for(ChangeEvent change : changes.getChangeEvents()) {
    			if(change.getChangeType()== ChangeType.UPDATED && change.getObjectId().equals(folder.getId())){
    				if(isPropertyChangesSupported) {
    					if(change.getProperties().containsKey("cmis:name")){
    						List<?> list = change.getProperties().get("cmis:name");
    						if(list.contains(newname)) {
    	    					updateEventFound = true;
    	    					break;
    						}
    					}
    				}else {
    					if(change.getProperties() != null && change.getProperties().size()>0)
    						addResult(createInfoResult("Properties won't requested, but there are some."));
	    				updateEventFound = true;
	    				break;
    				}
    			}else if(change.getChangeType() == ChangeType.CREATED && change.getObjectId().equals(folder.getId())) {
    				createEventFound = true;
    			}else if(change.getChangeType() == ChangeType.DELETED && change.getObjectId().equals(oldfolderid)) {
    				deleteEventFound = true;
    			}
    		}
    		if(!updateEventFound && !(createEventFound && deleteEventFound)) {
    			addResult(createResult(FAILURE, "There should be an update event for the folder, but there isn't."));
    			return;
    		}
    		changetoken = changes.getLatestChangeLogToken();
    	}finally {
    		deleteTestFolder();
    	}
    	String folderId = folder.getId();
    	ChangeEvents changes = session.getContentChanges(changetoken, isPropertyChangesSupported, 100);
    	boolean deleteEventFound = false;
    	for(ChangeEvent change : changes.getChangeEvents()) {
    		if(change.getChangeType()==ChangeType.DELETED && change.getObjectId().equals(folderId)) {
    			deleteEventFound = true;
    			break;
    		}
    	}
    	if(!deleteEventFound) {
    		addResult(createResult(FAILURE, "There should be a delete event for the folder, but there isn't."));
    		return;
    	}
    }
    
    private void runCUDFileTest(Session session) {
    	Folder testfolder = createTestFolder(session);
    	CapabilityChanges capChanges = session.getRepositoryInfo().getCapabilities().getChangesCapability();
    	boolean isPropertyChangesSupported = false;
    	if(capChanges == CapabilityChanges.ALL || capChanges == CapabilityChanges.PROPERTIES)
    		isPropertyChangesSupported = true;
    	String changetoken = session.getRepositoryInfo().getLatestChangeLogToken();
    	Document doc = createDocument(session, testfolder, "update.txt", "Hello changing World!");
    	try{
    		ChangeEvents changes = session.getContentChanges(changetoken, isPropertyChangesSupported, 100);
    		if(changetoken.equals(changes.getLatestChangeLogToken())) {
    			addResult(createResult(FAILURE, "There should be a create event for the new file and a new changelog token."));
    			return;
    		}
    		if(changes.getTotalNumItems() == 0 || changes.getChangeEvents().size() == 0) {
    			addResult(createResult(FAILURE, "There should be a create event for the new file, but there isn't."));
    			return;
    		}
    		boolean createEventFound = false;
    		for(ChangeEvent change : changes.getChangeEvents()) {
    			if(change.getChangeType() == ChangeType.CREATED && change.getObjectId().equals(doc.getId())){
    				createEventFound = true;
    				break;
    			}
    		}
    		if(!createEventFound) {
    			addResult(createResult(FAILURE, "No event found for the created file."));
    			return;
    		}
    		changetoken = changes.getLatestChangeLogToken();
    		String olddocid = doc.getId();
    		String newname = doc.getName() + "_new";
    		Map<String, Object> properties = new HashMap<String, Object>();
    		properties.put("cmis:name", newname);
    		doc.updateProperties(properties);
    		changes = session.getContentChanges(changetoken, isPropertyChangesSupported, 100);
    		boolean updateEventFound = false;
    		boolean deleteEventFound = false;
    		createEventFound = false;
    		for(ChangeEvent change : changes.getChangeEvents()) {
    			if(change.getChangeType() == ChangeType.UPDATED && change.getObjectId().equals(doc.getId())){
    				if(isPropertyChangesSupported) {
    					if(change.getProperties().containsKey("cmis:name")){
    						List<?> list = change.getProperties().get("cmis:name");
    						if(list.contains(newname)) {
    	    					updateEventFound = true;
    	    					break;
    						}
    					}
    				}else {
    					if(change.getProperties() != null && change.getProperties().size()>0)
    						addResult(createInfoResult("Properties won't requested, but there are some."));
	    				updateEventFound = true;
	    				break;
    				}
    			}else if(change.getChangeType() == ChangeType.CREATED && change.getObjectId().equals(doc.getId())) {
    				createEventFound = true;
    			}else if(change.getChangeType() == ChangeType.DELETED && change.getObjectId().equals(olddocid)) {
    				deleteEventFound = true;
    			}
    		}
    		if(!updateEventFound && !(createEventFound && deleteEventFound)) {
    			addResult(createResult(FAILURE, "There should be an update event for the file, but there isn't."));
    			return;
    		}
    		changetoken = changes.getLatestChangeLogToken();
        	String docId = doc.getId();
        	doc.deleteAllVersions();
        	changes = session.getContentChanges(changetoken, isPropertyChangesSupported, 100);
        	deleteEventFound = false;
        	for(ChangeEvent change : changes.getChangeEvents()) {
        		if(change.getChangeType()==ChangeType.DELETED && change.getObjectId().equals(docId)) {
        			deleteEventFound = true;
        			break;
        		}
        	}
        	if(!deleteEventFound) {
        		addResult(createResult(FAILURE, "There should be a delete event for the file, but there isn't."));
        		return;
        	}
    	}finally {
    		deleteTestFolder();
    	}
    }
    
    private void runMoveFolderTest(Session session) {
    	
    }
    
    private void runMoveFileTest(Session session) {
    	
    }
}
